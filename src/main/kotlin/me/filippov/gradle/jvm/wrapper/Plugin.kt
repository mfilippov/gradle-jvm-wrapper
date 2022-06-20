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
                    BUILD_DIR="${cfg.unixJvmInstallDir}"
                    JVM_ARCH=${'$'}(uname -m)
                    JVM_TEMP_FILE=${"$"}BUILD_DIR/gradle-jvm-temp.tar.gz
                    if [ "${"$"}darwin" = "true" ]; then
                        case ${"$"}JVM_ARCH in
                        x86_64)
                            JVM_URL=${cfg.macX64JvmUrl}
                            JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.macX64JvmUrl)}
                            ;;
                        arm64)
                            JVM_URL=${cfg.macAarch64JvmUrl}
                            JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.macAarch64JvmUrl)}
                            ;;
                        *) 
                            die "Unknown architecture ${"$"}JVM_ARCH"
                            ;;
                        esac
                    elif [ "${"$"}cygwin" = "true" ] || [ "${"$"}msys" = "true" ]; then
                        JVM_URL=${cfg.windowsX64JvmUrl}
                        JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.windowsX64JvmUrl)}
                    else
                        JVM_ARCH=${'$'}(linux${'$'}(getconf LONG_BIT) uname -m)
                         case ${"$"}JVM_ARCH in
                            x86_64)
                                JVM_URL=${cfg.linuxX64JvmUrl}
                                JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.linuxX64JvmUrl)}
                                ;;
                            aarch64)
                                JVM_URL=${cfg.linuxAarch64JvmUrl}
                                JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.linuxAarch64JvmUrl)}
                                ;;
                            *) 
                                die "Unknown architecture ${"$"}JVM_ARCH"
                                ;;
                            esac
                    fi
            
                    set -e
            
                    if [ -e "${"$"}JVM_TARGET_DIR/.flag" ] && [ -n "${'$'}(ls "${"$"}JVM_TARGET_DIR")" ] && [ "x${'$'}(cat "${"$"}JVM_TARGET_DIR/.flag")" = "x${"$"}{JVM_URL}" ]; then
                        # Everything is up-to-date in ${"$"}JVM_TARGET_DIR, do nothing
                        true
                    else
                      echo "Downloading ${"$"}JVM_URL to ${"$"}JVM_TEMP_FILE"
            
                      rm -f "${"$"}JVM_TEMP_FILE"
                      mkdir -p "${"$"}BUILD_DIR"
                      if command -v curl >/dev/null 2>&1; then
                          if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
                          # shellcheck disable=SC2086
                          curl ${"$"}CURL_PROGRESS -L --output "${"$"}{JVM_TEMP_FILE}" "${"$"}JVM_URL" 2>&1
                      elif command -v wget >/dev/null 2>&1; then
                          if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
                          wget ${"$"}WGET_PROGRESS -O "${"$"}{JVM_TEMP_FILE}" "${"$"}JVM_URL" 2>&1
                      else
                          die "ERROR: Please install wget or curl"
                      fi
            
                      echo "Extracting ${"$"}JVM_TEMP_FILE to ${"$"}JVM_TARGET_DIR"
                      rm -rf "${"$"}JVM_TARGET_DIR"
                      mkdir -p "${"$"}JVM_TARGET_DIR"
            
                      if [ "${"$"}cygwin" = "true" ] || [ "${"$"}msys" = "true" ]; then
                          unzip "${"$"}JVM_TEMP_FILE" -d "${"$"}JVM_TARGET_DIR"
                      else
                          tar -x -f "${"$"}JVM_TEMP_FILE" -C "${"$"}JVM_TARGET_DIR"
                      fi
                      rm -f "${"$"}JVM_TEMP_FILE"
            
                      echo "${"$"}JVM_URL" >"${"$"}JVM_TARGET_DIR/.flag"
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
                    set JVM_TARGET_DIR=%BUILD_DIR%\${getJvmDirName(cfg.windowsX64JvmUrl)}\

                    set JVM_TEMP_FILE=gradle-jvm.zip
                    set JVM_URL=${cfg.windowsX64JvmUrl.replace("%", "%%")}

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
                    "%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; ${"$"}ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\%JVM_TEMP_FILE%', '.');"
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
