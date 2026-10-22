import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.github.vue3setupcreator"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // WebStorm bundles the Vue.js plugin, the plugin therefore targets WebStorm.
        create(IntelliJPlatformType.WebStorm, "2026.2.1")
        // Bundled plugins the code compiles against.
        bundledPlugin("JavaScript")
        bundledPlugin("org.jetbrains.plugins.vue")
        pluginVerifier()
        zipSigner()
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.vue3setupcreator"
        name = "Vue Script Setup Creator"
        version = project.version.toString()

        description = """
            <p>Detects variables and methods that are used inside the <code>&lt;template&gt;</code> block
            of a Vue 3 single file component but are never defined.</p>
            <p>When the mouse is moved over such an identifier the quick fixes <b>创建变量</b>
            (create variable) and <b>创建方法</b> (create method) are offered. Both of them add the new
            binding to the <code>&lt;script setup&gt;</code> block of the component, the block is
            created automatically when it does not exist yet.</p>
        """.trimIndent()

        changeNotes = """
            <p><b>1.0.0</b></p>
            <ul>
              <li>Detects undefined variables and methods in Vue 3 templates.</li>
              <li>Quick fixes “创建变量” / “创建方法” create the binding inside
                  <code>&lt;script setup&gt;</code>.</li>
            </ul>
        """.trimIndent()

        // Fill in your own data before publishing to the JetBrains Marketplace.
        vendor {
            name = providers.gradleProperty("vendorName").getOrElse("vue3-setup-creator")
            email = providers.gradleProperty("vendorEmail").getOrElse("")
            url = providers.gradleProperty("vendorUrl").getOrElse("")
        }

        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }

    // Credentials for the JetBrains Marketplace, see PUBLISHING.md.
    publishing {
        token = providers.gradleProperty("marketplaceToken")
            .orElse(providers.environmentVariable("MARKETPLACE_TOKEN"))
        channels = providers.gradleProperty("marketplaceChannels")
            .map { it.split(',').map(String::trim) }
            .orElse(listOf("default"))
    }

    // Optional: sign the distribution with a Marketplace compatible certificate.
    signing {
        certificateChain = providers.gradleProperty("certificateChain")
        privateKey = providers.gradleProperty("privateKey")
        password = providers.gradleProperty("privateKeyPassword")
    }

    pluginVerification {
        // The IDE that is used for the verification is the one from the dependencies block.
        ides {
            recommended()
        }
    }
}

java {
    toolchain {
        // The platform classes of WebStorm 2026.2 are compiled for Java 25.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    test {
        useJUnit()
    }
}
