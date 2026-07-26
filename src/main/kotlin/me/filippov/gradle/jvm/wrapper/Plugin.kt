package me.filippov.gradle.jvm.wrapper

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.wrapper.Wrapper
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
    }

    private fun String.sha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun getJvmDirName(url: String) =
        url.substringAfterLast('/').removeSuffix(".zip").removeSuffix(".tar.gz") +
                "-" +
                url.sha256().take(6)

    override fun apply(project: Project) {
        val cfg = project.extensions.create(extensionName, PluginExtension::class.java)
        project.tasks.getByName(wrapperTaskName) {
            val task = it as Wrapper
            project.afterEvaluate {
                val unixJvmScript = """
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
                      [ -n "${'$'}(ls "${'$'}JVM_TARGET_DIR" 2>/dev/null)" ] && grep -q -x "${'$'}JVM_URL" "${'$'}JVM_TARGET_DIR/.flag" 2>/dev/null
                    }
                    KEEP_ROSETTA2=${cfg.keepRosetta2}
                    BUILD_DIR="${cfg.unixJvmInstallDir}"
                    JVM_ARCH=${'$'}(uname -m)
                    if [ "${"$"}darwin" = "true" ] && ! ${"$"}KEEP_ROSETTA2 && [ "${'$'}(sysctl -n sysctl.proc_translated 2>/dev/null || true)" = "1" ]; then
                        JVM_ARCH=arm64
                    fi
                    JVM_TEMP_FILE=${"$"}BUILD_DIR/gradle-jvm-temp.tar.gz
                    if [ "${"$"}darwin" = "true" ]; then
                        case ${"$"}JVM_ARCH in
                        x86_64)
                            JVM_URL=${cfg.macX64JvmUrl}
                            JVM_SHA256=${cfg.macX64JvmSha256.lowercase()}
                            JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.macX64JvmUrl)}
                            ;;
                        arm64)
                            JVM_URL=${cfg.macAarch64JvmUrl}
                            JVM_SHA256=${cfg.macAarch64JvmSha256.lowercase()}
                            JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.macAarch64JvmUrl)}
                            ;;
                        *) 
                            die "Unknown architecture ${"$"}JVM_ARCH"
                            ;;
                        esac
                    elif [ "${"$"}cygwin" = "true" ] || [ "${"$"}msys" = "true" ]; then
                        JVM_URL=${cfg.windowsX64JvmUrl}
                        JVM_SHA256=${cfg.windowsX64JvmSha256.lowercase()}
                        JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.windowsX64JvmUrl)}
                    else
                        JVM_ARCH=${'$'}(linux${'$'}(getconf LONG_BIT) uname -m)
                         case ${"$"}JVM_ARCH in
                            x86_64)
                                JVM_URL=${cfg.linuxX64JvmUrl}
                                JVM_SHA256=${cfg.linuxX64JvmSha256.lowercase()}
                                JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.linuxX64JvmUrl)}
                                ;;
                            aarch64)
                                JVM_URL=${cfg.linuxAarch64JvmUrl}
                                JVM_SHA256=${cfg.linuxAarch64JvmSha256.lowercase()}
                                JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.linuxAarch64JvmUrl)}
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
                        if [ -n "${"$"}LOCK_OWNER" ] && grep -q -x "${"$"}LOCK_OWNER" "${"$"}LOCK_FILE" 2>/dev/null; then
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
                          retry_on_error 5 curl ${"$"}CURL_PROGRESS -L --output "${"$"}{JVM_TEMP_FILE}" "${"$"}JVM_URL"
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

                val winJvmScript = """
                    @rem $patchedFileStartMarker

                    setlocal
                    set BUILD_DIR=${cfg.winJvmInstallDir}

                    for /f "tokens=3 delims= " %%A in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v "PROCESSOR_ARCHITECTURE"') do set WIN_ARCH=%%A
                    if "%WIN_ARCH%" equ "AMD64" (
                        set JVM_TARGET_DIR=%BUILD_DIR%\${getJvmDirName(cfg.windowsX64JvmUrl)}\
                        set JVM_URL=${cfg.windowsX64JvmUrl.replace("%", "%%")}
                        set JVM_SHA256=${cfg.windowsX64JvmSha256.lowercase()}
                    ) else if "%WIN_ARCH%" equ "ARM64" (
                        set JVM_TARGET_DIR=%BUILD_DIR%\${getJvmDirName(cfg.windowsAarch64JvmUrl)}\
                        set JVM_URL=${cfg.windowsAarch64JvmUrl.replace("%", "%%")}
                        set JVM_SHA256=${cfg.windowsAarch64JvmSha256.lowercase()}
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
                    ${'$'}lock = New-Object System.Threading.Mutex(${'$'}true, 'Global\gradle-jvm-wrapper-lock', [ref]${'$'}createdNew); ^
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
                            (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%BUILD_DIR%\%JVM_TEMP_FILE%'); ^
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

                task.inputs.property("unixJvmScript", unixJvmScript)
                task.inputs.property("winJvmScript", winJvmScript)

                task.doLast {
                    val unixScriptFile = task.scriptFile
                    val winScriptFile = task.batchScript

                    val unixScriptFileContent = unixScriptFile.readText(Charsets.UTF_8)
                    if (!unixScriptFileContent.contains(patchedFileStartMarker)) {
                        project.logger.debug("Patch $unixScriptFile")
                        val newUnixScriptFileContent = unixScriptFileContent.replace(unixPatchPlaceHolder, unixJvmScript + unixPatchPlaceHolder)
                        unixScriptFile.writeText(newUnixScriptFileContent, Charsets.UTF_8)
                        project.logger.debug("$unixScriptFile patched")
                    } else {
                        project.logger.debug("$unixScriptFile is up-to-date")
                    }
                    val winScriptFileContent = winScriptFile.readText(Charsets.UTF_8)
                    if (!winScriptFileContent.contains(patchedFileStartMarker)) {
                        project.logger.debug("Patch $winScriptFile")
                        val newWinScriptFileContent = winScriptFileContent.replace(winPatchPlaceHolder,
                                if (winScriptFileContent.contains("\r\n")) {
                                    winJvmScript.replace("\n", "\r\n")
                                } else {
                                    winJvmScript
                                } + winPatchPlaceHolder)
                        winScriptFile.writeText(newWinScriptFileContent, Charsets.UTF_8)
                        project.logger.debug("$winScriptFile patched")
                    } else {
                        project.logger.debug("$winScriptFile is up-to-date")
                    }
                }
            }
        }
    }
}
