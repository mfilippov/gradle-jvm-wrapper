package me.filippov.gradle.jvm.wrapper

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PluginTest {
    @Test
    fun smoke(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("folder with space").toFile()
        projectRoot.mkdirs()
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
        val resultWhenJavaNotExists = gradlew(projectRoot, "hello")

        resultWhenJavaNotExists.stdout.shouldContain("Hello world!", "Invalid output: \n" + resultWhenJavaNotExists.stdout)
        resultWhenJavaNotExists.stderr.shouldBeEmpty("Invalid output: \n" + resultWhenJavaNotExists.stderr)
        resultWhenJavaNotExists.exitCode.shouldBe(0)

        projectRoot.resolve("build").resolve("gradle-jvm").exists().shouldBeTrue()

        val resultWhenJavaExists = gradlew(projectRoot, "hello")

        resultWhenJavaExists.stdout.shouldContain("Hello world!", "Invalid output: \n" + resultWhenJavaNotExists.stdout)
        resultWhenJavaExists.stderr.shouldBeEmpty("Invalid output: \n" + resultWhenJavaNotExists.stderr)
        resultWhenJavaExists.exitCode.shouldBe(0)

        withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }

            jvmWrapper {
                linuxJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-linux-x64.tar.gz"
                macJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-macosx-x64.tar.gz"
                windowsJvmUrl ="https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-windows-x86-jdk.zip"
            }
            
            tasks.register("newHello") {
                doLast {
                    println("Hello new world!")
                }
            }
        """}
        prepareWrapper(projectRoot)

        val resultAfterJavaUpdate = gradlew(projectRoot, "newHello")
        resultAfterJavaUpdate.stdout.shouldContain("Hello new world!", "Invalid output: \n" + resultWhenJavaNotExists.stdout)
        resultAfterJavaUpdate.stderr.shouldBeEmpty("Invalid output: \n" + resultWhenJavaNotExists.stderr)
        resultAfterJavaUpdate.exitCode.shouldBe(0)

        val jdkDirs = projectRoot.resolve("build").resolve("gradle-jvm").list()!!
        jdkDirs.size.shouldBe(2)
        repeat((0..30).count()) {
            if (!projectRoot.exists()) {
                return@repeat
            }
            Thread.sleep(1000)
            projectRoot.deleteRecursively()
        }
    }
}