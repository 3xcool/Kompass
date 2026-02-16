import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    id("com.vanniktech.maven.publish") version "0.35.0"
    signing
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Kompass"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.collections.immutable)

                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }

        iosMain {
            dependencies {
            }
        }

        jvmMain {
            dependencies {
            }
        }
    }
}

android {
    namespace = "com.tekmoon.kompass"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

//signing {
//    val keyId = System.getenv("GPG_KEY_ID")
//    val password = System.getenv("GPG_PASSPHRASE")
//    val keyFile = System.getenv("HOME")?.let { "$it/.gnupg/secring.gpg" }
//
//    if (keyId != null && password != null && keyFile != null) {
//        useInMemoryPgpKeys(keyId, keyFile, password)
//        sign(publishing.publications)
//    }
//}

signing {
    val keyId = System.getenv("GPG_KEY_ID")
    val password = System.getenv("GPG_PASSPHRASE")
    val keyFile = System.getenv("HOME")?.let { "$it/.gnupg/secring.gpg" }

    println("DEBUG: keyId = $keyId")
    println("DEBUG: password = $password")
    println("DEBUG: keyFile = $keyFile")

    if (keyId != null && password != null && keyFile != null) {
        useInMemoryPgpKeys(keyId, keyFile, password)
        sign(publishing.publications)
    } else {
        println("DEBUG: Signing not configured - one or more env vars missing")
    }
}

mavenPublishing {
    coordinates(
        groupId = "com.3xcool",
        artifactId = "kompass",
        version = "0.0.1"
    )

    pom {
        name.set("Kompass")
        description.set("A lightweight, type-safe navigation library for Kotlin Multiplatform")
        inceptionYear.set("2026")
        url.set("https://github.com/3xcool/kompass")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("3xcool")
                name.set("3xcool")
                email.set("alg.filgueiras@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/3xcool/kompass")
            connection.set("scm:git:https://github.com/3xcool/kompass.git")
            developerConnection.set("scm:git:https://github.com/3xcool/kompass.git")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}

tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
    notCompatibleWithConfigurationCache("Maven publish is incompatible with configuration cache")
}

task("testClasses") {}