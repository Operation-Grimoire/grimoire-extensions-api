plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Both artifacts are one binary contract and must publish at the same version.
val publishVersion = providers.environmentVariable("API_RELEASE_TAG").orNull
    ?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: "1.5.1-SNAPSHOT"
allprojects {
    group = "io.grimoire"
    version = publishVersion
}
