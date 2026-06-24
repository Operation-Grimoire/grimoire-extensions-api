package io.grimoire.api.source.web

import io.grimoire.api.source.Source

/**
 * Marker for a [Source] that provides a chapter list — either the full list
 * ([ChapterListSource]) or one page at a time ([PaginatedSource]). The host uses
 * this to answer "does this source list chapters?"; the EPUB content path
 * ([io.grimoire.api.source.epub.EpubSource]) lists chapters from the parsed file
 * instead and does not implement this.
 */
interface ChapterSource : Source
