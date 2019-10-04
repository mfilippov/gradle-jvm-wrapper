plugins {
    id("com.gradle.plugin-publish") version "0.10.1"
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.50"
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("jvm-wrapper") {
            id = "me.filippov.jvm.wrapper"
            implementationClass = "me.filippov.jvm.wrapper.JvmWrapperPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/mfilippov/gradle-jvm-wrapper"
    vcsUrl = "https://github.com/mfilippov/gradle-jvm-wrapper"
    description = "Allows using gradle wrapper with embedded Java"
}
