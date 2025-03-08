package me.filippov.gradle.jvm.wrapper

open class PluginExtension {
    var winJvmInstallDir: String = "%LOCALAPPDATA%\\gradle-jvm"
    var unixJvmInstallDir: String = "${"$"}{HOME}/.local/share/gradle-jvm"
    // https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-21
    var windowsAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-21.0.6-windows-aarch64.zip"
    // https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html
    var windowsX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.5_windows-x64_bin.zip"
    var linuxAarch64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.5_linux-aarch64_bin.tar.gz"
    var linuxX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.5_linux-x64_bin.tar.gz"
    var macAarch64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.5_macos-aarch64_bin.tar.gz"
    var macX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.5_macos-x64_bin.tar.gz"
}
