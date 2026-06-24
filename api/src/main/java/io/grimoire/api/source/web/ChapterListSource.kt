package io.grimoire.api.source.web

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel

/** A [ChapterSource] that returns the whole chapter list in one call. */
interface ChapterListSource : ChapterSource {
    suspend fun getChapterList(novel: Novel): List<Chapter>
}
