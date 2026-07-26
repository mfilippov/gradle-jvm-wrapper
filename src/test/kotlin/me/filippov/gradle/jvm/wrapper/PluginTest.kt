package me.filippov.gradle.jvm.wrapper

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*

class PluginTest {
    // Allows platform-specific CI (e.g. Alpine/musl) to point the tests at a compatible JDK build.
    private fun jvmUrlOverrides(): String {
        val linuxX64Url = System.getenv("TEST_LINUX_X64_JVM_URL") ?: return ""
        return """linuxX64JvmUrl = "$linuxX64Url""""
    }

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
                ${jvmUrlOverrides()}
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

    @Test
    fun smokeSelfHealAfterBrokenInstall(@TempDir tempDir: Path) {
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
                ${jvmUrlOverrides()}
            }
            tasks.register("hello") {
                doLast {
                    println("Hello world!")
                }
            }
        """}

        prepareWrapper(projectRoot)

        val firstRun = gradlew(projectRoot, "hello")
        firstRun.exitCode.shouldBe(0, "First run failed:\nSTDOUT:\n${firstRun.stdout}\nSTDERR:\n${firstRun.stderr}\n")

        // Simulate a broken installation: the JDK content is gone, but the .flag survived.
        // Retry the removal: on Windows the JDK files may be briefly locked after the first run.
        val jvmTargetDir = jvmInstallDir.listFiles()!!.single()
        for (attempt in 0..30) {
            jvmTargetDir.listFiles()!!.filter { it.name != ".flag" }.forEach { it.deleteRecursively() }
            if (jvmTargetDir.listFiles()!!.all { it.name == ".flag" }) break
            Thread.sleep(1000)
        }
        jvmTargetDir.listFiles()!!.all { it.name == ".flag" }
            .shouldBeTrue("Failed to remove the JDK content from $jvmTargetDir")

        val secondRun = gradlew(projectRoot, "hello")
        secondRun.stdout.shouldContain("Down",
            "Expected the JVM to be re-downloaded:\nSTDOUT:\n${secondRun.stdout}\nSTDERR:\n${secondRun.stderr}\n")
        secondRun.stdout.shouldContain("Hello world!",
            "'Hello world!' not found in output:\nSTDOUT:\n${secondRun.stdout}\nSTDERR:\n${secondRun.stderr}\n")
        secondRun.exitCode.shouldBe(0, "Non zero exit code:\nSTDOUT:\n${secondRun.stdout}\nSTDERR:\n${secondRun.stderr}\n")

        repeat((0..30).count()) {
            if (!projectRoot.exists()) {
                return@repeat
            }
            Thread.sleep(1000)
            projectRoot.deleteRecursively()
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun staleLockReportsClearError(@TempDir tempDir: Path) {
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
                ${jvmUrlOverrides()}
            }
            tasks.register("hello") {
                doLast {
                    println("Hello world!")
                }
            }
        """}

        prepareWrapper(projectRoot)

        // A lock file whose owner is long dead: the wrapper must fail with a clear error
        // instead of corrupting the installation or waiting forever.
        jvmInstallDir.mkdirs()
        jvmInstallDir.resolve(".gradle-jvm-lock.pid").writeText("99999999")

        val result = gradlew(projectRoot, "hello")
        (result.exitCode != 0).shouldBeTrue(
            "Expected a failure, got exit code 0:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")
        (result.stdout + result.stderr).shouldContain("The lock file",
            "Expected a stale lock error:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")

        repeat((0..30).count()) {
            if (!projectRoot.exists()) {
                return@repeat
            }
            Thread.sleep(1000)
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun invalidSha256FailsAtConfigurationTime(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.mkdirs()
        withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                linuxX64JvmSha256 = "not-a-checksum"
            }
        """}

        val output = prepareWrapperExpectingFailure(projectRoot)
        output.shouldContain("linuxX64JvmSha256",
            "Expected a configuration error mentioning the property:\n$output")
    }

    @Test
    fun smokeSha256Validation(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("folder with space").toFile()
        projectRoot.mkdirs()
        val jvmInstallDir = projectRoot.resolve("build").resolve("test-temp-dir").resolve("gradle-jvm")
        val absJvmDir = jvmInstallDir.absolutePath.replace("\\", "\\\\")

        // The JVM URL the wrapper will pick on this platform (the plugin defaults) and
        // its published checksum from the vendor's sidecar file.
        val defaults = PluginExtension()
        val arch = System.getProperty("os.arch").lowercase(Locale.ENGLISH)
        val isArm = arch == "aarch64" || arch == "arm64"
        val (platform, jvmUrl) = when {
            isWindows && isArm -> "windowsAarch64" to defaults.windowsAarch64JvmUrl
            isWindows -> "windowsX64" to defaults.windowsX64JvmUrl
            isMac && isArm -> "macAarch64" to defaults.macAarch64JvmUrl
            isMac -> "macX64" to defaults.macX64JvmUrl
            isArm -> "linuxAarch64" to defaults.linuxAarch64JvmUrl
            else -> "linuxX64" to defaults.linuxX64JvmUrl
        }
        // Oracle publishes "<url>.sha256", Microsoft "<url>.sha256sum.txt". A missing
        // aka.ms suffix redirects to a search page, hence the size cutoff; if no sidecar
        // yields a checksum, fall back to hashing the archive itself.
        val sidecarSha256 = sequenceOf("$jvmUrl.sha256", "$jvmUrl.sha256sum.txt")
            .firstNotNullOfOrNull { sidecarUrl ->
                runCatching {
                    URI(sidecarUrl).toURL().readText().takeIf { it.length <= 1024 }
                        ?.trim()?.split(Regex("\\s+"))?.firstOrNull { it.matches(Regex("[0-9a-fA-F]{64}")) }
                }.getOrNull()
            }
        val publishedSha256 = sidecarSha256 ?: run {
            val digest = MessageDigest.getInstance("SHA-256")
            URI(jvmUrl).toURL().openStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun buildScriptWithSha256(sha256: String) = withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                winJvmInstallDir = "$absJvmDir"
                unixJvmInstallDir = "$absJvmDir"
                ${platform}JvmSha256 = "$sha256"
            }
            tasks.register("hello") {
                doLast {
                    println("Hello world!")
                }
            }
        """}

        // A wrong checksum must fail the build with a clear error before extraction.
        buildScriptWithSha256("0".repeat(64))
        prepareWrapper(projectRoot)
        val badRun = gradlew(projectRoot, "hello")
        (badRun.exitCode != 0).shouldBeTrue(
            "Expected a failure, got exit code 0:\nSTDOUT:\n${badRun.stdout}\nSTDERR:\n${badRun.stderr}\n")
        (badRun.stdout + badRun.stderr).shouldContain("SHA-256 mismatch",
            "Expected a checksum error:\nSTDOUT:\n${badRun.stdout}\nSTDERR:\n${badRun.stderr}\n")

        // After the failure nothing must be extracted or marked as installed,
        // and the rejected archive must be removed.
        val leftoverFiles = jvmInstallDir.walkTopDown().filter { it.isFile }.map { it.name }.toList()
        leftoverFiles.none { it == ".flag" }
            .shouldBeTrue("A .flag file survived a failed validation: $leftoverFiles")
        leftoverFiles.none { it.startsWith("gradle-jvm") }
            .shouldBeTrue("The rejected archive was not removed: $leftoverFiles")
        leftoverFiles.none { it.endsWith("java") || it.endsWith("java.exe") }
            .shouldBeTrue("The JDK was extracted despite a failed validation: $leftoverFiles")

        // The correct checksum must pass; uppercase input checks normalization.
        buildScriptWithSha256(publishedSha256.uppercase())
        prepareWrapper(projectRoot)
        val goodRun = gradlew(projectRoot, "hello")
        goodRun.stdout.shouldContain("Hello world!",
            "'Hello world!' not found in output:\nSTDOUT:\n${goodRun.stdout}\nSTDERR:\n${goodRun.stderr}\n")
        goodRun.exitCode.shouldBe(0, "Non zero exit code:\nSTDOUT:\n${goodRun.stdout}\nSTDERR:\n${goodRun.stderr}\n")

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
