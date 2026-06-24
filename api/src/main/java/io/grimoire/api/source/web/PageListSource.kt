package io.grimoire.api.source.web

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.source.Source

/**
 * A [Source] that returns the page content of a chapter (web reading). Paired with a
 * [ChapterSource]; the EPUB path ([io.grimoire.api.source.epub.EpubSource]) supplies
 * content from the parsed file instead.
 */
interface PageListSource : Source {
    suspend fun getPageList(chapter: Chapter): List<NovelPage>
}
