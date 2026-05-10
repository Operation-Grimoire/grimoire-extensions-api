plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

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
                version = "0.1.0-SNAPSHOT"
            }
        }
    }
}
