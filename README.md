# Gradle JVM Wrapper plugin
To use it, you need to add the plugin to your Gradle file.

Groovy edition:
```groovy
plugins {
    id "me.filippov.gradle.jvm.wrapper" version "0.17.0"
}
```
Kotlin edition:
```kotlin
plugins {
    id("me.filippov.gradle.jvm.wrapper") version "0.17.0"
}
```
After that, call the `wrapper` Gradle task to set up a wrapper and update the command-line scripts.
The plugin must be applied to the root project (the `wrapper` task only exists there).

Note: with the Kotlin DSL, the `=` assignment syntax in `jvmWrapper { }` requires Gradle 8.2 or newer;
on older Gradle versions use `.set(...)` instead.
Configured URLs and install dirs must be printable ASCII (they are embedded into batch and
shell scripts); for localized user-profile paths keep the `%LOCALAPPDATA%` / `${HOME}`
environment-variable indirection the defaults use — it expands on each user's machine.
A few characters the generated scripts cannot quote away are rejected at configuration
time: URLs must not contain spaces, quotes, backticks, backslashes or `$`;
`winJvmInstallDir` must not contain `" & ^ < > | * ?`; `unixJvmInstallDir` must not
contain `"`, backticks, `$(`, or a backslash at the end, before another backslash or
before `$`. A URL whose path ends in `.zip` (any case) is treated as a zip archive,
anything else — including suffix-less "latest" redirector links — as a tar.gz.

On an Apple Silicon Mac the wrapper detects a shell running under Rosetta 2 and still
downloads the native arm64 JDK; set `keepRosetta2 = true` to keep the x64 JDK matching
the translated shell instead.
By default the plugin uses Oracle JDK 25 (Microsoft OpenJDK on Windows ARM, where Oracle
publishes no build). You can configure it for your JVM distribution:

Groovy edition:
```groovy
plugins {
    id "me.filippov.gradle.jvm.wrapper" version "0.17.0"
}

jvmWrapper {
    unixJvmInstallDir = '${HOME}/my-custom-path/gradle-jvm'
    winJvmInstallDir = '%LOCALAPPDATA%\\gradle-jvm'
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
    id("me.filippov.gradle.jvm.wrapper") version "0.17.0"
}

jvmWrapper {
    unixJvmInstallDir = "\${HOME}/my-custom-path/gradle-jvm"
    winJvmInstallDir = "%LOCALAPPDATA%\\gradle-jvm"
    linuxAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-aarch64.tar.gz"
    linuxX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-x64.tar.gz"
    macAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-aarch64.tar.gz"
    macX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz"
    windowsAarch64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-aarch64.zip"
    windowsX64JvmUrl = "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-x64.zip"
}
```

## Outdated wrapper detection
Changes in the `jvmWrapper { }` block only take effect after the `wrapper` task regenerates
the command-line scripts. While `gradlew`/`gradlew.bat` are out of sync with the configuration,
the plugin prints a warning whenever a task-executing build is configured (with the
configuration cache enabled, the warning appears when an entry is stored; cache hits
stay silent).
To fail the build instead of warning, enable the strict mode (any build that runs
the `wrapper` task itself stays allowed):
```kotlin
jvmWrapper {
    failOnOutdatedWrapper = true
}
```
The check runs when a task graph is built, so two kinds of builds never see it (nor can the
strict mode break them): IDE sync (Tooling API model requests) and running Gradle from a
composite root that includes this build without requesting its tasks.

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
