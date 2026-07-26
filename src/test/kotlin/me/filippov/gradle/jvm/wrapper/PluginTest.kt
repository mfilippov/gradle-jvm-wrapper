package me.filippov.gradle.jvm.wrapper

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*

class PluginTest {
    private val projectRoots = mutableListOf<File>()

    private fun projectRoot(tempDir: Path, name: String): File =
        tempDir.resolve(name).toFile().also {
            it.mkdirs()
            projectRoots.add(it)
        }

    // Runs even when a test fails: Windows may keep JDK files locked briefly, and a
    // failed @TempDir cleanup would otherwise obscure the real assertion failure.
    @AfterEach
    fun cleanupProjectRoots() {
        projectRoots.forEach { deleteWithRetries(it) }
    }

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
        val projectRoot = projectRoot(tempDir, "folder with space")
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
        // A silently failed single delete would leave the JVM in place and break
        // the exactly-one-download assertion below; Windows may hold locks briefly.
        deleteWithRetries(jvmInstallDir)

        val results = gradlewParallel(projectRoot, "hello", 2)
        results.forEach { result ->
            result.stdout.shouldContain("Hello world!",
                "'Hello world!' not found in output:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")
            result.exitCode.shouldBe(0, "Non zero exit code:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")
        }
        results.count { it.stdout.contains("Downloading ") }.shouldBe(1,
            "Expected exactly one process to download the JVM:\n" +
                    results.joinToString("\n") { "STDOUT:\n${it.stdout}\nSTDERR:\n${it.stderr}\n" })
        jvmInstallDir.list()!!.size.shouldBe(1)

    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun smokeMsys(@TempDir tempDir: Path) {
        // Runs the unix wrapper script under Git Bash: covers the cygwin/msys branch
        // (incl. the aarch64 arch case on ARM runners) and the PowerShell zip
        // fallback, since Git Bash ships no unzip.
        if (System.getenv("CI") != null) {
            // A skip would silently drop the only end-to-end coverage of the msys
            // branch if a runner image ever moves or drops Git for Windows.
            gitBash.exists().shouldBeTrue("Git Bash is required on CI runners: $gitBash")
        } else {
            Assumptions.assumeTrue(gitBash.exists(), "Git Bash is not installed")
        }
        val projectRoot = projectRoot(tempDir, "folder with space's")
        val jvmInstallDir = projectRoot.resolve("build").resolve("test-temp-dir").resolve("gradle-jvm")

        withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                winJvmInstallDir = "${jvmInstallDir.absolutePath.replace("\\", "\\\\")}"
                unixJvmInstallDir = "/${jvmInstallDir.absolutePath[0].lowercaseChar()}${
                    jvmInstallDir.absolutePath.substring(2).replace("\\", "/")}"
            }
            tasks.register("hello") {
                doLast {
                    println("Hello world!")
                }
            }
        """}

        prepareWrapper(projectRoot)

        val result = gradlewInGitBash(projectRoot, "hello")
        result.stdout.shouldContain("Extracting ",
            "Expected the JVM to be downloaded and extracted:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")
        result.stdout.shouldContain("Hello world!",
            "'Hello world!' not found in output:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")
        result.exitCode.shouldBe(0, "Non zero exit code:\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}\n")
        jvmInstallDir.exists().shouldBeTrue("The JVM was not installed into $jvmInstallDir")

    }

    @Test
    fun smokeSelfHealAfterBrokenInstall(@TempDir tempDir: Path) {
        val projectRoot = projectRoot(tempDir, "folder with space")
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
        // A leftover visible directory without bin/java: the install dir is non-empty,
        // but still broken — the wrapper must re-download, not trust the .flag.
        jvmTargetDir.resolve("leftover-dir").mkdirs()

        val secondRun = gradlew(projectRoot, "hello")
        secondRun.stdout.shouldContain("Downloading ",
            "Expected the JVM to be re-downloaded:\nSTDOUT:\n${secondRun.stdout}\nSTDERR:\n${secondRun.stderr}\n")
        secondRun.stdout.shouldContain("Hello world!",
            "'Hello world!' not found in output:\nSTDOUT:\n${secondRun.stdout}\nSTDERR:\n${secondRun.stderr}\n")
        secondRun.exitCode.shouldBe(0, "Non zero exit code:\nSTDOUT:\n${secondRun.stdout}\nSTDERR:\n${secondRun.stderr}\n")

    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun staleLockReportsClearError(@TempDir tempDir: Path) {
        val projectRoot = projectRoot(tempDir, "folder with space")
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

    }

    @Test
    fun outdatedWrapperReportsWarning(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.mkdirs()
        val outdatedMessage = "does not match the generated wrapper scripts"
        fun buildScriptWithUrl(url: String) = withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                linuxX64JvmUrl = "$url"
            }
        """}

        // No wrapper scripts generated yet: nothing to compare, no warning.
        buildScriptWithUrl(PluginExtension.DEFAULT_LINUX_X64_JVM_URL)
        prepareWrapperWithArguments(projectRoot, "help")
            .shouldNotContain(outdatedMessage, "No warning expected before the wrapper is generated")

        prepareWrapper(projectRoot)
        prepareWrapperWithArguments(projectRoot, "help")
            .shouldNotContain(outdatedMessage, "No warning expected for an up-to-date wrapper")

        // The configuration changed: every build warns until the wrapper is regenerated.
        buildScriptWithUrl("https://example.com/custom-jdk-linux-x64.tar.gz")
        prepareWrapperWithArguments(projectRoot, "help")
            .shouldContain(outdatedMessage, "Expected a warning for an outdated wrapper")

        prepareWrapper(projectRoot)
        prepareWrapperWithArguments(projectRoot, "help")
            .shouldNotContain(outdatedMessage, "No warning expected after regeneration")
    }

    @Test
    fun outdatedWrapperFailsInStrictMode(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.mkdirs()
        fun buildScriptWithUrl(url: String) = withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                failOnOutdatedWrapper = true
                linuxX64JvmUrl = "$url"
            }
        """}

        buildScriptWithUrl(PluginExtension.DEFAULT_LINUX_X64_JVM_URL)
        prepareWrapper(projectRoot)

        buildScriptWithUrl("https://example.com/custom-jdk-linux-x64.tar.gz")
        runGradleExpectingFailure(projectRoot, "help")
            .shouldContain("does not match the generated wrapper scripts")

        // The wrapper task itself must stay runnable to fix the situation.
        prepareWrapper(projectRoot)
        prepareWrapperWithArguments(projectRoot, "help")
            .shouldNotContain("does not match the generated wrapper scripts")
    }

    @Test
    fun outdatedWrapperCheckAllowsAbbreviatedWrapperTaskName(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.mkdirs()
        fun buildScriptWithUrl(url: String) = withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                failOnOutdatedWrapper = true
                linuxX64JvmUrl = "$url"
            }
        """}

        buildScriptWithUrl(PluginExtension.DEFAULT_LINUX_X64_JVM_URL)
        prepareWrapper(projectRoot)
        buildScriptWithUrl("https://example.com/custom-jdk-linux-x64.tar.gz")

        // 'wrap' resolves to the 'wrapper' task via Gradle's task name abbreviation;
        // the strict mode must not fail the build before the wrapper can regenerate.
        prepareWrapperWithArguments(projectRoot, "wrap")
        prepareWrapperWithArguments(projectRoot, "help")
            .shouldNotContain("does not match the generated wrapper scripts",
                "No warning expected after regeneration via an abbreviated task name")
    }

    @Test
    fun outdatedWrapperCheckAllowsWrapperViaDependencyAndCaseVariants(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.mkdirs()
        val outdatedMessage = "does not match the generated wrapper scripts"
        fun buildScriptWithUrl(url: String) = withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                failOnOutdatedWrapper = true
                linuxX64JvmUrl = "$url"
            }
            tasks.register("regenerate") {
                dependsOn("wrapper")
            }
        """}

        buildScriptWithUrl(PluginExtension.DEFAULT_LINUX_X64_JVM_URL)
        prepareWrapper(projectRoot)

        // Gradle resolves task names case-insensitively; the strict mode must not
        // fail the build before the wrapper task can regenerate the scripts.
        buildScriptWithUrl("https://example.com/custom-jdk-linux-x64.tar.gz")
        prepareWrapperWithArguments(projectRoot, "WRAPPER")
        prepareWrapperWithArguments(projectRoot, "help").shouldNotContain(outdatedMessage,
            "No warning expected after regeneration via a case variant of the task name")

        // The wrapper task may also run as a dependency of another task.
        buildScriptWithUrl("https://example.com/other-jdk-linux-x64.tar.gz")
        prepareWrapperWithArguments(projectRoot, "regenerate")
        prepareWrapperWithArguments(projectRoot, "help").shouldNotContain(outdatedMessage,
            "No warning expected after regeneration via a task dependency")
    }

    @Test
    fun outdatedWrapperCheckIsConfigurationCacheCompatible(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.mkdirs()
        val outdatedMessage = "does not match the generated wrapper scripts"
        fun buildScriptWithUrl(url: String) = withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                linuxX64JvmUrl = "$url"
            }
        """}

        buildScriptWithUrl(PluginExtension.DEFAULT_LINUX_X64_JVM_URL)
        prepareWrapper(projectRoot)

        val stored = prepareWrapperWithArguments(projectRoot, "help", "--configuration-cache")
        stored.shouldContain("Configuration cache entry stored",
            "Expected the check to be configuration cache compatible:\n$stored")
        stored.shouldNotContain(outdatedMessage, "No warning expected for an up-to-date wrapper:\n$stored")

        val reused = prepareWrapperWithArguments(projectRoot, "help", "--configuration-cache")
        reused.shouldContain("Reusing configuration cache", "Expected cache reuse:\n$reused")

        // A configuration change invalidates the entry and surfaces the warning.
        buildScriptWithUrl("https://example.com/custom-jdk-linux-x64.tar.gz")
        val invalidated = prepareWrapperWithArguments(projectRoot, "help", "--configuration-cache")
        invalidated.shouldContain(outdatedMessage, "Expected a warning after a config change:\n$invalidated")

        // Regenerating the wrapper changes the scripts; the file reads of the check are
        // build configuration inputs, so the entry is invalidated and the check goes silent.
        prepareWrapperWithArguments(projectRoot, "wrapper", "--configuration-cache")
        val fixed = prepareWrapperWithArguments(projectRoot, "help", "--configuration-cache")
        fixed.shouldContain("Configuration cache entry stored",
            "Expected the entry to be invalidated by the regenerated scripts:\n$fixed")
        fixed.shouldNotContain(outdatedMessage, "No warning expected after regeneration:\n$fixed")
    }

    @Test
    fun wrapperTaskSupportsConfigurationCache(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.mkdirs()
        withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                winJvmInstallDir = "build\\test-temp-dir\\gradle-jvm"
                unixJvmInstallDir = "build/test-temp-dir/gradle-jvm"
            }
        """}

        val firstRun = prepareWrapperWithArguments(projectRoot, ":wrapper", "--configuration-cache")
        firstRun.shouldContain("Configuration cache entry stored",
            "Expected the first run to store a configuration cache entry:\n$firstRun")

        val secondRun = prepareWrapperWithArguments(projectRoot, ":wrapper", "--configuration-cache")
        secondRun.shouldContain("Reusing configuration cache",
            "Expected the second run to reuse the configuration cache:\n$secondRun")
    }

    @Test
    fun invalidConfigurationValuesFailAtConfigurationTime(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.mkdirs()
        fun buildScriptWith(configuration: String) = withBuildScript(projectRoot) { """
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
            jvmWrapper {
                $configuration
            }
        """}

        // Values the generated scripts cannot quote away must be rejected up front.
        listOf(
            """linuxX64JvmUrl = "https://example.com/jdk with space.tar.gz"""" to "without spaces",
            """linuxX64JvmUrl = "https://example.com/jdk\"quote.tar.gz"""" to "must not contain",
            """linuxX64JvmUrl = "https://example.com/jdk\${'$'}{HOME}.tar.gz"""" to "must not contain",
            """winJvmInstallDir = "C:\\path\\with\"quote"""" to "must not contain",
            """unixJvmInstallDir = "${'$'}(rm -rf /)/jvm"""" to "command substitution",
        ).forEach { (configuration, expectedError) ->
            buildScriptWith(configuration)
            prepareWrapperExpectingFailure(projectRoot)
                .shouldContain(expectedError, "Expected '$configuration' to be rejected")
        }

        // Characters the scripts do quote correctly must stay usable (signed URLs use '&').
        buildScriptWith("""linuxX64JvmUrl = "https://example.com/jdk.tar.gz?token=a&expires=1"""")
        prepareWrapper(projectRoot)
    }

    @Test
    fun applyingToASubprojectFailsWithAClearError(@TempDir tempDir: Path) {
        val projectRoot = tempDir.resolve("project").toFile()
        projectRoot.resolve("sub").mkdirs()
        projectRoot.resolve("settings.gradle.kts").writeText("""include("sub")""")
        projectRoot.resolve("build.gradle.kts").writeText("")
        projectRoot.resolve("sub").resolve("build.gradle.kts").writeText("""
            plugins {
              id("me.filippov.gradle.jvm.wrapper")
            }
        """.trimIndent())

        val output = runGradleExpectingFailure(projectRoot, "help")
        output.shouldContain("Apply the plugin to the root project",
            "Expected a clear root-only error:\n$output")
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
        val projectRoot = projectRoot(tempDir, "folder with space")
        val jvmInstallDir = projectRoot.resolve("build").resolve("test-temp-dir").resolve("gradle-jvm")
        val absJvmDir = jvmInstallDir.absolutePath.replace("\\", "\\\\")

        // The JVM URL the wrapper will pick on this platform (the plugin defaults) and
        // its published checksum from the vendor's sidecar file.
        val arch = System.getProperty("os.arch").lowercase(Locale.ENGLISH)
        val isArm = arch == "aarch64" || arch == "arm64"
        val (platform, jvmUrl) = when {
            isWindows && isArm -> "windowsAarch64" to PluginExtension.DEFAULT_WINDOWS_AARCH64_JVM_URL
            isWindows -> "windowsX64" to PluginExtension.DEFAULT_WINDOWS_X64_JVM_URL
            isMac && isArm -> "macAarch64" to PluginExtension.DEFAULT_MAC_AARCH64_JVM_URL
            isMac -> "macX64" to PluginExtension.DEFAULT_MAC_X64_JVM_URL
            isArm -> "linuxAarch64" to PluginExtension.DEFAULT_LINUX_AARCH64_JVM_URL
            else -> "linuxX64" to PluginExtension.DEFAULT_LINUX_X64_JVM_URL
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

    }

    private fun doSmoke(tempDir: Path, windowsX64Url: String) {
        // The apostrophe covers install paths like C:\Users\O'Brien\...
        val projectRoot = projectRoot(tempDir, "folder with space's")
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

        resultWhenJavaNotExists.stdout.shouldContain("Downloading ",
            "'Downloading' not found in output:\nSTDOUT:\n${resultWhenJavaNotExists.stdout}\nSTDERR:\n${resultWhenJavaNotExists.stderr}\n"
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
        resultWhenJavaExists.stdout.shouldNotContain("Downloading ",
            "Unexpected download on a warm run:\nSTDOUT:\n${resultWhenJavaExists.stdout}\nSTDERR:\n${resultWhenJavaExists.stderr}\n")
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
        resultAfterJavaUpdate.stdout.shouldContain("Downloading ",
            "'Downloading' not found in output:\nSTDOUT:\n${resultAfterJavaUpdate.stdout}\nSTDERR:\n${resultAfterJavaUpdate.stderr}\n")
        resultAfterJavaUpdate.stderr.shouldBeEmpty("Non empty stderr:\n" + resultAfterJavaUpdate.stderr)
        resultAfterJavaUpdate.exitCode.shouldBe(0)

        val jdkDirs = projectRoot.resolve("build").resolve("test-temp-dir").resolve("gradle-jvm").list()!!
        jdkDirs.size.shouldBe(2)
    }
}
