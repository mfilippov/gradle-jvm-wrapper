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
    }
    private val isWindows = System.getProperty("os.name").startsWith("windows")

    override fun apply(project: Project) {
        val cfg = project.extensions.create<JvmWrapperPluginExtension>("gradle-jvm-wrapper")
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

        project.task("patch-command-line") {
            doLast {
                val unixScriptFile = project.file("gradlew")
                val unixScriptFileContent = unixScriptFile.readText(Charsets.UTF_8)
                if (!unixScriptFileContent.contains(patchedFileMarker)) {
                    val newUnixScriptFileContent = unixScriptFileContent.replace(unixPatchPlaceHolder, unixJvmScript + unixPatchPlaceHolder)
                    unixScriptFile.writeText(newUnixScriptFileContent, Charsets.UTF_8)
                }
            }
        }
        project.tasks.findByName("wrapper")?.finalizedBy("patch-command-line")
    }
}

apply<JvmWrapperPlugin>()

var winJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-windows-x64.zip"
var linJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-linux-x64.tar.gz"
var macJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-macosx-x64.tar.gz"