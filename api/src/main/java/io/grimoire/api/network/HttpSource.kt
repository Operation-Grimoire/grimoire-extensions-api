package io.grimoire.api.network

import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Filter
import io.grimoire.api.model.Novel
import io.grimoire.api.model.NovelPage
import io.grimoire.api.source.CatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

abstract class HttpSource : CatalogueSource {

    abstract val baseUrl: String

    open val client: OkHttpClient = defaultOkHttpClient()

    // Request builders — override when a site's URL structure differs from the default
    open fun popularNovelsRequest(page: Int): Request =
        GET("$baseUrl/popular?page=$page")

    open fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest?page=$page")

    open fun searchNovelsRequest(query: String, page: Int, filters: List<Filter<*>>): Request =
        GET("$baseUrl/search?q=$query&page=$page")

    open fun novelDetailsRequest(novel: Novel): Request =
        GET(resolveUrl(novel.url))

    open fun chapterListRequest(novel: Novel): Request =
        GET(resolveUrl(novel.url))

    open fun pageListRequest(chapter: Chapter): Request =
        GET(resolveUrl(chapter.url))

    // Parse fns — implemented by ParsedHttpSource (Jsoup) or overridden directly (JSON)
    abstract suspend fun popularNovelsParse(response: Response): List<Novel>
    abstract suspend fun latestUpdatesParse(response: Response): List<Novel>
    abstract suspend fun searchNovelsParse(response: Response): List<Novel>
    abstract suspend fun novelDetailsParse(response: Response): Novel
    abstract suspend fun chapterListParse(response: Response): List<Chapter>
    abstract suspend fun pageListParse(response: Response): List<NovelPage>

    // CatalogueSource — fetch + delegate to parse
    final override suspend fun getPopularNovels(page: Int): List<Novel> =
        withContext(Dispatchers.IO) {
            popularNovelsParse(client.newCall(popularNovelsRequest(page)).execute())
        }

    final override suspend fun getLatestUpdates(page: Int): List<Novel> =
        withContext(Dispatchers.IO) {
            latestUpdatesParse(client.newCall(latestUpdatesRequest(page)).execute())
        }

    final override suspend fun searchNovels(query: String, page: Int, filters: List<Filter<*>>): List<Novel> =
        withContext(Dispatchers.IO) {
            searchNovelsParse(client.newCall(searchNovelsRequest(query, page, filters)).execute())
        }

    // Source
    final override suspend fun getNovelDetails(novel: Novel): Novel =
        withContext(Dispatchers.IO) {
            novelDetailsParse(client.newCall(novelDetailsRequest(novel)).execute())
        }

    final override suspend fun getChapterList(novel: Novel): List<Chapter> =
        withContext(Dispatchers.IO) {
            chapterListParse(client.newCall(chapterListRequest(novel)).execute())
        }

    final override suspend fun getPageList(chapter: Chapter): List<NovelPage> =
        withContext(Dispatchers.IO) {
            pageListParse(client.newCall(pageListRequest(chapter)).execute())
        }

    protected fun resolveUrl(url: String): String =
        if (url.startsWith("http")) url else "$baseUrl$url"

    protected fun GET(url: String): Request =
        Request.Builder().url(url).build()
}
