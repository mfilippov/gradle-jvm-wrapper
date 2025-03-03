package me.filippov.gradle.jvm.wrapper

open class PluginExtension {
    var winJvmInstallDir: String = "%LOCALAPPDATA%\\gradle-jvm"
    var unixJvmInstallDir: String = "${"$"}{HOME}/.local/share/gradle-jvm"
    var windowsX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_windows-x64_bin.zip"
    var linuxAarch64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_linux-aarch64_bin.tar.gz"
    var linuxX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_linux-aarch64_bin.tar.gz"
    var macAarch64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_macos-aarch64_bin.tar.gz"
    var macX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_macos-x64_bin.tar.gz"
}
