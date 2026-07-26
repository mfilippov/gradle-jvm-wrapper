package me.filippov.gradle.jvm.wrapper

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PluginTest {
    @Test
    fun smoke(@TempDir tempDir: Path) {
        doSmoke(tempDir, "https://download.oracle.com/java/18/archive/jdk-18.0.1.1_windows-x64_bin.zip")
    }

    @Test
    fun smokeWindowsTarGz(@TempDir tempDir: Path) {
        doSmoke(tempDir, "https://cache-redirector.jetbrains.com/intellij-jbr/jbr-17.0.3-windows-x64-b469.37.tar.gz")
    }

    @Test
    fun smokeParallelRun(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("folder with space").toFile()
        projectRoot.mkdirs()
        val jvmInstallDir = projectRoot.resolve("build").resolve("test-temp-dir").resolve("gradle-jvm")
        val absJvmDir = jvmInstallDir.absolutePath.replace("\\", "\\\\")

        withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                winJvmInstallDir = "$absJvmDir"
                unixJvmInstallDir = "$absJvmDir"
            }
            tasks.register("hello") {
                doLast {
                    println("Hello world!")
                }
            }
        """}

        prepareWrapper(projectRoot)

        // Warm up Gradle caches, then drop the downloaded JVM so both parallel
        // runs start with an empty install dir and race for the download.
        val warmUp = gradlew(projectRoot, "hello")
        warmUp.exitCode.shouldBe(0, "Warm-up run failed:\nSTDOUT:\n${warmUp.stdout}\nSTDERR:\n${warmUp.stderr}\n")
        jvmInstallDir.deleteRecursively()

        val results = gradlewParallel(projectRoot, "hello", 2)
        results.forEach { result ->
            result.stdout.shouldContain("Hello world!",
                "'Hello world!' not found in output:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")
            result.exitCode.shouldBe(0, "Non zero exit code:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")
        }
        results.count { it.stdout.contains("Down") }.shouldBe(1,
            "Expected exactly one process to download the JVM:\n" +
                    results.joinToString("\n") { "STDOUT:\n${it.stdout}\nSTDERR:\n${it.stderr}\n" })
        jvmInstallDir.list()!!.size.shouldBe(1)

        repeat((0..30).count()) {
            if (!projectRoot.exists()) {
                return@repeat
            }
            Thread.sleep(1000)
            projectRoot.deleteRecursively()
        }
    }

    private fun doSmoke(tempDir: Path, windowsX64Url: String) {
        val projectRoot = tempDir.resolve("folder with space").toFile()
        projectRoot.mkdirs()
        val absJvmDir = projectRoot.resolve("build").resolve("test-temp-dir").resolve("gradle-jvm").absolutePath.replace("\\", "\\\\")

        withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                winJvmInstallDir = "$absJvmDir"
                unixJvmInstallDir = "$absJvmDir"
            }
            tasks.register("hello") {
                doLast {
                    println("Hello world!")
                }
            }
        """}

        prepareWrapper(projectRoot)
        val resultWhenJavaNotExists = gradlew(projectRoot, "hello")

        resultWhenJavaNotExists.stdout.shouldContain("Down",
            "'Down' not found in output:\nSTDOUT:\n${resultWhenJavaNotExists.stdout}\nSTDERR:\n${resultWhenJavaNotExists.stderr}\n"
        )

        resultWhenJavaNotExists.stdout.shouldContain("Hello world!",
            "'Hello world!' not found in output:\nSTDOUT:\n${resultWhenJavaNotExists.stdout}\nSTDERR:\n${resultWhenJavaNotExists.stderr}\n"
        )
        resultWhenJavaNotExists.stderr.shouldBeEmpty("Non empty stderr:\n" + resultWhenJavaNotExists.stderr)
        resultWhenJavaNotExists.exitCode.shouldBe(0)

        projectRoot.resolve("build").resolve("test-temp-dir").resolve("gradle-jvm").exists().shouldBeTrue()

        val resultWhenJavaExists = gradlew(projectRoot, "hello")

        resultWhenJavaExists.stdout.shouldContain("Hello world!",
            "'Hello world!' not found in output:\nSTDOUT:\n${resultWhenJavaExists.stdout}\nSTDERR:\n${resultWhenJavaExists.stderr}\n")
        resultWhenJavaExists.stdout.shouldNotContain("Down",
            "'Down' not found in output:\nSTDOUT:\n${resultWhenJavaExists.stdout}\nSTDERR:\n${resultWhenJavaExists.stderr}\n")
        resultWhenJavaExists.stderr.shouldBeEmpty("Non empty stderr:\n" + resultWhenJavaExists.stderr)
        resultWhenJavaExists.exitCode.shouldBe(0)

        withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }

            jvmWrapper {
                winJvmInstallDir = "$absJvmDir"
                unixJvmInstallDir = "$absJvmDir"
                linuxAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-aarch64.tar.gz"
                linuxX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-x64.tar.gz"
                macAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-aarch64.tar.gz"
                macX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz"
                windowsAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.4-windows-aarch64.zip"
                windowsX64JvmUrl ="$windowsX64Url"
            }
            
            tasks.register("newHello") {
                doLast {
                    println("Hello new world!")
                }
            }
        """}
        prepareWrapper(projectRoot)

        val resultAfterJavaUpdate = gradlew(projectRoot, "newHello")
        resultAfterJavaUpdate.stdout.shouldContain("Hello new world!",
            "'Hello new world!' not found in output:\nSTDOUT:\n${resultAfterJavaUpdate.stdout}\nSTDERR:\n${resultAfterJavaUpdate.stderr}\n")
        resultAfterJavaUpdate.stdout.shouldContain("Down",
            "'Down' not found in output:\nSTDOUT:\n${resultAfterJavaUpdate.stdout}\nSTDERR:\n${resultAfterJavaUpdate.stderr}\n")
        resultAfterJavaUpdate.stderr.shouldBeEmpty("Non empty stderr:\n" + resultAfterJavaUpdate.stderr)
        resultAfterJavaUpdate.exitCode.shouldBe(0)

        val jdkDirs = projectRoot.resolve("build").resolve("test-temp-dir").resolve("gradle-jvm").list()!!
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
