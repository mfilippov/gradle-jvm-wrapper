# Gradle JVM Wrapper plugin
To use it, you need to add the plugin in your Gradle file.

For Groovy edition:
```groovy
plugins {
  id "me.filippov.gradle.jvm.wrapper" version "0.8"
}
```
For Kotlin edidtion:
```kotlin
plugins {
  id("me.filippov.gradle.jvm.wrapper") version "0.8"
}
```
After that you could call Gradle task `jvm-wrapper` instead of `wrapper` to setup wrapper and update command-line scripts.