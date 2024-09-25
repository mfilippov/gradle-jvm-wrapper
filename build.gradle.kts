import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("com.gradle.plugin-publish") version "1.3.0"
    id("me.filippov.gradle.jvm.wrapper") version("0.14.0")
    `java-gradle-plugin`
    kotlin("jvm") version "2.0.20"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

jvmWrapper {
    winJvmInstallDir = "build\\gradle-jvm"
    unixJvmInstallDir = "build/gradle-jvm"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        allWarningsAsErrors.set(true)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

version = "0.14.0"
group = "me.filippov.gradle.jvm.wrapper"

gradlePlugin {
    website.set("https://github.com/mfilippov/gradle-jvm-wrapper")
    vcsUrl.set("https://github.com/mfilippov/gradle-jvm-wrapper")
    plugins {
        create("jvm-wrapper-plugin") {
            id = "me.filippov.gradle.jvm.wrapper"
            implementationClass = "me.filippov.gradle.jvm.wrapper.Plugin"
            description = "Allows using gradle wrapper with embedded Java"
            displayName = "Embedded JVM in gradle wrapper plugin"
            tags = listOf("wrapper", "jvm", "embedded", "plugin")
        }
    }
}
