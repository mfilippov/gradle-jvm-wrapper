package me.filippov.gradle.jvm.wrapper

import org.junit.Assert
import java.util.*

val isWindows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows")
val isMac = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("mac")
val isLinux = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("linux")

val wrapperScriptFileName = when {
    isWindows -> "gradlew.bat"
    isLinux -> "gradlew"
    isMac -> "gradlew"
    else -> error("Unknown OS")
}

fun <T> T.shouldBe(expectedValue: T, message: String? = null) {
    Assert.assertEquals(message, expectedValue, this)
}

fun Boolean.shouldBeTrue(message: String? = null) {
    Assert.assertTrue(message, this)
}
