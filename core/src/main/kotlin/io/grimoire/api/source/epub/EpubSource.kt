package io.grimoire.api.source.epub

import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.Source

/**
 * A [Source] that delivers a whole book as a single EPUB file rather than scraping
 * chapter HTML. The host downloads the bytes once, parses the EPUB locally
 * (extracting chapters, text and cover) and stores the result — the alternative is
 * the web content path ([io.grimoire.api.source.web.ChapterListSource] /
 * [io.grimoire.api.source.web.PaginatedSource] + [io.grimoire.api.source.web.PageListSource]).
 */
interface EpubSource : Source {
    /** Returns the raw `.epub` bytes for [novel]. */
    suspend fun getEpub(novel: Novel): ByteArray
}
