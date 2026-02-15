plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    id("maven-publish")
    id("signing")
}

kotlin {
    jvm()

    androidLibrary {
        namespace = "com.tekmoon.kompass"
        compileSdk = 36
        minSdk = 24

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    val xcfName = "kompassKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

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

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.testExt.junit)
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

/* ============================================
   Publishing to Maven Central Configuration
   ============================================ */

publishing {
    publications {
        create<MavenPublication>("kompass") {
            from(components["kotlin"])

            groupId = "io.github.3xcool"
            artifactId = "kompass"
            version = project.version.toString()

            pom {
                name.set("Kompass")
                description.set("A lightweight, type-safe navigation library for Kotlin Multiplatform")
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
                    connection.set("scm:git:https://github.com/3xcool/kompass.git")
                    developerConnection.set("scm:git:https://github.com/3xcool/kompass.git")
                    url.set("https://github.com/3xcool/kompass")
                }
            }
        }
    }

    repositories {
        maven {
            name = "MavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")

            credentials {
                username = findProperty("mavenCentralUsername")?.toString() ?: System.getenv("MAVEN_CENTRAL_USERNAME") ?: ""
                password = findProperty("mavenCentralPassword")?.toString() ?: System.getenv("MAVEN_CENTRAL_PASSWORD") ?: ""
            }
        }
    }
}

signing {
    sign(publishing.publications["kompass"])
}

tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("signing.keyId") || System.getenv("GPG_KEY_ID") != null }
}