package me.filippov.gradle.jvm.wrapper

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.wrapper.Wrapper
import java.security.MessageDigest

@Suppress("unused")
class Plugin : Plugin<Project> {
    companion object {
        private const val windowsJvmFile = "jvm-windows-x64.zip"
        private const val macJvmFile = "jvm-macosx-x64.tar.gz"
        private const val linuxJvmFile = "jvm-linux-x64.tar.gz"
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
        return digest.fold("", { str, it -> str + "%02x".format(it) })
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
                    BUILD_DIR=${"$"}APP_HOME/build
            
                    if [ "${"$"}darwin" = "true" ]; then
                        JVM_TEMP_FILE=${"$"}BUILD_DIR/$macJvmFile
                        JVM_URL=${cfg.macJvmUrl}
                        JVM_TARGET_DIR=${"$"}BUILD_DIR/gradle-jvm/${getJvmDirName(cfg.macJvmUrl)}
                    elif [ "${"$"}cygwin" = "true" ] || [ "${"$"}msys" = "true" ]; then
                        JVM_TEMP_FILE=${"$"}BUILD_DIR/$windowsJvmFile
                        JVM_URL=https://d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-windows-x64.zip
                        JVM_TARGET_DIR=${"$"}BUILD_DIR/${getJvmDirName(cfg.windowsJvmUrl)}
                    else
                        JVM_TEMP_FILE=${"$"}BUILD_DIR/$linuxJvmFile
                        JVM_URL=${cfg.linuxJvmUrl}
                        JVM_TARGET_DIR=${"$"}BUILD_DIR/gradle-jvm/${getJvmDirName(cfg.linuxJvmUrl)}
                    fi
            
                    set -e
            
                    if [ -e "${"$"}JVM_TARGET_DIR/.flag" ] && [ -n "${'$'}(ls "${"$"}JVM_TARGET_DIR")" ] && [ "x${'$'}(cat "${"$"}JVM_TARGET_DIR/.flag")" = "x${"$"}{JVM_URL}" ]; then
                        # Everything is up-to-date in ${"$"}JVM_TARGET_DIR, do nothing
                        true
                    else
                      warn "Downloading ${"$"}JVM_URL to ${"$"}JVM_TEMP_FILE"
            
                      rm -f "${"$"}JVM_TEMP_FILE"
                      mkdir -p "${"$"}BUILD_DIR"
                      if command -v curl >/dev/null 2>&1; then
                          if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
                          # shellcheck disable=SC2086
                          curl ${"$"}CURL_PROGRESS --output "${"$"}{JVM_TEMP_FILE}" "${"$"}JVM_URL"
                      elif command -v wget >/dev/null 2>&1; then
                          if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
                          wget ${"$"}WGET_PROGRESS -O "${"$"}{JVM_TEMP_FILE}" "${"$"}JVM_URL"
                      else
                          die "ERROR: Please install wget or curl"
                      fi
            
                      warn "Extracting ${"$"}JVM_TEMP_FILE to ${"$"}JVM_TARGET_DIR"
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
        
                    set BUILD_DIR=%APP_HOME%build\
                    set JVM_TARGET_DIR=%BUILD_DIR%gradle-jvm\${getJvmDirName(cfg.windowsJvmUrl)}\
                    
                    set JVM_TEMP_FILE=$windowsJvmFile
                    set JVM_URL=${cfg.windowsJvmUrl.replace("%", "%%")}
                    
                    set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe
                    
                    if not exist "%JVM_TARGET_DIR%" MD "%JVM_TARGET_DIR%"
                    
                    if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm
                    
                    set /p CURRENT_FLAG=<"%JVM_TARGET_DIR%.flag"
                    if "%CURRENT_FLAG%" == "%JVM_URL%" goto continueWithJvm
                    
                    :downloadAndExtractJvm
                    
                    CD "%BUILD_DIR%"
                    if errorlevel 1 goto fail
                    
                    echo Downloading %JVM_URL% to %BUILD_DIR%%JVM_TEMP_FILE%
                    if exist "%JVM_TEMP_FILE%" DEL /F "%JVM_TEMP_FILE%"
                    "%POWERSHELL%" -nologo -noprofile -Command "Set-StrictMode -Version 3.0; ${"$"}ErrorActionPreference = \"Stop\"; (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%JVM_TEMP_FILE%')"
                    if errorlevel 1 goto fail
                    
                    RMDIR /S /Q "%JVM_TARGET_DIR%"
                    if errorlevel 1 goto fail
                    
                    MKDIR "%JVM_TARGET_DIR%"
                    if errorlevel 1 goto fail
                    
                    CD "%JVM_TARGET_DIR%"
                    if errorlevel 1 goto fail
                    
                    echo Extracting %BUILD_DIR%%JVM_TEMP_FILE% to %JVM_TARGET_DIR%
                    "%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; ${"$"}ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\..\\%JVM_TEMP_FILE%', '.');"
                    if errorlevel 1 goto fail
                    
                    DEL /F "..\..\%JVM_TEMP_FILE%"
                    if errorlevel 1 goto fail
                    
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
