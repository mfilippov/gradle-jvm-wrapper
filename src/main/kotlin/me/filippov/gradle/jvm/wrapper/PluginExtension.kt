package me.filippov.gradle.jvm.wrapper

open class PluginExtension {
    var buildDir = "build"
    var windowsJvmUrl = "https://corretto.aws/downloads/resources/11.0.10.9.1/amazon-corretto-11.0.10.9.1-windows-x64-jdk.zip"
    var linuxJvmUrl = "https://corretto.aws/downloads/resources/11.0.10.9.1/amazon-corretto-11.0.10.9.1-linux-x64.tar.gz"
    var macJvmUrl = "https://corretto.aws/downloads/resources/11.0.10.9.1/amazon-corretto-11.0.10.9.1-macosx-x64.tar.gz"
}
