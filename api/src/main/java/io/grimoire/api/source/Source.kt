package io.grimoire.api.source

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelPage

interface Source {
    /** Legacy id — ignored; identity comes from the package via [sourceIdFor]. Don't override. */
    @Deprecated("Identity is derived from the package name via sourceIdFor(); this value is ignored.")
    val id: Long get() = 0L
    val name: String
    val lang: String

    suspend fun getNovelDetails(novel: Novel): Novel
    suspend fun getChapterList(novel: Novel): List<Chapter>
    suspend fun getPageList(chapter: Chapter): List<NovelPage>
}
