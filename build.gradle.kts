plugins {
    id("com.gradle.plugin-publish") version "0.13.0"
    id("me.filippov.gradle.jvm.wrapper") version("0.11.0")
    `java-gradle-plugin`
    kotlin("jvm") version "1.5.31"
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
    kotlinOptions {
        jvmTarget = "1.8"
        allWarningsAsErrors = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("jvm-wrapper-plugin") {
            id = "me.filippov.gradle.jvm.wrapper"
            implementationClass = "me.filippov.gradle.jvm.wrapper.Plugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/mfilippov/gradle-jvm-wrapper"
    vcsUrl = "https://github.com/mfilippov/gradle-jvm-wrapper"
    description = "Allows using gradle wrapper with embedded Java"
    version = "0.12.0"

    (plugins) {
        "jvm-wrapper-plugin" {
            displayName = "Embedded JVM in gradle wrapper plugin"
            tags = listOf("wrapper", "jvm", "embedded", "plugin")
        }
    }
}
