package me.filippov.gradle.jvm.wrapper

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

val isWindows = System.getProperty("os.name").lowercase(Locale.ENGLISH).startsWith("windows")
val isMac = System.getProperty("os.name").lowercase(Locale.ENGLISH).startsWith("mac")
val isLinux = System.getProperty("os.name").lowercase(Locale.ENGLISH).startsWith("linux")

val wrapperScriptFileName = when {
    isWindows -> "gradlew.bat"
    isLinux -> "gradlew"
    isMac -> "gradlew"
    else -> error("Unknown OS")
}

data class TaskResult(val exitCode: Int, val stdout: String, val stderr: String)

private fun gradlewProcessBuilder(projectRoot: File, task: String): ProcessBuilder {
    val workingDirectory = File(System.getProperty("user.dir"))
    return ProcessBuilder(
            projectRoot.resolve(wrapperScriptFileName).absolutePath, "--include-build",
            workingDirectory.absolutePath, "-Pkotlin.compiler.execution.strategy=in-process", "--no-daemon", ":$task")
        .directory(projectRoot)
}

// destroyForcibly alone kills only the direct child (cmd.exe/sh); a surviving java
// grandchild would keep @TempDir files locked and the pipes open.
fun killTree(process: Process) {
    process.toHandle().descendants().forEach { it.destroyForcibly() }
    process.destroyForcibly()
}

fun runProcess(builder: ProcessBuilder, timeoutMinutes: Long = 10): TaskResult {
    val process = builder.start()
    // Read both streams concurrently: a sequential read deadlocks when the child
    // fills the other pipe's buffer, and the timeout below is never reached.
    val stdout = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
    val stderr = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
    if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
        killTree(process)
        val out = runCatching { stdout.get(10, TimeUnit.SECONDS) }.getOrDefault("<unavailable>")
        val err = runCatching { stderr.get(10, TimeUnit.SECONDS) }.getOrDefault("<unavailable>")
        error("Process timed out after $timeoutMinutes minutes\nSTDOUT:\n$out\nSTDERR:\n$err")
    }
    // A grandchild that inherited the pipes could keep them open past the child's
    // exit; never wait on the streams forever.
    return TaskResult(process.exitValue(),
        stdout.get(1, TimeUnit.MINUTES), stderr.get(1, TimeUnit.MINUTES))
}

fun gradlew(projectRoot: File, task: String): TaskResult =
    runProcess(gradlewProcessBuilder(projectRoot, task))

val gitBash = File("""C:\Program Files\Git\bin\bash.exe""")

// Runs the unix wrapper script on Windows under Git Bash (the msys environment).
fun gradlewInGitBash(projectRoot: File, task: String): TaskResult {
    val workingDirectory = File(System.getProperty("user.dir"))
    return runProcess(ProcessBuilder(
            gitBash.absolutePath, "./gradlew", "--include-build",
            workingDirectory.absolutePath, "-Pkotlin.compiler.execution.strategy=in-process", "--no-daemon", ":$task")
        .directory(projectRoot))
}

fun gradlewParallel(projectRoot: File, task: String, count: Int): List<TaskResult> {
    val processes = List(count) { gradlewProcessBuilder(projectRoot, task).start() }
    try {
        val outputs = processes.map { process ->
            CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() } to
                    CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
        }
        return processes.mapIndexed { i, process ->
            if (!process.waitFor(10, TimeUnit.MINUTES)) error("Process timeout error")
            TaskResult(process.exitValue(),
                outputs[i].first.get(1, TimeUnit.MINUTES), outputs[i].second.get(1, TimeUnit.MINUTES))
        }
    } finally {
        // A timeout of process 0 must not leak the still-running siblings.
        processes.forEach { if (it.isAlive) killTree(it) }
    }
}

fun withBuildScript(projectRoot: File, withContent: () -> String) {
    projectRoot.resolve("build.gradle.kts").writeText(withContent().trimIndent())
}

fun prepareWrapperWithArguments(projectRoot: File, vararg arguments: String): String =
    GradleRunner.create()
        .withProjectDir(projectRoot)
        .withArguments(*arguments)
        .withPluginClasspath()
        .build()
        .output

fun prepareWrapperExpectingFailure(projectRoot: File): String =
    runGradleExpectingFailure(projectRoot, ":wrapper")

fun runGradleExpectingFailure(projectRoot: File, vararg arguments: String): String =
    GradleRunner.create()
        .withProjectDir(projectRoot)
        .withArguments(*arguments)
        .withPluginClasspath()
        .buildAndFail()
        .output

fun prepareWrapper(projectRoot: File) {
    val wrapperResult = GradleRunner.create()
        .withProjectDir(projectRoot)
        .withArguments(":wrapper")
        .withPluginClasspath().build()
    wrapperResult.task(":wrapper")?.outcome.shouldBe(TaskOutcome.SUCCESS,
        "Wrapper generation failed:\n${wrapperResult.output}")
}

fun <T> T.shouldBe(expectedValue: T, message: String? = null) {
    assertEquals(expectedValue, this, message)
}

fun String.shouldContain(expectedValue: String, message: String? = null) {
    assert(this.contains(expectedValue)){  message ?: "String '$this' not contains '$expectedValue'" }
}

fun String.shouldNotContain(expectedValue: String, message: String? = null) {
    assert(!this.contains(expectedValue)){  message ?: "String '$this' contains '$expectedValue'" }
}

fun String.shouldBeEmpty(message: String? = null) {
    assert(this.isEmpty()) { message ?: "String '$this' is not empty" }
}

fun Boolean.shouldBeTrue(message: String? = null) {
    assert(this) { message ?: "Value should be true" }
}
