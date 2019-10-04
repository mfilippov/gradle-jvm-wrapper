open class JvmWrapperPluginExtension {
    var winJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-windows-x64.zip"
    var winJvmRelativeArchivePath = ""
    var linJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-macosx-x64.tar.gz"
    var linJvmRelativeArchivePath = ""
    var macJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-linux-x64.tar.gz"
    var macJvmRelativeArchivePath = "amazon-corretto-11.jdk/Contents/Home"
}

class JvmWrapperPlugin : Plugin<Project> {
    companion object {
        private const val winJvmFile = "jvm-windows-x64.zip"
        private const val macJvmFile = "jvm-macosx-x64.tar.gz"
        private const val linJvmFile = "jvm-linux-x64.tar.gz"
        private const val patchedFileMarker = "GRADLE JVM PLUGIN PATH MARKER"
        private const val unixPatchPlaceHolder = "# Determine the Java command to use to start the JVM."
        private const val winPatchPlaceHolder = "@rem Find java.exe"
        val wrapperTaskName = "wrapper"
        val updateWrapperFilesTaskName = "update-wrapper-files"
        val winWrapperFileName = "gradlew"
        val unixWrapperFileName = "gradlew.bat"
    }

    override fun apply(project: Project) {
        val cfg = project.extensions.create<JvmWrapperPluginExtension>("jvm-wrapper")
        val unixJvmScript = """
            # $patchedFileMarker
            BUILD_DIR=${"$"}APP_HOME/build
            JVM_TARGET_DIR=${"$"}BUILD_DIR/gradle-jvm
    
            if [ "${"$"}darwin" = "true" ]; then
                JVM_TEMP_FILE=${"$"}BUILD_DIR/$macJvmFile
                JVM_URL=${cfg.macJvmUrl}
                JVM_ARCHIVE_RELATIVE_PATH=${cfg.macJvmRelativeArchivePath}
            else
                JVM_TEMP_FILE=${"$"}BUILD_DIR/$linJvmFile
                JVM_URL=${cfg.linJvmUrl}
                JVM_ARCHIVE_RELATIVE_PATH=${cfg.linJvmRelativeArchivePath}
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
    
              tar -x -f "${"$"}JVM_TEMP_FILE" -C "${"$"}JVM_TARGET_DIR"
              rm -f "${"$"}JVM_TEMP_FILE"
    
              echo "${"$"}JVM_URL" >"${"$"}JVM_TARGET_DIR/.flag"
            fi
    
            JAVA_HOME=${"$"}JVM_TARGET_DIR/${"$"}JVM_ARCHIVE_RELATIVE_PATH
    
            set +e${"\n\n"}
        """.trimIndent()
        val winJvmScript = """
            @rem $patchedFileMarker
            set JAVA_HOME=%APP_HOME%build\gradle-jvm\jdk11.0.4_10

            setlocal

            set BUILD_DIR=%APP_HOME%build\
            set JVM_TARGET_DIR=%BUILD_DIR%gradle-jvm\
            
            set JVM_TEMP_FILE=amazon-corretto-11.0.4.11.1-windows-x64.zip
            set JVM_URL=https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-windows-x64.zip
            
            set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe
            
            if not exist "%JVM_TARGET_DIR%" MD "%JVM_TARGET_DIR%"
            
            if not exist "%JAVA_HOME%\bin\java.exe" goto downloadAndExtractJvm
            if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm
            
            echo %JVM_URL% >"%JVM_TARGET_DIR%.flag.tmp"
            if errorlevel 1 goto fail
            
            FC /B "%JVM_TARGET_DIR%.flag" "%JVM_TARGET_DIR%.flag.tmp" >nul
            if "%ERRORLEVEL%" == "0" goto continueWithJvm
            
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
            "%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; ${"$"}ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\%JVM_TEMP_FILE%', '.');"
            if errorlevel 1 goto fail
            
            DEL /F "..\%JVM_TEMP_FILE%"
            if errorlevel 1 goto fail
            
            echo %JVM_URL% >"%JVM_TARGET_DIR%.flag"
            if errorlevel 1 goto fail
            
            :continueWithJvm
            
            if exist "%JVM_TARGET_DIR%.flag.tmp" (
              DEL /F "%JVM_TARGET_DIR%.flag.tmp"
              if errorlevel 1 goto fail
            )
            
            endlocal${"\n\n"}""".trimIndent()

        project.task(updateWrapperFilesTaskName) {
            val unixScriptFile = project.file(winWrapperFileName)
            val winScriptFile = project.file(unixWrapperFileName)
            doLast {
                val unixScriptFileContent = unixScriptFile.readText(Charsets.UTF_8)
                if (!unixScriptFileContent.contains(patchedFileMarker)) {
                    project.logger.debug("Patch $unixScriptFile")
                    val newUnixScriptFileContent = unixScriptFileContent.replace(unixPatchPlaceHolder, unixJvmScript + unixPatchPlaceHolder)
                    unixScriptFile.writeText(newUnixScriptFileContent, Charsets.UTF_8)
                    project.logger.debug("$unixScriptFile patched")
                } else {
                    project.logger.debug("$unixScriptFile is up-to-date")
                }
                val winScriptFileContent = winScriptFile.readText(Charsets.UTF_8)
                if (!winScriptFileContent.contains(patchedFileMarker)) {
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
        project.tasks.findByName(wrapperTaskName)?.finalizedBy(updateWrapperFilesTaskName)
    }
}

apply<JvmWrapperPlugin>()

var winJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-windows-x64.zip"
var linJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-linux-x64.tar.gz"
var macJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-macosx-x64.tar.gz"