import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.gradle.plugin-publish") version "1.3.1"
    id("me.filippov.gradle.jvm.wrapper") version("0.16.0")
    kotlin("jvm") version "2.3.20"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

jvmWrapper {
    winJvmInstallDir = "build\\gradle-jvm"
    unixJvmInstallDir = "build/gradle-jvm"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        allWarningsAsErrors.set(true)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

version = "0.16.0"
group = "me.filippov.gradle.jvm.wrapper"

gradlePlugin {
    website.set("https://github.com/mfilippov/gradle-jvm-wrapper")
    vcsUrl.set("https://github.com/mfilippov/gradle-jvm-wrapper")
    plugins {
        create("jvmWrapperPlugin") {
            id = "me.filippov.gradle.jvm.wrapper"
            implementationClass = "me.filippov.gradle.jvm.wrapper.Plugin"
            displayName = "Embedded JVM in gradle wrapper plugin"
            description = "Allows using gradle wrapper with embedded Java"
            tags.set(listOf("wrapper", "jvm", "embedded"))
        }
    }
}
