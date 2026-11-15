package io.grimoire.api.source.web

import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.ChapterPage
import io.grimoire.api.model.novel.Novel

/**
 * A [ChapterSource] whose backend continues from opaque cursors rather than
 * numbered pages. The host starts with a null cursor and passes each returned
 * [ChapterPage.nextCursor] to the following call. A null cursor ends the list.
 */
interface CursorPaginatedSource : ChapterSource {
    suspend fun getChapterList(novel: Novel, cursor: String?): ChapterPage
}

/**
 * Fetch a complete chapter list sequentially. Duplicate chapter URLs are
 * removed. Empty continuation pages, blank or repeated cursors, and walks past
 * [maxPages] fail rather than silently truncating the result.
 */
suspend fun CursorPaginatedSource.getAllChapters(
    novel: Novel,
    maxPages: Int = 1_000,
    onPageProgress: (page: Int) -> Unit = {},
): List<Chapter> {
    require(maxPages > 0)
    val all = mutableListOf<Chapter>()
    val seenChapters = mutableSetOf<String>()
    val seenCursors = mutableSetOf<String>()
    var cursor: String? = null
    for (page in 1..maxPages) {
        val result = getChapterList(novel, cursor)
        onPageProgress(page)
        val next = result.nextCursor
        check(next == null || next.isNotBlank()) { "Chapter list page $page returned a blank continuation cursor" }
        val fresh = result.chapters.distinctBy { it.url }.filter { seenChapters.add(it.url) }
        check(fresh.isNotEmpty() || next == null) { "Chapter list page $page has a continuation but no new chapters" }
        all += fresh
        if (next == null) return all
        check(seenCursors.add(next)) { "Chapter list page $page repeated continuation cursor $next" }
        cursor = next
    }
    error("Chapter list exceeded the $maxPages-page limit")
}
