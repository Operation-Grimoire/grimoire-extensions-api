package io.grimoire.api.model.novel

/** One renderable unit of a chapter at position [index], carrying typed [content]. */
data class NovelPage(
    val index: Int,
    val content: PageContent,
)
