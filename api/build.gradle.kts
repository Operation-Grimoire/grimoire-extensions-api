plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// Publication version. The release workflow builds from a vX.Y.Z tag and sets
// API_RELEASE_TAG to publish the immutable release X.Y.Z. Every other build
// (main, local) publishes a -SNAPSHOT of the next version for cross-repo
// development; bump the base below when a release is cut. See README.md.
val publishVersion: String =
    System.getenv("API_RELEASE_TAG")?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() }
        ?: "0.8.0-SNAPSHOT"

android {
    namespace = "io.grimoire.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.jsoup)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    // Gradle 9 no longer auto-resolves the JUnit Platform Launcher from
    // ServiceLoader — without this dep tests are silently discovered as zero.
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Operation-Grimoire/grimoire-extensions-api")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "io.grimoire"
                artifactId = "extensions-api"
                version = publishVersion
            }
        }
    }
}
