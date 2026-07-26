package me.filippov.gradle.jvm.wrapper

open class PluginExtension {
    companion object {
        // Vendor-published SHA-256 checksums of the default JVM archives below.
        // Used automatically when the corresponding URL is left at its default;
        // an explicitly configured *JvmSha256 value always wins.
        internal val defaultJvmSha256 = mapOf(
            "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-aarch64.zip"
                    to "e0d9380cf3d0b5efc675664fa0db22cc9eb5d77c4fd2a132f4b58df0608593cf",
            "https://download.oracle.com/java/25/archive/jdk-25.0.2_windows-x64_bin.zip"
                    to "56fbc625835eaa4e96942e9d8df38ffdf6009e0062620aaf9a8b647a5cd8ec7a",
            "https://download.oracle.com/java/25/archive/jdk-25.0.2_linux-aarch64_bin.tar.gz"
                    to "1aaf2d46506ecdf15569d5d3f0c2295a7c18795ec7a6ee030cf19406daccd0dc",
            "https://download.oracle.com/java/25/archive/jdk-25.0.2_linux-x64_bin.tar.gz"
                    to "505fdcb1f172b4aad23415f0584912cff90b7d902adc5f1593894b4a8cbf7c39",
            "https://download.oracle.com/java/25/archive/jdk-25.0.2_macos-aarch64_bin.tar.gz"
                    to "045d17ca8a00f77d91f43a12c8a023598837acc576d0701193ccf560f62ef4b4",
            "https://download.oracle.com/java/25/archive/jdk-25.0.2_macos-x64_bin.tar.gz"
                    to "e4f4a4b10883c1d3a0a1cefa9cd3e4ac1ed8b8e053d12ab34adc39a6875b984d",
        )
    }

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
