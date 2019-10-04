package me.filippov.gradle.jvm.wrapper

open class PluginExtension {
    var winJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-windows-x64.zip"
    var winJvmRelativeArchivePath = "jdk11.0.4_10"
    var linJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-macosx-x64.tar.gz"
    var linJvmRelativeArchivePath = ""
    var macJvmUrl = "https://repo.labs.intellij.net/cache/https/d3pxv6yz143wms.cloudfront.net/11.0.4.11.1/amazon-corretto-11.0.4.11.1-linux-x64.tar.gz"
    var macJvmRelativeArchivePath = "amazon-corretto-11.jdk/Contents/Home"
}