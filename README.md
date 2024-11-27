# Gradle JVM Wrapper plugin
To use it, you need to add the plugin to your Gradle file.

Groovy edition:
```groovy
plugins {
    id "me.filippov.gradle.jvm.wrapper" version "0.14.0"
}
```
Kotlin edition:
```kotlin
plugins {
    id("me.filippov.gradle.jvm.wrapper") version "0.14.0"
}
```
After that you should call `wrapper` Gradle task to setup a wrapper and update the command-line scripts.
By default the plugin uses Amazon Coretto 11. You can configure it for your JVM distribution:

Groovy edition:
```groovy
plugins {
    id "me.filippov.gradle.jvm.wrapper" version "0.14.0"
}

jvmWrapper {
    unixJvmInstallDir = "${"$"}{HOME}/my-custom-path/gradle-jvm"
    winJvmInstallDir = "%LOCALAPPDATA%\\gradle-jvm"
    linuxAarch64JvmUrl = "https://corretto.aws/downloads/resources/11.0.9.12.1/amazon-corretto-11.0.9.12.1-linux-aarch64.tar.gz"
    linuxX64JvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz"
    macAarch64JvmUrl = "https://cdn.azul.com/zulu/bin/zulu11.45.27-ca-jdk11.0.10-macosx_aarch64.tar.gz"
    macX64JvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-macos-jdk.tar.gz"
    windowsX64JvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-windows-jdk.zip"
}
```
Kotlin edition:
```kotlin
plugins {
    id("me.filippov.gradle.jvm.wrapper") version "0.14.0"
}

jvmWrapper {
    unixJvmInstallDir = "${"$"}{HOME}/my-custom-path/gradle-jvm"
    winJvmInstallDir = "%LOCALAPPDATA%\\gradle-jvm"
    linuxAarch64JvmUrl = "https://corretto.aws/downloads/resources/11.0.9.12.1/amazon-corretto-11.0.9.12.1-linux-aarch64.tar.gz"
    linuxX64JvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz"
    macAarch64JvmUrl = "https://cdn.azul.com/zulu/bin/zulu11.45.27-ca-jdk11.0.10-macosx_aarch64.tar.gz"
    macX64JvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-macos-jdk.tar.gz"
    windowsX64JvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-windows-jdk.zip"
}
```
