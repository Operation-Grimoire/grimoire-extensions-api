plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// The root build owns the version shared with extensions-core.
android {
    namespace = "io.grimoire.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    api(project(":core"))
    implementation("io.grimoire:browser-sessions-android:0.1.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.webkit:webkit:1.14.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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
                version = rootProject.version.toString()
            }
        }
    }
}
