package io.grimoire.api.source

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel

/**
 * Base of every source: identity plus novel-detail resolution. Content is a
 * capability — a web source declares a chapter list ([io.grimoire.api.source.web.ChapterListSource]
 * or [io.grimoire.api.source.web.PaginatedSource]) plus [io.grimoire.api.source.web.PageListSource];
 * an [io.grimoire.api.source.epub.EpubSource] delivers a whole book instead. Browse/search
 * capabilities live in `source.feature`.
 */
interface Source {
    /** Human-readable source name shown in the UI (e.g. "NovelFull"). */
    val name: String

    /**
     * The source's content language. Single-language sources return their one
     * language; sources that serve many return [Language.MULTI].
     */
    val lang: Language

    /**
     * Resolves full metadata for [novel] (which carries at least its `url`),
     * returning a populated [Novel] with `initialized = true`. Runs off the main
     * thread; may throw on network/parse failure.
     */
    suspend fun getNovelDetails(novel: Novel): Novel
}
