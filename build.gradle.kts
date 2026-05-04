import java.util.Properties

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.androidLint) apply false

    id("maven-publish")
    id("signing")
}

// Set version first.
// Resolution order (highest priority first):
//   1. -PkompassVersion=... passed on the Gradle command line (used by CI publish workflow)
//   2. kompassVersion=... in local.properties (used for local dev / publishToMavenLocal)
//   3. literal "1.0.0" fallback (should never be hit in practice)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val kompassVersion: String =
    (project.findProperty("kompassVersion") as? String)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("kompassVersion")
        ?: "1.0.0"
version = kompassVersion

allprojects {
    version = rootProject.version

    repositories {
        google()
        mavenCentral()
    }
}