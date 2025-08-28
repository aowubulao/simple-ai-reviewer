val okhttpVersion = "4.12.0"
val gsonVersion = "2.10.1"
val commonmarkVersion = "0.21.0"

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "io.github.aowubulao"
version = "1.0.0-RELEASE"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("Git4Idea")
    }

    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    implementation("com.google.code.gson:gson:$gsonVersion")

    implementation("org.commonmark:commonmark:$commonmarkVersion")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }

    buildSearchableOptions = false
    instrumentCode = false
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    jar {
        from(configurations.runtimeClasspath.get().map {
            if (it.isDirectory()) it else zipTree(it)
        }) {
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")
            exclude("META-INF/DEPENDENCIES")
            exclude("META-INF/LICENSE*")
            exclude("META-INF/NOTICE*")
            exclude("META-INF/versions/**")
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
