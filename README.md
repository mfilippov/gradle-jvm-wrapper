# Gradle JVM Wrapper plugin
To use it, you need to add the plugin in your Gradle file.

Groovy edition:
```groovy
plugins {
  id "me.filippov.gradle.jvm.wrapper" version "0.9.2"
}
```
Kotlin edition:
```kotlin
plugins {
  id("me.filippov.gradle.jvm.wrapper") version "0.9.2"
}
```
After that you could call Gradle task `wrapper` to setup wrapper and update command-line scripts.
By default plugin using Amazon Coretto 11. You could configure for your JVM distribution:

Groovy edition:
```groovy
plugins {
  id "me.filippov.gradle.jvm.wrapper" version "0.9.2"
}

jvmWrapper {
    linuxJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-linux-x64.tar.gz"
    macJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-macosx-x64.tar.gz"
    windowsJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-windows-x64-jdk.zip"
}
```
Kotlin edition:
```kotlin
plugins {
  id("me.filippov.gradle.jvm.wrapper") version "0.9.2"
}

jvmWrapper {
    linuxJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-linux-x64.tar.gz"
    macJvmUrl = "https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-macosx-x64.tar.gz"
    windowsJvmUrl ="https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/amazon-corretto-8.232.09.1-windows-x86-jdk.zip"
}
```
