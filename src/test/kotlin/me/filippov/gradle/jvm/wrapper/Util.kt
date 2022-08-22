package me.filippov.gradle.jvm.wrapper

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

val isWindows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows")
val isMac = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("mac")
val isLinux = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("linux")

val wrapperScriptFileName = when {
    isWindows -> "gradlew.bat"
    isLinux -> "gradlew"
    isMac -> "gradlew"
    else -> error("Unknown OS")
}

data class TaskResult(val exitCode: Int, val stdout: String, val stderr: String)

fun gradlew(projectRoot: File, task: String): TaskResult {
    val workingDirectory = File(System.getProperty("user.dir"))
    val processBuilder = ProcessBuilder(
            projectRoot.resolve(wrapperScriptFileName).absolutePath, "--include-build",
            workingDirectory.absolutePath, "-Dkotlin.compiler.execution.strategy=in-process", "--no-daemon", ":$task")
        .directory(projectRoot)
    val process = processBuilder.start()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    if (!process.waitFor(5, TimeUnit.MINUTES)) error("Process timeout error")
    return TaskResult(process.exitValue(), stdout, stderr)
}

fun withBuildScript(projectRoot: File, withContent: () -> String) {
    projectRoot.resolve("build.gradle.kts").writeText(withContent().trimIndent())
}

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
