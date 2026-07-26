package me.filippov.gradle.jvm.wrapper

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.wrapper.Wrapper
import java.io.File
import java.io.Serializable
import java.security.MessageDigest

@Suppress("unused")
class Plugin : Plugin<Project> {
    companion object {
        private const val patchedFileStartMarker = "GRADLE JVM WRAPPER START MARKER"
        private const val patchedFileEndMarker = "GRADLE JVM WRAPPER END MARKER"
        private const val unixPatchPlaceHolder = "# Determine the Java command to use to start the JVM."
        private const val winPatchPlaceHolder = "@rem Find java.exe"
        const val wrapperTaskName = "wrapper"
        const val extensionName = "jvmWrapper"

        // Returns null when the script is already patched. Fails instead of silently
        // producing an unpatched script when the placeholder is not in the template:
        // otherwise every subsequent build would report the wrapper as outdated with
        // no way to fix it by re-running the wrapper task.
        internal fun patchedScriptContent(
            scriptName: String, content: String, jvmScript: String, placeHolder: String): String? {
            if (content.contains(patchedFileStartMarker)) {
                return null
            }
            if (!content.contains(placeHolder)) {
                throw GradleException(
                    "Unable to patch $scriptName: the placeholder '$placeHolder' was not found. " +
                            "The wrapper script layout of this Gradle version is not supported " +
                            "by the gradle-jvm-wrapper plugin.")
            }
            val script = if (content.contains("\r\n")) jvmScript.replace("\n", "\r\n") else jvmScript
            return content.replace(placeHolder, script + placeHolder)
        }
    }

    private data class ResolvedConfig(
        val winJvmInstallDir: String,
        val unixJvmInstallDir: String,
        val keepRosetta2: Boolean,
        val windowsAarch64JvmUrl: String,
        val windowsX64JvmUrl: String,
        val linuxAarch64JvmUrl: String,
        val linuxX64JvmUrl: String,
        val macAarch64JvmUrl: String,
        val macX64JvmUrl: String,
        val windowsAarch64JvmSha256: String,
        val windowsX64JvmSha256: String,
        val linuxAarch64JvmSha256: String,
        val linuxX64JvmSha256: String,
        val macAarch64JvmSha256: String,
        val macX64JvmSha256: String,
    ) : Serializable

    private fun String.sha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun getJvmDirName(url: String) =
        url.substringAfterLast('/').removeSuffix(".zip").removeSuffix(".tar.gz") +
                "-" +
                url.sha256().take(6)

    private fun effectiveSha256(name: String, configured: String, url: String): String {
        if (configured.isNotEmpty() && !configured.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw GradleException(
                "jvmWrapper.$name must be a 64-character hexadecimal SHA-256 checksum, got: '$configured'")
        }
        return configured.ifEmpty { PluginExtension.defaultJvmSha256[url] ?: "" }.lowercase()
    }

    // The values below are embedded into sh, batch and PowerShell sources. Everything the
    // generated scripts cannot quote away is rejected here, at configuration time, instead
    // of corrupting the scripts (or executing as code) on the machine of whoever runs them.
    private fun validatedValue(name: String, value: String, forbidden: String, allowSpaces: Boolean): String {
        val floor = if (allowSpaces) 0x20 else 0x21
        if (value.isEmpty() || !value.all { it.code in floor..0x7e }) {
            throw GradleException(
                "jvmWrapper.$name must be a non-empty string of printable ASCII characters" +
                        (if (allowSpaces) "" else " without spaces") + ", got: '$value'")
        }
        val bad = value.firstOrNull { it in forbidden }
        if (bad != null) {
            throw GradleException(
                "jvmWrapper.$name must not contain the character '$bad', " +
                        "it cannot be safely embedded in the generated wrapper scripts, got: '$value'")
        }
        return value
    }

    private fun validatedUrl(name: String, url: String) =
        validatedValue(name, url, "\"'`\\$", allowSpaces = false)

    private fun validatedUnixDir(name: String, dir: String): String {
        validatedValue(name, dir, "\"`", allowSpaces = true)
        if (dir.contains("$(")) {
            throw GradleException(
                "jvmWrapper.$name must not contain a command substitution '$(', got: '$dir'")
        }
        return dir
    }

    private fun resolveConfig(cfg: PluginExtension): ResolvedConfig {
        val windowsAarch64JvmUrl = validatedUrl("windowsAarch64JvmUrl", cfg.windowsAarch64JvmUrl.get())
        val windowsX64JvmUrl = validatedUrl("windowsX64JvmUrl", cfg.windowsX64JvmUrl.get())
        val linuxAarch64JvmUrl = validatedUrl("linuxAarch64JvmUrl", cfg.linuxAarch64JvmUrl.get())
        val linuxX64JvmUrl = validatedUrl("linuxX64JvmUrl", cfg.linuxX64JvmUrl.get())
        val macAarch64JvmUrl = validatedUrl("macAarch64JvmUrl", cfg.macAarch64JvmUrl.get())
        val macX64JvmUrl = validatedUrl("macX64JvmUrl", cfg.macX64JvmUrl.get())
        return ResolvedConfig(
            winJvmInstallDir = validatedValue(
                "winJvmInstallDir", cfg.winJvmInstallDir.get(), "\"", allowSpaces = true),
            unixJvmInstallDir = validatedUnixDir("unixJvmInstallDir", cfg.unixJvmInstallDir.get()),
            keepRosetta2 = cfg.keepRosetta2.get(),
            windowsAarch64JvmUrl = windowsAarch64JvmUrl,
            windowsX64JvmUrl = windowsX64JvmUrl,
            linuxAarch64JvmUrl = linuxAarch64JvmUrl,
            linuxX64JvmUrl = linuxX64JvmUrl,
            macAarch64JvmUrl = macAarch64JvmUrl,
            macX64JvmUrl = macX64JvmUrl,
            windowsAarch64JvmSha256 =
                effectiveSha256("windowsAarch64JvmSha256", cfg.windowsAarch64JvmSha256.get(), windowsAarch64JvmUrl),
            windowsX64JvmSha256 =
                effectiveSha256("windowsX64JvmSha256", cfg.windowsX64JvmSha256.get(), windowsX64JvmUrl),
            linuxAarch64JvmSha256 =
                effectiveSha256("linuxAarch64JvmSha256", cfg.linuxAarch64JvmSha256.get(), linuxAarch64JvmUrl),
            linuxX64JvmSha256 =
                effectiveSha256("linuxX64JvmSha256", cfg.linuxX64JvmSha256.get(), linuxX64JvmUrl),
            macAarch64JvmSha256 =
                effectiveSha256("macAarch64JvmSha256", cfg.macAarch64JvmSha256.get(), macAarch64JvmUrl),
            macX64JvmSha256 =
                effectiveSha256("macX64JvmSha256", cfg.macX64JvmSha256.get(), macX64JvmUrl),
        )
    }

    private fun generateUnixJvmScript(c: ResolvedConfig) = """
        # $patchedFileStartMarker
        retry_on_error () {
          n="${'$'}1"
          shift
          for _ in ${'$'}(seq 2 "${'$'}n"); do
            "${'$'}@" 2>&1 && return || echo "WARNING: Command '${'$'}1' returned non-zero exit status ${'$'}?, try again"
          done
          "${'$'}@"
        }
        jvm_is_up_to_date () {
          [ -n "${'$'}(ls "${'$'}JVM_TARGET_DIR" 2>/dev/null)" ] && grep -F -q -x "${'$'}JVM_URL" "${'$'}JVM_TARGET_DIR/.flag" 2>/dev/null
        }
        KEEP_ROSETTA2=${c.keepRosetta2}
        BUILD_DIR="${c.unixJvmInstallDir}"
        JVM_ARCH=${'$'}(uname -m)
        if [ "${"$"}darwin" = "true" ] && ! ${"$"}KEEP_ROSETTA2 && [ "${'$'}(sysctl -n sysctl.proc_translated 2>/dev/null || true)" = "1" ]; then
            JVM_ARCH=arm64
        fi
        JVM_TEMP_FILE="${"$"}BUILD_DIR/gradle-jvm-temp.tar.gz"
        if [ "${"$"}darwin" = "true" ]; then
            case ${"$"}JVM_ARCH in
            x86_64)
                JVM_URL="${c.macX64JvmUrl}"
                JVM_SHA256="${c.macX64JvmSha256}"
                JVM_TARGET_DIR="${"$"}BUILD_DIR/${getJvmDirName(c.macX64JvmUrl)}"
                ;;
            arm64)
                JVM_URL="${c.macAarch64JvmUrl}"
                JVM_SHA256="${c.macAarch64JvmSha256}"
                JVM_TARGET_DIR="${"$"}BUILD_DIR/${getJvmDirName(c.macAarch64JvmUrl)}"
                ;;
            *)
                die "Unknown architecture ${"$"}JVM_ARCH"
                ;;
            esac
        elif [ "${"$"}cygwin" = "true" ] || [ "${"$"}msys" = "true" ]; then
            case ${"$"}JVM_ARCH in
            aarch64 | arm64)
                JVM_URL="${c.windowsAarch64JvmUrl}"
                JVM_SHA256="${c.windowsAarch64JvmSha256}"
                JVM_TARGET_DIR="${"$"}BUILD_DIR/${getJvmDirName(c.windowsAarch64JvmUrl)}"
                ;;
            *)
                JVM_URL="${c.windowsX64JvmUrl}"
                JVM_SHA256="${c.windowsX64JvmSha256}"
                JVM_TARGET_DIR="${"$"}BUILD_DIR/${getJvmDirName(c.windowsX64JvmUrl)}"
                ;;
            esac
        else
            JVM_ARCH=${'$'}(linux${'$'}(getconf LONG_BIT) uname -m)
            case ${"$"}JVM_ARCH in
                x86_64)
                    JVM_URL="${c.linuxX64JvmUrl}"
                    JVM_SHA256="${c.linuxX64JvmSha256}"
                    JVM_TARGET_DIR="${"$"}BUILD_DIR/${getJvmDirName(c.linuxX64JvmUrl)}"
                    ;;
                aarch64)
                    JVM_URL="${c.linuxAarch64JvmUrl}"
                    JVM_SHA256="${c.linuxAarch64JvmSha256}"
                    JVM_TARGET_DIR="${"$"}BUILD_DIR/${getJvmDirName(c.linuxAarch64JvmUrl)}"
                    ;;
                *)
                    die "Unknown architecture ${"$"}JVM_ARCH"
                    ;;
                esac
        fi

        set -e

        if jvm_is_up_to_date; then
            # Everything is up-to-date in ${"$"}JVM_TARGET_DIR, do nothing
            true
        else
        while true; do  # Note: emulates goto
          mkdir -p "${"$"}BUILD_DIR"
          LOCK_FILE=${"$"}BUILD_DIR/.gradle-jvm-lock.pid
          TMP_LOCK_FILE=${"$"}BUILD_DIR/.tmp.${'$'}${'$'}.pid
          echo ${'$'}${'$'} >"${"$"}TMP_LOCK_FILE"
          LN_FAILED_COUNT=0
          while ! ln "${"$"}TMP_LOCK_FILE" "${"$"}LOCK_FILE" 2>/dev/null; do
            if [ ! -e "${"$"}LOCK_FILE" ]; then
              # ln failed, but there is no lock file: either the owner has just released it
              # (retry will succeed), or the filesystem does not support hard links.
              LN_FAILED_COUNT=${'$'}((LN_FAILED_COUNT + 1))
              if [ "${"$"}LN_FAILED_COUNT" -ge 10 ]; then
                rm -f "${"$"}TMP_LOCK_FILE"
                die "ERROR: Unable to create the lock file ${"$"}LOCK_FILE. Check that the filesystem supports hard links."
              fi
              sleep 1
              continue
            fi
            LN_FAILED_COUNT=0
            LOCK_OWNER=${'$'}(cat "${"$"}LOCK_FILE" 2>/dev/null || true)
            while [ -n "${"$"}LOCK_OWNER" ] && kill -0 "${"$"}LOCK_OWNER" 2>/dev/null; do
              warn "Waiting for the process ${"$"}LOCK_OWNER to finish the JVM bootstrap"
              sleep 1
              LOCK_OWNER=${'$'}(cat "${"$"}LOCK_FILE" 2>/dev/null || true)
              # Hurry up, bootstrap is ready..
              if jvm_is_up_to_date; then
                rm -f "${"$"}TMP_LOCK_FILE"
                break 3  # Note: goto out of the outer if-else block.
              fi
            done
            if [ -n "${"$"}LOCK_OWNER" ] && grep -F -q -x "${"$"}LOCK_OWNER" "${"$"}LOCK_FILE" 2>/dev/null; then
              die "ERROR: The lock file ${"$"}LOCK_FILE still exists on disk after the owner process ${"$"}LOCK_OWNER exited"
            fi
          done
          trap 'rm -f "${"$"}LOCK_FILE"' EXIT
          rm "${"$"}TMP_LOCK_FILE"
          if ! jvm_is_up_to_date; then
          echo "Downloading ${"$"}JVM_URL to ${"$"}JVM_TEMP_FILE"

          rm -f "${"$"}JVM_TEMP_FILE"
          mkdir -p "${"$"}BUILD_DIR"
          if command -v curl >/dev/null 2>&1; then
              if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
              # shellcheck disable=SC2086
              retry_on_error 5 curl ${"$"}CURL_PROGRESS --fail -L --output "${"$"}{JVM_TEMP_FILE}" "${"$"}JVM_URL"
          elif command -v wget >/dev/null 2>&1; then
              if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
              retry_on_error 5 wget ${"$"}WGET_PROGRESS -O "${"$"}{JVM_TEMP_FILE}" "${"$"}JVM_URL"
          else
              die "ERROR: Please install wget or curl"
          fi

          if [ -n "${"$"}JVM_SHA256" ]; then
            if command -v sha256sum >/dev/null 2>&1; then
              ACTUAL_SHA256=${'$'}(sha256sum "${"$"}JVM_TEMP_FILE" | cut -d' ' -f1)
            elif command -v shasum >/dev/null 2>&1; then
              ACTUAL_SHA256=${'$'}(shasum -a 256 "${"$"}JVM_TEMP_FILE" | cut -d' ' -f1)
            else
              die "ERROR: Please install sha256sum or shasum to verify the downloaded JVM"
            fi
            if [ "${"$"}ACTUAL_SHA256" != "${"$"}JVM_SHA256" ]; then
              rm -f "${"$"}JVM_TEMP_FILE"
              die "ERROR: SHA-256 mismatch for ${"$"}JVM_URL: expected ${"$"}JVM_SHA256, actual ${"$"}ACTUAL_SHA256"
            fi
          fi

          echo "Extracting ${"$"}JVM_TEMP_FILE to ${"$"}JVM_TARGET_DIR"
          rm -rf "${"$"}JVM_TARGET_DIR"
          mkdir -p "${"$"}JVM_TARGET_DIR"

          case "${'$'}JVM_URL" in
            *".zip") unzip "${"$"}JVM_TEMP_FILE" -d "${"$"}JVM_TARGET_DIR" ;;
            *) tar -x -f "${"$"}JVM_TEMP_FILE" -C "${"$"}JVM_TARGET_DIR" ;;
          esac

          rm -f "${"$"}JVM_TEMP_FILE"

          echo "${"$"}JVM_URL" >"${"$"}JVM_TARGET_DIR/.flag"
          fi
          rm "${"$"}LOCK_FILE"
          trap - EXIT
          break
        done
        fi

        JAVA_HOME=
        for d in "${"$"}JVM_TARGET_DIR" "${"$"}JVM_TARGET_DIR"/* "${"$"}JVM_TARGET_DIR"/Contents/Home "${"$"}JVM_TARGET_DIR"/*/Contents/Home; do
          if [ -e "${"$"}d/bin/java" ]; then
            JAVA_HOME="${"$"}d"
          fi
        done

        if [ '!' -e "${"$"}JAVA_HOME/bin/java" ]; then
          die "Unable to find bin/java under ${"$"}JVM_TARGET_DIR"
        fi

        # Make it available for child processes
        export JAVA_HOME

        set +e

        # $patchedFileEndMarker
    """.trimIndent() + "\n\n"

    private fun generateWinJvmScript(c: ResolvedConfig) = """
        @rem $patchedFileStartMarker

        setlocal
        set BUILD_DIR=${c.winJvmInstallDir}

        for /f "tokens=3 delims= " %%A in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v "PROCESSOR_ARCHITECTURE"') do set WIN_ARCH=%%A
        if "%WIN_ARCH%" equ "AMD64" (
            set JVM_TARGET_DIR=%BUILD_DIR%\${getJvmDirName(c.windowsX64JvmUrl)}\
            set JVM_URL=${c.windowsX64JvmUrl.replace("%", "%%")}
            set JVM_SHA256=${c.windowsX64JvmSha256}
        ) else if "%WIN_ARCH%" equ "ARM64" (
            set JVM_TARGET_DIR=%BUILD_DIR%\${getJvmDirName(c.windowsAarch64JvmUrl)}\
            set JVM_URL=${c.windowsAarch64JvmUrl.replace("%", "%%")}
            set JVM_SHA256=${c.windowsAarch64JvmSha256}
        ) else (
            echo Unknown architecture %WIN_ARCH%
            goto fail
        )

        set IS_TAR_GZ=0
        set JVM_TEMP_FILE=gradle-jvm.zip

        if /I "%JVM_URL:~-7%"==".tar.gz" (
            set IS_TAR_GZ=1
            set JVM_TEMP_FILE=gradle-jvm.tar.gz
        )

        set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe
        set JVM_DOWNLOAD_ATTEMPTED=0

        if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm

        set /p CURRENT_FLAG=<"%JVM_TARGET_DIR%.flag"
        if "%CURRENT_FLAG%" == "%JVM_URL%" goto continueWithJvm

        :downloadAndExtractJvm

        set JVM_DOWNLOAD_ATTEMPTED=1

        set DOWNLOAD_AND_EXTRACT_JVM_PS1= ^
        Set-StrictMode -Version 3.0; ^
        ${'$'}ErrorActionPreference = 'Stop'; ^
         ^
        ${'$'}createdNew = ${'$'}false; ^
        ${'$'}lockName = 'Global\gradle-jvm-wrapper-' + '%BUILD_DIR%'.ToLowerInvariant().Replace('\', '-'); ^
        ${'$'}lock = New-Object System.Threading.Mutex(${'$'}true, ${'$'}lockName, [ref]${'$'}createdNew); ^
        if (-not ${'$'}createdNew) { ^
            Write-Host 'Waiting for the other process to finish the JVM bootstrap'; ^
            try { [void]${'$'}lock.WaitOne(); } catch [System.Threading.AbandonedMutexException] { } ^
        } ^
         ^
        try { ^
            if ((Get-Content '%JVM_TARGET_DIR%.flag' -ErrorAction Ignore) -ne '%JVM_URL%') { ^
                [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
                Write-Host 'Downloading %JVM_URL% to %BUILD_DIR%\%JVM_TEMP_FILE%'; ^
                [void](New-Item '%BUILD_DIR%' -ItemType Directory -Force); ^
                ${'$'}downloadAttempt = 1; ^
                while (${'$'}true) { ^
                    try { ^
                        (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%BUILD_DIR%\%JVM_TEMP_FILE%'); ^
                        break; ^
                    } catch { ^
                        if (${'$'}downloadAttempt -ge 5) { throw; } ^
                        Write-Host ('WARNING: Download failed: ' + ${'$'}_.Exception.Message + ', try again'); ^
                        ${'$'}downloadAttempt = ${'$'}downloadAttempt + 1; ^
                    } ^
                } ^
                 ^
                if (-not [string]::IsNullOrEmpty(${'$'}env:JVM_SHA256)) { ^
                    ${'$'}sha256Stream = [System.IO.File]::OpenRead('%BUILD_DIR%\%JVM_TEMP_FILE%'); ^
                    try { ${'$'}hashBytes = [System.Security.Cryptography.SHA256]::Create().ComputeHash(${'$'}sha256Stream); } finally { ${'$'}sha256Stream.Close(); } ^
                    ${'$'}actualSha256 = ([System.BitConverter]::ToString(${'$'}hashBytes) -replace '-', '').ToLowerInvariant(); ^
                    if (${'$'}actualSha256 -ne ${'$'}env:JVM_SHA256) { ^
                        Remove-Item '%BUILD_DIR%\%JVM_TEMP_FILE%'; ^
                        throw ('SHA-256 mismatch for %JVM_URL%: expected ' + ${'$'}env:JVM_SHA256 + ', actual ' + ${'$'}actualSha256); ^
                    } ^
                } ^
                 ^
                Write-Host 'Extracting %BUILD_DIR%\%JVM_TEMP_FILE% to %JVM_TARGET_DIR%'; ^
                if (Test-Path '%JVM_TARGET_DIR%') { ^
                    Remove-Item '%JVM_TARGET_DIR%' -Recurse -Force; ^
                } ^
                [void](New-Item '%JVM_TARGET_DIR%' -ItemType Directory -Force); ^
                if ('%IS_TAR_GZ%' -eq '1') { ^
                    tar -x -f '%BUILD_DIR%\%JVM_TEMP_FILE%' -C '%JVM_TARGET_DIR%.'; ^
                    if (${'$'}LASTEXITCODE -ne 0) { throw 'tar extraction failed'; } ^
                } else { ^
                    Add-Type -A 'System.IO.Compression.FileSystem'; ^
                    [IO.Compression.ZipFile]::ExtractToDirectory('%BUILD_DIR%\%JVM_TEMP_FILE%', '%JVM_TARGET_DIR%'); ^
                } ^
                Remove-Item '%BUILD_DIR%\%JVM_TEMP_FILE%'; ^
                 ^
                Set-Content '%JVM_TARGET_DIR%.flag' -Value '%JVM_URL%'; ^
            } ^
        } ^
        finally { ^
            [void]${'$'}lock.ReleaseMutex(); ^
        }

        "%POWERSHELL%" -nologo -noprofile -Command %DOWNLOAD_AND_EXTRACT_JVM_PS1%
        if errorlevel 1 goto fail

        :continueWithJvm

        set JAVA_HOME=
        for /d %%d in ("%JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set JAVA_HOME=%%d
        if not exist "%JAVA_HOME%\bin\java.exe" (
          if "%JVM_DOWNLOAD_ATTEMPTED%"=="0" (
            DEL /F /Q "%JVM_TARGET_DIR%.flag" 2>NUL
            goto downloadAndExtractJvm
          )
          echo Unable to find java.exe under %JVM_TARGET_DIR%
          goto fail
        )

        endlocal & set JAVA_HOME=%JAVA_HOME%

        @rem $patchedFileEndMarker
    """.trimIndent() + "\n\n"

    private fun extractPatchedBlock(content: String): String? {
        val start = content.indexOf(patchedFileStartMarker)
        val end = content.indexOf(patchedFileEndMarker)
        if (start < 0 || end < start) return null
        return content.substring(start, end)
    }

    private fun checkWrapperScriptsUpToDate(project: Project, cfg: PluginExtension, task: Wrapper) {
        val config = resolveConfig(cfg)
        val outdated = listOf(
            task.scriptFile to generateUnixJvmScript(config),
            task.batchScript to generateWinJvmScript(config),
        ).filter { (scriptFile, expectedScript) ->
            scriptFile.exists() &&
                    extractPatchedBlock(scriptFile.readText(Charsets.UTF_8).replace("\r\n", "\n")) !=
                    extractPatchedBlock(expectedScript)
        }.map { it.first.name }
        if (outdated.isEmpty()) {
            return
        }
        val message = "The jvmWrapper configuration does not match the generated wrapper scripts " +
                "(${outdated.joinToString(", ")}). Run the '$wrapperTaskName' task to regenerate them."
        if (cfg.failOnOutdatedWrapper.get()) {
            throw GradleException(message)
        }
        project.logger.warn(message)
    }

    private fun patchScriptFile(scriptFile: File, jvmScript: String, placeHolder: String, logger: Logger) {
        val content = scriptFile.readText(Charsets.UTF_8)
        val patched = patchedScriptContent(scriptFile.name, content, jvmScript, placeHolder)
        if (patched == null) {
            logger.debug("{} is up-to-date", scriptFile)
            return
        }
        logger.debug("Patch {}", scriptFile)
        scriptFile.writeText(patched, Charsets.UTF_8)
        logger.debug("{} patched", scriptFile)
    }

    override fun apply(project: Project) {
        if (project != project.rootProject) {
            throw GradleException(
                "The me.filippov.gradle.jvm.wrapper plugin patches the wrapper scripts of the " +
                        "'$wrapperTaskName' task, which only exists in the root project. " +
                        "Apply the plugin to the root project instead of '${project.path}'.")
        }
        val cfg = project.extensions.create(extensionName, PluginExtension::class.java)
        val unixJvmScript = project.provider { generateUnixJvmScript(resolveConfig(cfg)) }
        val winJvmScript = project.provider { generateWinJvmScript(resolveConfig(cfg)) }
        project.gradle.taskGraph.whenReady { graph ->
            val task = project.tasks.named(wrapperTaskName, Wrapper::class.java).get()
            // The task graph knows every way the wrapper task can run in this build
            // (exact name, abbreviation, any letter case, a dependency of another task,
            // an included-build invocation), so the check never blocks a regeneration.
            if (!graph.hasTask(task)) {
                // Configuration-time check: file reads below become build configuration inputs,
                // so a configuration cache entry is invalidated when the scripts change.
                checkWrapperScriptsUpToDate(project, cfg, task)
            }
        }
        project.tasks.named(wrapperTaskName, Wrapper::class.java).configure { task ->
            task.inputs.property("unixJvmScript", unixJvmScript)
            task.inputs.property("winJvmScript", winJvmScript)

            task.doLast {
                val wrapper = it as Wrapper
                patchScriptFile(
                    wrapper.scriptFile,
                    wrapper.inputs.properties["unixJvmScript"] as String,
                    unixPatchPlaceHolder,
                    wrapper.logger)
                patchScriptFile(
                    wrapper.batchScript,
                    wrapper.inputs.properties["winJvmScript"] as String,
                    winPatchPlaceHolder,
                    wrapper.logger)
            }
        }
    }
}
