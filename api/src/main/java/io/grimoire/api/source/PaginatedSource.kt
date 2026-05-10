package io.grimoire.api.source

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel

interface PaginatedSource : Source {
    suspend fun getChapterList(novel: Novel, page: Int): List<Chapter>
}
