rootProject.name = "vue3-setup-creator"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
    }
}

plugins {
    // Allows Gradle to download the required JDK automatically.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
