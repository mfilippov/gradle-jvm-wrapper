package me.filippov.gradle.jvm.wrapper

open class PluginExtension {
    var winJvmInstallDir: String = "%LOCALAPPDATA%\\gradle-jvm"
    var keepRosetta2: Boolean = false
    var unixJvmInstallDir: String = "${"$"}{HOME}/.local/share/gradle-jvm"
    // https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-25
    var windowsAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-aarch64.zip"
    // https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html
    var windowsX64JvmUrl = "https://download.oracle.com/java/25/archive/jdk-25.0.2_windows-x64_bin.zip"
    var linuxAarch64JvmUrl = "https://download.oracle.com/java/25/archive/jdk-25.0.2_linux-aarch64_bin.tar.gz"
    var linuxX64JvmUrl = "https://download.oracle.com/java/25/archive/jdk-25.0.2_linux-x64_bin.tar.gz"
    var macAarch64JvmUrl = "https://download.oracle.com/java/25/archive/jdk-25.0.2_macos-aarch64_bin.tar.gz"
    var macX64JvmUrl = "https://download.oracle.com/java/25/archive/jdk-25.0.2_macos-x64_bin.tar.gz"
    // Optional SHA-256 checksums of the archives above (empty value disables the check)
    var windowsAarch64JvmSha256 = ""
    var windowsX64JvmSha256 = ""
    var linuxAarch64JvmSha256 = ""
    var linuxX64JvmSha256 = ""
    var macAarch64JvmSha256 = ""
    var macX64JvmSha256 = ""
}
