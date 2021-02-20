# Gradle JVM Wrapper plugin
To use it, you need to add the plugin to your Gradle file.

Groovy edition:
```groovy
plugins {
  id "me.filippov.gradle.jvm.wrapper" version "0.10.0"
}
```
Kotlin edition:
```kotlin
plugins {
  id("me.filippov.gradle.jvm.wrapper") version "0.10.0"
}
```
After that you should call `wrapper` Gradle task to setup a wrapper and update the command-line scripts.
By default the plugin uses Amazon Coretto 11. You can configure it for your JVM distribution:

Groovy edition:
```groovy
plugins {
  id "me.filippov.gradle.jvm.wrapper" version "0.10.0"
}

jvmWrapper {
    linuxJvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz"
    macJvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-macos-jdk.tar.gz"
    windowsJvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-windows-jdk.zip"
}
```
Kotlin edition:
```kotlin
plugins {
  id("me.filippov.gradle.jvm.wrapper") version "0.10.0"
}

jvmWrapper {
    linuxJvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz"
    macJvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-macos-jdk.tar.gz"
    windowsJvmUrl = "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-windows-jdk.zip"
}
```
