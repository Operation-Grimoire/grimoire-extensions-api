package io.grimoire.api.network

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelPage
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ParsedHttpSource : HttpSource() {

    // Popular novels
    abstract fun popularNovelsSelector(): String
    abstract fun popularNovelsFromElement(element: Element): Novel
    open fun popularNovelsNextPageSelector(): String? = null

    // Latest updates
    abstract fun latestUpdatesSelector(): String
    abstract fun latestUpdatesFromElement(element: Element): Novel
    open fun latestUpdatesNextPageSelector(): String? = null

    // Search
    abstract fun searchNovelsSelector(): String
    abstract fun searchNovelsFromElement(element: Element): Novel
    open fun searchNovelsNextPageSelector(): String? = null

    // Novel details
    abstract fun novelDetailsFromDocument(document: Document): Novel

    // Chapter list
    abstract fun chapterListSelector(): String
    abstract fun chapterFromElement(element: Element): Chapter

    // Page list (novel text content)
    abstract fun pageListSelector(): String
    abstract fun pageFromElement(element: Element, index: Int): NovelPage

    // HttpSource parse implementations using Jsoup
    override suspend fun popularNovelsParse(response: Response): List<Novel> =
        response.asJsoup().select(popularNovelsSelector()).map { popularNovelsFromElement(it) }

    override suspend fun latestUpdatesParse(response: Response): List<Novel> =
        response.asJsoup().select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }

    override suspend fun searchNovelsParse(response: Response): List<Novel> =
        response.asJsoup().select(searchNovelsSelector()).map { searchNovelsFromElement(it) }

    override suspend fun novelDetailsParse(response: Response): Novel =
        novelDetailsFromDocument(response.asJsoup())

    override suspend fun chapterListParse(response: Response): List<Chapter> =
        response.asJsoup().select(chapterListSelector()).map { chapterFromElement(it) }

    override suspend fun pageListParse(response: Response): List<NovelPage> =
        response.asJsoup().select(pageListSelector()).mapIndexed { index, element ->
            pageFromElement(element, index)
        }

    protected fun Response.asJsoup(): Document =
        Jsoup.parse(body!!.string(), request.url.toString())
}
