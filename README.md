# Gradle JVM Wrapper plugin
To use it, you need to add the plugin to your Gradle file.

Groovy edition:
```groovy
plugins {
    id "me.filippov.gradle.jvm.wrapper" version "0.16.0"
}
```
Kotlin edition:
```kotlin
plugins {
    id("me.filippov.gradle.jvm.wrapper") version "0.16.0"
}
```
After that you should call `wrapper` Gradle task to setup a wrapper and update the command-line scripts.

Note: with the Kotlin DSL, the `=` assignment syntax in `jvmWrapper { }` requires Gradle 8.2 or newer;
on older Gradle versions use `.set(...)` instead.
By default the plugin uses Oracle JDK 25. You can configure it for your JVM distribution:

Groovy edition:
```groovy
plugins {
    id "me.filippov.gradle.jvm.wrapper" version "0.16.0"
}

jvmWrapper {
    unixJvmInstallDir = "${"$"}{HOME}/my-custom-path/gradle-jvm"
    winJvmInstallDir = "%LOCALAPPDATA%\\gradle-jvm"
    linuxAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-aarch64.tar.gz"
    linuxX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-x64.tar.gz"
    macAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-aarch64.tar.gz"
    macX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz"
    windowsAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-aarch64.zip"
    windowsX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-x64.zip"
}
```
Kotlin edition:
```kotlin
plugins {
    id("me.filippov.gradle.jvm.wrapper") version "0.16.0"
}

jvmWrapper {
    unixJvmInstallDir = "${"$"}{HOME}/my-custom-path/gradle-jvm"
    winJvmInstallDir = "%LOCALAPPDATA%\\gradle-jvm"
    linuxAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-aarch64.tar.gz"
    linuxX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-x64.tar.gz"
    macAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-aarch64.tar.gz"
    macX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz"
    windowsAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-aarch64.zip"
    windowsX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-x64.zip"
}
```

## SHA-256 validation
The downloaded JVM archive is verified against an expected SHA-256 checksum.
The default JVM URLs are validated out of the box: the vendor-published checksums for them
are built into the plugin. For a custom JVM URL, add the matching checksum yourself
(with no checksum configured, a custom URL is downloaded without validation):
```kotlin
jvmWrapper {
    linuxAarch64JvmSha256 = "..."
    linuxX64JvmSha256 = "..."
    macAarch64JvmSha256 = "..."
    macX64JvmSha256 = "..."
    windowsAarch64JvmSha256 = "..."
    windowsX64JvmSha256 = "..."
}
```
If the checksum of the downloaded archive does not match, the build fails with a `SHA-256 mismatch` error.

Take the expected checksums from your JDK vendor: Oracle publishes them next to each archive
(append `.sha256` to the download URL), Microsoft lists them on the
[download page](https://learn.microsoft.com/en-us/java/openjdk/download).
