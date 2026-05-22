package io.grimoire.api.model

data class NovelPage(
    val index: Int,
    val text: String,
    /** When non-null, the page is an illustration to render at this position instead of [text]. */
    val imageUrl: String? = null,
)
