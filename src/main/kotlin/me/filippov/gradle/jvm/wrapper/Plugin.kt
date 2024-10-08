package me.filippov.gradle.jvm.wrapper

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.wrapper.Wrapper
import java.security.MessageDigest

@Suppress("unused")
class Plugin : Plugin<Project> {
    companion object {
        private const val PATCHED_FILE_START_MARKER = "GRADLE JVM WRAPPER START MARKER"
        private const val PATCHED_FILE_END_MARKER = "GRADLE JVM WRAPPER END MARKER"
        private const val UNIX_PATCH_PLACEHOLDER = "# Determine the Java command to use to start the JVM."
        private const val WINDOWS_PATCH_PLACEHOLDER = "@rem Find java.exe"
        const val WRAPPER_TASK_NAME = "wrapper"
        const val EXTENSION_NAME = "jvmWrapper"
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
        val cfg = project.extensions.create(EXTENSION_NAME, PluginExtension::class.java)
        project.tasks.getByName(WRAPPER_TASK_NAME) {
            val task = it as Wrapper
            project.afterEvaluate {
                val unixJvmScript = """
                    # $PATCHED_FILE_START_MARKER
                    is_linux_musl () {
                      (ldd --version 2>&1 || true) | grep -q musl
                    }
                    retry_on_error () {
                      n="${'$'}1"
                      shift
                      for _ in ${'$'}(seq 2 "${'$'}n"); do
                        "${'$'}@" 2>&1 && return || echo "WARNING: Command '${'$'}1' returned non-zero exit status ${'$'}?, try again"
                      done
                      "${'$'}@"
                    }
                    KEEP_ROSETTA2=${cfg.keepRosetta2}
                    TARGET_DIR="${cfg.unixJvmInstallDir}"
                    JVM_TEMP_FILE=${'$'}(mktemp)
                    case ${'$'}(uname) in
                    Darwin)
                        JVM_ARCH=${'$'}(uname -m)
                        if ! ${'$'}KEEP_ROSETTA2 && [ "${'$'}(sysctl -n sysctl.proc_translated 2>/dev/null || true)" = "1" ]; then
                            JVM_ARCH=arm64
                        fi
                        case ${"$"}JVM_ARCH in
                        x86_64)
                            JVM_URL=${cfg.macX64JvmUrl}
                            JVM_TARGET_DIR=${"$"}TARGET_DIR/${getJvmDirName(cfg.macX64JvmUrl)}
                            ;;
                        arm64)
                            JVM_URL=${cfg.macAarch64JvmUrl}
                            JVM_TARGET_DIR=${"$"}TARGET_DIR/${getJvmDirName(cfg.macAarch64JvmUrl)}
                            ;;
                        *) 
                            die "Unsupported architecture ${"$"}JVM_ARCH"
                            ;;
                        esac
                        ;;
                    Linux)
                        JVM_ARCH=${'$'}(linux"${'$'}(getconf LONG_BIT)" uname -m)
                        case ${"$"}JVM_ARCH in
                            x86_64)
                                JVM_URL=${cfg.linuxX64JvmUrl}
                                JVM_TARGET_DIR=${"$"}TARGET_DIR/${getJvmDirName(cfg.linuxX64JvmUrl)}
                                ;;
                            aarch64)
                                JVM_URL=${cfg.linuxAarch64JvmUrl}
                                JVM_TARGET_DIR=${"$"}TARGET_DIR/${getJvmDirName(cfg.linuxAarch64JvmUrl)}
                                ;;
                            *) 
                                die "Unknown architecture ${"$"}JVM_ARCH"
                                ;;
                            esac
                            ;;
                    *)
                        if [ "${"$"}cygwin" = "true" ] || [ "${"$"}msys" = "true" ]; then
                            JVM_URL=${cfg.windowsX64JvmUrl}
                            JVM_TARGET_DIR=${"$"}TARGET_DIR/${getJvmDirName(cfg.windowsX64JvmUrl)}
                        else
                            JVM_ARCH=${'$'}(linux"${'$'}(getconf LONG_BIT)" uname -m)
                             case ${"$"}JVM_ARCH in
                                x86_64)
                                    JVM_URL=${cfg.linuxX64JvmUrl}
                                    JVM_TARGET_DIR=${"$"}TARGET_DIR/${getJvmDirName(cfg.linuxX64JvmUrl)}
                                    ;;
                                aarch64)
                                    JVM_URL=${cfg.linuxAarch64JvmUrl}
                                    JVM_TARGET_DIR=${"$"}TARGET_DIR/${getJvmDirName(cfg.linuxAarch64JvmUrl)}
                                    ;;
                                *) 
                                    die "Unknown architecture ${"$"}JVM_ARCH"
                                    ;;
                                esac
                        fi
                        ;;
                    esac
                    if grep -q -x "${'$'}JVM_URL" "${'$'}JVM_TARGET_DIR/.flag" 2>/dev/null; then
                      # Everything is up-to-date in ${'$'}JVM_TARGET_DIR, do nothing
                      true
                    else
                    while true; do  # Note: for goto
                      mkdir -p "${'$'}TARGET_DIR"
                      LOCK_FILE="${'$'}TARGET_DIR/.java-cmd-lock.pid"
                      TMP_LOCK_FILE="${'$'}TARGET_DIR/.tmp.${'$'}${'$'}.pid"
                      echo ${'$'}${'$'} >"${'$'}TMP_LOCK_FILE"
                      while ! ln "${'$'}TMP_LOCK_FILE" "${'$'}LOCK_FILE" 2>/dev/null; do
                        LOCK_OWNER=${'$'}(cat "${'$'}LOCK_FILE" 2>/dev/null || true)
                        while [ -n "${'$'}LOCK_OWNER" ] && ps -p "${'$'}LOCK_OWNER" >/dev/null; do
                          warn "Waiting for the process ${'$'}LOCK_OWNER to finish bootstrap java.cmd"
                          sleep 1
                          LOCK_OWNER=${'$'}(cat "${'$'}LOCK_FILE" 2>/dev/null || true)
                          # Hurry up, bootstrap is ready..
                          if grep -q -x "${'$'}JVM_URL" "${'$'}JVM_TARGET_DIR/.flag" 2>/dev/null; then
                            break 3  # Note goto out of the outer if-else block.
                          fi
                        done
                        if [ -n "${'$'}LOCK_OWNER" ] && grep -q -x "${'$'}LOCK_OWNER" "${'$'}LOCK_FILE" 2>/dev/null; then
                          die "ERROR: The lock file ${'$'}LOCK_FILE still exists on disk after the owner process ${'$'}LOCK_OWNER exited"
                        fi
                      done
                      trap 'rm -f \"${'$'}LOCK_FILE\"' EXIT
                      rm "${'$'}TMP_LOCK_FILE"
                      if ! grep -q -x "${'$'}JVM_URL" "${'$'}JVM_TARGET_DIR/.flag" 2>/dev/null; then
                        echo "Downloading ${'$'}JVM_URL to ${'$'}JVM_TEMP_FILE"
                        rm -f "${'$'}JVM_TEMP_FILE"
                        if command -v curl >/dev/null 2>&1; then
                          if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
                          retry_on_error 5 curl -L ${'$'}CURL_PROGRESS --output "${'$'}{JVM_TEMP_FILE}" "${'$'}JVM_URL"
                        elif command -v wget >/dev/null 2>&1; then
                          if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
                          retry_on_error 5 wget ${'$'}WGET_PROGRESS -O "${'$'}{JVM_TEMP_FILE}" "${'$'}JVM_URL"
                        else
                          die "ERROR: Please install wget or curl"
                        fi
                        echo "Extracting ${'$'}JVM_TEMP_FILE to ${'$'}JVM_TARGET_DIR"
                        rm -rf "${'$'}JVM_TARGET_DIR"
                        mkdir -p "${'$'}JVM_TARGET_DIR"
                        tar -x -f "${'$'}JVM_TEMP_FILE" -C "${'$'}JVM_TARGET_DIR"
                        rm -f "${'$'}JVM_TEMP_FILE"
                        echo "${'$'}JVM_URL" >"${'$'}JVM_TARGET_DIR/.flag"
                      fi
                      rm "${'$'}LOCK_FILE"
                      break
                    done
                    fi
                    JAVA_HOME=
                    for d in "${"$"}JVM_TARGET_DIR" "${"$"}JVM_TARGET_DIR"/* "${"$"}JVM_TARGET_DIR"/Contents/Home "${"$"}JVM_TARGET_DIR"/*/Contents/Home; do
                      if [ -e "${"$"}d/bin/java" ]; then
                        JAVA_HOME="${"$"}d"
                      fi
                    done
                    if [ ! -e "${"$"}JAVA_HOME/bin/java" ]; then
                      die "Unable to find bin/java under ${"$"}JVM_TARGET_DIR"
                    fi
                    # Make it available for child processes
                    export JAVA_HOME
                    # $PATCHED_FILE_END_MARKER
                """.trimIndent() + "\n\n"

                val winJvmScript = """
                    @rem $PATCHED_FILE_START_MARKER

                    setlocal
                    set BUILD_DIR=${cfg.winJvmInstallDir}
                    set JVM_TARGET_DIR=%BUILD_DIR%\${getJvmDirName(cfg.windowsX64JvmUrl)}\

                    set JVM_URL=${cfg.windowsX64JvmUrl.replace("%", "%%")}
                    
                    set IS_TAR_GZ=0
                    set JVM_TEMP_FILE=gradle-jvm.zip
                    
                    if /I "%JVM_URL:~-7%"==".tar.gz" (
                        set IS_TAR_GZ=1
                        set JVM_TEMP_FILE=gradle-jvm.tar.gz
                    )

                    set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

                    if not exist "%JVM_TARGET_DIR%" MD "%JVM_TARGET_DIR%"

                    if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm

                    set /p CURRENT_FLAG=<"%JVM_TARGET_DIR%.flag"
                    if "%CURRENT_FLAG%" == "%JVM_URL%" goto continueWithJvm

                    :downloadAndExtractJvm
                    
                    PUSHD "%BUILD_DIR%"
                    if errorlevel 1 goto fail

                    echo Downloading %JVM_URL% to %BUILD_DIR%\%JVM_TEMP_FILE%
                    if exist "%JVM_TEMP_FILE%" DEL /F "%JVM_TEMP_FILE%"
                    "%POWERSHELL%" -nologo -noprofile -Command "Set-StrictMode -Version 3.0; ${"$"}ErrorActionPreference = \"Stop\"; (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%JVM_TEMP_FILE%')"
                    if errorlevel 1 goto fail

                    POPD

                    RMDIR /S /Q "%JVM_TARGET_DIR%"
                    if errorlevel 1 goto fail

                    MKDIR "%JVM_TARGET_DIR%"
                    if errorlevel 1 goto fail

                    PUSHD "%JVM_TARGET_DIR%"
                    if errorlevel 1 goto fail

                    echo Extracting %BUILD_DIR%\%JVM_TEMP_FILE% to %JVM_TARGET_DIR%
                    
                    if "%IS_TAR_GZ%"=="1" (
                        tar xf "..\\%JVM_TEMP_FILE%"
                    ) else (
                        "%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; ${"$"}ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\%JVM_TEMP_FILE%', '.');"
                    )
                    if errorlevel 1 goto fail

                    DEL /F "..\%JVM_TEMP_FILE%"
                    if errorlevel 1 goto fail

                    POPD

                    echo %JVM_URL%>"%JVM_TARGET_DIR%.flag"
                    if errorlevel 1 goto fail

                    :continueWithJvm

                    set JAVA_HOME=
                    for /d %%d in ("%JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set JAVA_HOME=%%d
                    if not exist "%JAVA_HOME%\bin\java.exe" (
                      echo Unable to find java.exe under %JVM_TARGET_DIR%
                      goto fail
                    )

                    endlocal & set JAVA_HOME=%JAVA_HOME%

                    @rem $PATCHED_FILE_END_MARKER
                """.trimIndent() + "\n\n"

                task.inputs.property("unixJvmScript", unixJvmScript)
                task.inputs.property("winJvmScript", winJvmScript)

                task.doLast {
                    val unixScriptFile = task.scriptFile
                    val winScriptFile = task.batchScript

                    val unixScriptFileContent = unixScriptFile.readText(Charsets.UTF_8)
                    if (!unixScriptFileContent.contains(PATCHED_FILE_START_MARKER)) {
                        project.logger.debug("Patch {}", unixScriptFile)
                        val newUnixScriptFileContent = unixScriptFileContent.replace(UNIX_PATCH_PLACEHOLDER, unixJvmScript + UNIX_PATCH_PLACEHOLDER)
                        unixScriptFile.writeText(newUnixScriptFileContent, Charsets.UTF_8)
                        project.logger.debug("{} patched", unixScriptFile)
                    } else {
                        project.logger.debug("{} is up-to-date", unixScriptFile)
                    }
                    val winScriptFileContent = winScriptFile.readText(Charsets.UTF_8)
                    if (!winScriptFileContent.contains(PATCHED_FILE_START_MARKER)) {
                        project.logger.debug("Patch {}", winScriptFile)
                        val newWinScriptFileContent = winScriptFileContent.replace(WINDOWS_PATCH_PLACEHOLDER,
                                if (winScriptFileContent.contains("\r\n")) {
                                    winJvmScript.replace("\n", "\r\n")
                                } else {
                                    winJvmScript
                                } + WINDOWS_PATCH_PLACEHOLDER)
                        winScriptFile.writeText(newWinScriptFileContent, Charsets.UTF_8)
                        project.logger.debug("{} patched", winScriptFile)
                    } else {
                        project.logger.debug("{} is up-to-date", winScriptFile)
                    }
                }
            }
        }
    }
}
