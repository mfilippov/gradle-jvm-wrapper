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

fun gradlew(projectRoot: File, task: String): TaskResult {
    val process = gradlewProcessBuilder(projectRoot, task).start()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    if (!process.waitFor(5, TimeUnit.MINUTES)) error("Process timeout error")
    return TaskResult(process.exitValue(), stdout, stderr)
}

fun gradlewParallel(projectRoot: File, task: String, count: Int): List<TaskResult> {
    val processes = List(count) { gradlewProcessBuilder(projectRoot, task).start() }
    val outputs = processes.map { process ->
        CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() } to
                CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
    }
    return processes.mapIndexed { i, process ->
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("Process timeout error")
        }
        TaskResult(process.exitValue(), outputs[i].first.get(), outputs[i].second.get())
    }
}

fun withBuildScript(projectRoot: File, withContent: () -> String) {
    projectRoot.resolve("build.gradle.kts").writeText(withContent().trimIndent())
}

fun prepareWrapperExpectingFailure(projectRoot: File): String =
    GradleRunner.create()
        .withProjectDir(projectRoot)
        .withArguments(":wrapper")
        .withPluginClasspath()
        .buildAndFail()
        .output

fun prepareWrapper(projectRoot: File) {
    val wrapperResult = GradleRunner.create()
        .withProjectDir(projectRoot)
        .withArguments(":wrapper")
        .withPluginClasspath().build()
    val result = wrapperResult.task(":wrapper")?.outcome
    if (result != TaskOutcome.SUCCESS) {
        println("test")
    }
}

fun <T> T.shouldBe(expectedValue: T, message: String? = null) {
    assertEquals(expectedValue, this, message)
}

fun String.shouldContain(expectedValue: String, message: String? = null) {
    assert(this.contains(expectedValue)){  message ?: "String '$this' not contains '$expectedValue'" }
}

fun String.shouldNotContain(expectedValue: String, message: String? = null) {
    assert(!this.contains(expectedValue)){  message ?: "String '$this' not contains '$expectedValue'" }
}

fun String.shouldBeEmpty(message: String? = null) {
    assert(this.isEmpty()) { message ?: "String '$this' is not empty" }
}

fun Boolean.shouldBeTrue(message: String? = null) {
    assert(this) { message ?: "Value should be true" }
}
