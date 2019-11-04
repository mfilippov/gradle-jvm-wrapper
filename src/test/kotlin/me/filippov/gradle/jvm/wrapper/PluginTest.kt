package me.filippov.gradle.jvm.wrapper

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()


    @Test
    fun smoke() {
        val projectRoot = testProjectDir.root
        withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            tasks.register("hello") {
                doLast {
                    println("Hello world!")
                }
            }
        """}

        prepareWrapper(projectRoot)
        val result = gradlew(projectRoot, "hello")

        result.stdout.shouldContain("Hello world!")
        result.stderr.shouldBeEmpty()
        result.exitCode.shouldBe(0)
        projectRoot.resolve("build").resolve("gradle-jvm").exists().shouldBeTrue()
    }
}