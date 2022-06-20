package me.filippov.gradle.jvm.wrapper

open class PluginExtension {
    var winJvmInstallDir: String = "%LOCALAPPDATA%\\gradle-jvm"
    var unixJvmInstallDir: String = "${"$"}{HOME}/.local/share/gradle-jvm"
    var windowsX64JvmUrl = "https://download.oracle.com/java/17/archive/jdk-17.0.3.1_windows-x64_bin.zip"
    var linuxAarch64JvmUrl = "https://download.oracle.com/java/17/archive/jdk-17.0.3.1_linux-aarch64_bin.tar.gz"
    var linuxX64JvmUrl = "https://download.oracle.com/java/17/archive/jdk-17.0.3.1_linux-x64_bin.tar.gz"
    var macAarch64JvmUrl = "https://download.oracle.com/java/17/archive/jdk-17.0.3.1_macos-aarch64_bin.tar.gz"
    var macX64JvmUrl = "https://download.oracle.com/java/17/archive/jdk-17.0.3.1_macos-x64_bin.tar.gz"
}
