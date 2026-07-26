package me.filippov.gradle.jvm.wrapper

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PatchScriptTest {
    private val placeHolder = "# Determine the Java command to use to start the JVM."
    private val jvmScript = "# GRADLE JVM WRAPPER START MARKER\nbootstrap\n# GRADLE JVM WRAPPER END MARKER\n"

    @Test
    fun insertsTheJvmBlockBeforeThePlaceholder() {
        val content = "#!/bin/sh\n\n$placeHolder\nrun\n"
        val patched = Plugin.patchedScriptContent("gradlew", content, jvmScript, placeHolder)!!
        patched.shouldContain("bootstrap\n# GRADLE JVM WRAPPER END MARKER\n$placeHolder")
        patched.shouldContain("run")
    }

    @Test
    fun keepsCrlfLineEndings() {
        val content = "@echo off\r\n$placeHolder\r\nrun\r\n"
        val patched = Plugin.patchedScriptContent("gradlew.bat", content, jvmScript, placeHolder)!!
        patched.shouldContain("bootstrap\r\n# GRADLE JVM WRAPPER END MARKER\r\n$placeHolder")
        patched.shouldNotContain("bootstrap\n#")
    }

    @Test
    fun skipsAnAlreadyPatchedScript() {
        val content = "#!/bin/sh\n$jvmScript$placeHolder\nrun\n"
        val patched = Plugin.patchedScriptContent("gradlew", content, jvmScript, placeHolder)
        (patched == null).shouldBeTrue("An already patched script must not be patched again")
    }

    @Test
    fun failsWithAClearErrorWhenThePlaceholderIsMissing() {
        val content = "#!/bin/sh\nrun\n"
        val exception = assertThrows(GradleException::class.java) {
            Plugin.patchedScriptContent("gradlew", content, jvmScript, placeHolder)
        }
        exception.message!!.shouldContain("gradlew")
        exception.message!!.shouldContain(placeHolder)
    }
}
