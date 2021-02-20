plugins {
    id("com.gradle.plugin-publish") version "0.13.0"
    id("me.filippov.gradle.jvm.wrapper") version("0.9.3")
    `java-gradle-plugin`
    kotlin("jvm") version "1.4.30"
}

repositories {
    mavenCentral()
}


dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
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
    version = "0.10.0"

    (plugins) {
        "jvm-wrapper-plugin" {
            displayName = "Embedded JVM in gradle wrapper plugin"
            tags = listOf("wrapper", "jvm", "embedded", "plugin")
        }
    }
}
