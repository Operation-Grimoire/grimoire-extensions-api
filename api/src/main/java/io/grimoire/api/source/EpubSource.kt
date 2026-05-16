package io.grimoire.api.source

import io.grimoire.api.model.Novel

/**
 * A [Source] that delivers a whole book as a single EPUB file rather than
 * scraping chapter HTML. The host app downloads the bytes once, parses the
 * EPUB locally (extracting chapters, text and cover), and stores the result —
 * so [Source.getChapterList] / [Source.getPageList] are unused for such a
 * source and may return empty lists.
 */
interface EpubSource : Source {
    /** Returns the raw `.epub` bytes for [novel]. */
    suspend fun getEpub(novel: Novel): ByteArray
}
