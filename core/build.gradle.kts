plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}
kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}
tasks.jar { archiveBaseName.set("extensions-core") }

dependencies {
    api(libs.okhttp)
    api(libs.jsoup)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // SourceNetwork has process-lifetime state; each test class gets a fresh host JVM.
    forkEvery = 1
}

publishing {
    publications {
        create<MavenPublication>("core") {
            from(components["java"])
            artifactId = "extensions-core"
        }
    }
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
}
