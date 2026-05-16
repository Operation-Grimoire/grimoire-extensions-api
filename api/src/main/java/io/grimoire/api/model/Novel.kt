package io.grimoire.api.model

data class Novel(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: NovelStatus = NovelStatus.UNKNOWN,
    // Average user rating normalized to a 0..5 scale. Sources that expose a
    // different scale (e.g. 0..10) must convert before populating this field.
    val rating: Float? = null,
    val ratingCount: Int? = null,
    // Content language as a plain English name (e.g. "English"). Multi-language
    // sources should populate this; the host may display it. Null = unknown.
    val language: String? = null,
    val initialized: Boolean = false,
)

enum class NovelStatus {
    UNKNOWN, ONGOING, COMPLETED, HIATUS, CANCELLED
}
