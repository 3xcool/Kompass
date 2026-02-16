import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

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

signing {
    val keyId = System.getenv("GPG_KEY_ID") ?: findProperty("GPG_KEY_ID")?.toString()
    val key = System.getenv("GPG_SECRET_KEY") ?: findProperty("GPG_SECRET_KEY")?.toString()
    val password = System.getenv("GPG_PASSPHRASE") ?: findProperty("GPG_PASSPHRASE")?.toString()


    println("DEBUG: keyId = $keyId")
    println("DEBUG: password = $password")

    if (keyId != null && key != null && password != null) {
        useInMemoryPgpKeys(keyId, key, password)
        sign(publishing.publications)
    }
}

mavenPublishing {
    coordinates(
        groupId = "com.3xcool",
        artifactId = "kompass",
        version = "0.0.2"
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