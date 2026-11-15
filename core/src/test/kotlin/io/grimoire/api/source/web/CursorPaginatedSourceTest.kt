package io.grimoire.api.source.web

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.ChapterPage
import io.grimoire.api.model.novel.Novel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CursorPaginatedSourceTest {
    private val novel = Novel("https://example.com/book", "Book", Language.EN)

    private fun chapter(id: Int) = Chapter("https://example.com/book/$id", "Chapter $id", chapterNumber = id.toFloat())

    private fun source(calls: MutableList<String?>, pages: Map<String?, ChapterPage>) = object : CursorPaginatedSource {
        override val name = "Fixture"
        override val lang = Language.EN
        override suspend fun getNovelDetails(novel: Novel) = novel
        override suspend fun getChapterList(novel: Novel, cursor: String?): ChapterPage {
            calls += cursor
            return pages.getValue(cursor)
        }
    }

    @Test
    fun `walks cursors in order reports progress and removes duplicate chapter URLs`() = runBlocking {
        val calls = mutableListOf<String?>()
        val source = source(calls, mapOf(
            null to ChapterPage(listOf(chapter(1), chapter(1)), "second"),
            "second" to ChapterPage(listOf(chapter(2)), "third"),
            "third" to ChapterPage(listOf(chapter(3)), null),
        ))
        val progress = mutableListOf<Int>()
        val chapters = source.getAllChapters(novel, onPageProgress = progress::add)
        assertEquals(listOf(null, "second", "third"), calls)
        assertEquals(listOf(1, 2, 3), chapters.map { it.chapterNumber.toInt() })
        assertEquals(listOf(1, 2, 3), progress)
    }

    @Test
    fun `a terminal empty page returns chapters already collected`() = runBlocking {
        val source = source(mutableListOf(), mapOf(
            null to ChapterPage(listOf(chapter(1)), "next"),
            "next" to ChapterPage(emptyList(), null),
        ))
        assertEquals(1, source.getAllChapters(novel).size)
    }

    @Test
    fun `rejects repeated and blank continuation cursors`() {
        for (next in listOf("repeat", " ")) {
            val source = source(mutableListOf(), mapOf(
                null to ChapterPage(listOf(chapter(1)), next),
                next to ChapterPage(listOf(chapter(2)), next),
            ))
            assertThrows(IllegalStateException::class.java) { runBlocking { source.getAllChapters(novel) } }
        }
    }

    @Test
    fun `rejects an empty or entirely duplicate continuation page`() {
        for (chapters in listOf(emptyList(), listOf(chapter(1)))) {
            val source = source(mutableListOf(), mapOf(
                null to ChapterPage(listOf(chapter(1)), "next"),
                "next" to ChapterPage(chapters, "later"),
            ))
            assertThrows(IllegalStateException::class.java) { runBlocking { source.getAllChapters(novel) } }
        }
    }

    @Test
    fun `rejects continuation beyond the page limit`() {
        val source = source(mutableListOf(), mapOf(null to ChapterPage(listOf(chapter(1)), "next")))
        assertThrows(IllegalStateException::class.java) { runBlocking { source.getAllChapters(novel, maxPages = 1) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { source.getAllChapters(novel, maxPages = 0) } }
    }
}
