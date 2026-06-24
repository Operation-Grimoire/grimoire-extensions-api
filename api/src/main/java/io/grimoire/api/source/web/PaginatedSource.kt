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
}
