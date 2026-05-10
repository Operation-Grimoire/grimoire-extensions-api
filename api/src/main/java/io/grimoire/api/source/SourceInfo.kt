package io.grimoire.api.source

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SourceInfo(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val versionCode: Int = 1,
)
