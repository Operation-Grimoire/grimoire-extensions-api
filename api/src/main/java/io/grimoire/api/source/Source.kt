package io.grimoire.api.source

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelPage

interface Source {
    val id: Long
    val name: String
    val lang: String

    suspend fun getNovelDetails(novel: Novel): Novel
    suspend fun getChapterList(novel: Novel): List<Chapter>
    suspend fun getPageList(chapter: Chapter): List<NovelPage>
}
