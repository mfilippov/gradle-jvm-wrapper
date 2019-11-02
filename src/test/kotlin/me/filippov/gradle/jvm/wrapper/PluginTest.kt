package me.filippov.gradle.jvm.wrapper

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginTest {
    @Rule @JvmField val testProjectDir = TemporaryFolder()

    @Test
    fun smoke() {
        val buildFile = testProjectDir.newFile("build.gradle.kts")
        buildFile.writeText("""
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
        """.trimIndent())

        val wrapperResult = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(":wrapper")
                .withPluginClasspath()
                .build()
        wrapperResult.task(":wrapper")?.outcome.shouldBe(TaskOutcome.SUCCESS)

        val processBuilder = ProcessBuilder(testProjectDir.root.resolve(wrapperScriptFileName).absolutePath, ":hello")
        val process = processBuilder.start()
        process.waitFor()
        testProjectDir.root.resolve("build").resolve("gradle-jvm").exists().shouldBeTrue()
    }
}