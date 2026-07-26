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
The downloaded JVM archive can optionally be verified against an expected SHA-256 checksum.
Add the checksum for the platforms you want to verify (an empty value disables the check):
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
