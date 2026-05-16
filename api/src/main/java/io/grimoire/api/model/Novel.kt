package io.grimoire.api.model

/**
 * NOTE ON BINARY COMPATIBILITY: extensions are compiled separately and shipped
 * as their own APKs, then run against the host's copy of this class. Adding a
 * parameter to the primary constructor changes the synthetic default-args
 * constructor descriptor, which makes every already-installed extension crash
 * with `NoSuchMethodError`. So new optional attributes must be added as body
 * `var`s (set after construction), NOT as constructor parameters.
 */
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
    val initialized: Boolean = false,
) {
    // Content language as a plain English name (e.g. "English"). Multi-language
    // sources should set this; the host may display it. Kept out of the
    // primary constructor for binary compatibility (see class note).
    var language: String? = null
}

enum class NovelStatus {
    UNKNOWN, ONGOING, COMPLETED, HIATUS, CANCELLED
}
