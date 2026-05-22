package io.grimoire.api.model

// @JvmOverloads keeps shorter constructor overloads on the JVM ABI so extensions
// built against an older api keep linking. Append new fields only; never reorder.
data class NovelPage @JvmOverloads constructor(
    val index: Int,
    val text: String,
    /** When non-null, the page is an illustration to render at this position instead of [text]. */
    val imageUrl: String? = null,
)
