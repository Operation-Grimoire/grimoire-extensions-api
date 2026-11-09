package io.grimoire.api.source.web

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel

/**
 * A [ChapterSource] whose chapter list is paginated: the host fetches pages until
 * an empty result is returned (see the app's `fetchAllChapters`). Alternative to
 * [ChapterListSource] — a source implements one or the other, not both.
 */
interface PaginatedSource : ChapterSource {
    suspend fun getChapterList(novel: Novel, page: Int): List<Chapter>

    /**
     * Total number of chapter-list pages for [novel], or null when the source
     * cannot tell cheaply. Most sites print it on the first list page.
     *
     * Implementing this makes the host's fetch robust: with a known count, an
     * empty page inside the range is treated as a transient failure (retried,
     * then surfaced as an error) instead of being mistaken for the end of the
     * list — which is what silently truncates chapter lists when a site
     * throttles or hiccups. Without it the host falls back to stop heuristics.
     *
     * Must run off the main thread; may fetch (e.g. read the pagination widget
     * from page 1) and should cache per novel where practical.
     */
    suspend fun getPageCount(novel: Novel): Int? = null
}
