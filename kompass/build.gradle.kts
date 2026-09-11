import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import kotlin.apply

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

    wasmJs {
        browser()
    }

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
                api(libs.kotlinx.coroutinesCore)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.collections.immutable)

                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.runtimeSaveable)
                api(libs.androidx.lifecycle.viewmodelCompose)
                api(libs.androidx.lifecycle.runtimeCompose)
                api(libs.androidx.lifecycle.viewmodelSavedstate)
                api(libs.androidx.savedstateCompose)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.compose.uiTest)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.test.koinViewmodel)
            }
        }

        androidUnitTest {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.test.robolectric)
                implementation(libs.test.navigationCompose)
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

        wasmJsMain {
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

    testOptions.unitTests.isIncludeAndroidResources = true

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

val gpgKeyId: String? = System.getenv("GPG_KEY_ID") ?: findProperty("GPG_KEY_ID")?.toString()
val gpgSecretKey: String? = System.getenv("GPG_SECRET_KEY") ?: findProperty("GPG_SECRET_KEY")?.toString()
val gpgPassphrase: String? = System.getenv("GPG_PASSPHRASE") ?: findProperty("GPG_PASSPHRASE")?.toString()

val canSignPublications: Boolean = gpgKeyId != null && gpgSecretKey != null && gpgPassphrase != null

signing {
    if (canSignPublications) {
        useInMemoryPgpKeys(gpgKeyId, gpgSecretKey, gpgPassphrase)
        sign(publishing.publications)
    }
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Resolution order (highest priority first):
//   1. -PkompassVersion=... passed on the Gradle command line (used by CI publish workflow)
//   2. kompassVersion=... in gradle.properties (versioned release default)
//   3. kompassVersion=... in local.properties (legacy fallback)
//   4. literal "1.0.0" fallback
val kompassVersion: String =
    (project.findProperty("kompassVersion") as? String)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("kompassVersion")
        ?: "1.0.0"

mavenPublishing {
    coordinates(
        groupId = "com.tekmoon",
        artifactId = "kompass",
        version = kompassVersion
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

    publishToMavenCentral(automaticRelease = true)
    if (canSignPublications) {
        signAllPublications()
    }
}

tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
    notCompatibleWithConfigurationCache("Maven publish is incompatible with configuration cache")
}

task("testClasses") {}