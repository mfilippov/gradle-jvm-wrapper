package me.filippov.gradle.jvm.wrapper

import org.gradle.api.provider.Property

abstract class PluginExtension {
    companion object {
        // https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-25
        const val DEFAULT_WINDOWS_AARCH64_JVM_URL = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-aarch64.zip"
        // https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html
        const val DEFAULT_WINDOWS_X64_JVM_URL = "https://download.oracle.com/java/25/archive/jdk-25.0.2_windows-x64_bin.zip"
        const val DEFAULT_LINUX_AARCH64_JVM_URL = "https://download.oracle.com/java/25/archive/jdk-25.0.2_linux-aarch64_bin.tar.gz"
        const val DEFAULT_LINUX_X64_JVM_URL = "https://download.oracle.com/java/25/archive/jdk-25.0.2_linux-x64_bin.tar.gz"
        const val DEFAULT_MAC_AARCH64_JVM_URL = "https://download.oracle.com/java/25/archive/jdk-25.0.2_macos-aarch64_bin.tar.gz"
        const val DEFAULT_MAC_X64_JVM_URL = "https://download.oracle.com/java/25/archive/jdk-25.0.2_macos-x64_bin.tar.gz"

        // Vendor-published SHA-256 checksums of the default JVM archives above.
        // Used automatically when the corresponding URL is left at its default;
        // an explicitly configured *JvmSha256 value always wins.
        internal val defaultJvmSha256 = mapOf(
            DEFAULT_WINDOWS_AARCH64_JVM_URL to "e0d9380cf3d0b5efc675664fa0db22cc9eb5d77c4fd2a132f4b58df0608593cf",
            DEFAULT_WINDOWS_X64_JVM_URL to "56fbc625835eaa4e96942e9d8df38ffdf6009e0062620aaf9a8b647a5cd8ec7a",
            DEFAULT_LINUX_AARCH64_JVM_URL to "1aaf2d46506ecdf15569d5d3f0c2295a7c18795ec7a6ee030cf19406daccd0dc",
            DEFAULT_LINUX_X64_JVM_URL to "505fdcb1f172b4aad23415f0584912cff90b7d902adc5f1593894b4a8cbf7c39",
            DEFAULT_MAC_AARCH64_JVM_URL to "045d17ca8a00f77d91f43a12c8a023598837acc576d0701193ccf560f62ef4b4",
            DEFAULT_MAC_X64_JVM_URL to "e4f4a4b10883c1d3a0a1cefa9cd3e4ac1ed8b8e053d12ab34adc39a6875b984d",
        )
    }

    abstract val winJvmInstallDir: Property<String>
    abstract val keepRosetta2: Property<Boolean>
    abstract val unixJvmInstallDir: Property<String>
    abstract val windowsAarch64JvmUrl: Property<String>
    abstract val windowsX64JvmUrl: Property<String>
    abstract val linuxAarch64JvmUrl: Property<String>
    abstract val linuxX64JvmUrl: Property<String>
    abstract val macAarch64JvmUrl: Property<String>
    abstract val macX64JvmUrl: Property<String>
    // Optional SHA-256 checksums of the archives above (an empty value falls back to the
    // built-in vendor checksum for a default URL and disables the check for a custom one)
    abstract val windowsAarch64JvmSha256: Property<String>
    abstract val windowsX64JvmSha256: Property<String>
    abstract val linuxAarch64JvmSha256: Property<String>
    abstract val linuxX64JvmSha256: Property<String>
    abstract val macAarch64JvmSha256: Property<String>
    abstract val macX64JvmSha256: Property<String>

    init {
        winJvmInstallDir.convention("%LOCALAPPDATA%\\gradle-jvm")
        keepRosetta2.convention(false)
        unixJvmInstallDir.convention("${"$"}{HOME}/.local/share/gradle-jvm")
        windowsAarch64JvmUrl.convention(DEFAULT_WINDOWS_AARCH64_JVM_URL)
        windowsX64JvmUrl.convention(DEFAULT_WINDOWS_X64_JVM_URL)
        linuxAarch64JvmUrl.convention(DEFAULT_LINUX_AARCH64_JVM_URL)
        linuxX64JvmUrl.convention(DEFAULT_LINUX_X64_JVM_URL)
        macAarch64JvmUrl.convention(DEFAULT_MAC_AARCH64_JVM_URL)
        macX64JvmUrl.convention(DEFAULT_MAC_X64_JVM_URL)
        windowsAarch64JvmSha256.convention("")
        windowsX64JvmSha256.convention("")
        linuxAarch64JvmSha256.convention("")
        linuxX64JvmSha256.convention("")
        macAarch64JvmSha256.convention("")
        macX64JvmSha256.convention("")
    }
}
