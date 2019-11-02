plugins {
    id("com.gradle.plugin-publish") version "0.10.1"
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.50"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testCompile("junit:junit:4.12")
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
    version = "0.9.1"

    (plugins) {
        "jvm-wrapper-plugin" {
            displayName = "Embedded JVM in gradle wrapper plugin"
            tags = listOf("wrapper", "jvm", "embedded", "plugin")
        }
    }
}