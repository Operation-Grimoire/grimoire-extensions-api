package io.grimoire.api.model.novel

/**
 * One cursor-based page of chapter metadata. [nextCursor] is null at the end of
 * the list; a non-null value must be passed to the following request.
 */
data class ChapterPage @JvmOverloads constructor(
    val chapters: List<Chapter>,
    val nextCursor: String? = null,
)
