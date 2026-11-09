package io.grimoire.api.source.http

import io.grimoire.api.network.defaultOkHttpClient
import io.grimoire.api.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Transport base for HTTP-backed sources. Provides only the shared OkHttp client
 * (cookie jar + User-Agent + Cloudflare handling) and small request helpers.
 *
 * It carries no capability of its own — a source declares what it supports
 * ([io.grimoire.api.source.web.ChapterListSource]/[io.grimoire.api.source.web.PaginatedSource], [io.grimoire.api.source.web.PageListSource],
 * [io.grimoire.api.source.epub.EpubSource], `PopularSource`, `SearchSource`, …) and
 * implements those methods using [get] / [client]. [ParsedHttpSource] adds an HTML
 * ([asJsoup]) helper on top.
 */
abstract class HttpSource : Source {

    abstract val baseUrl: String

    open val client: OkHttpClient = defaultOkHttpClient()

    /** Absolutise a possibly-relative URL against [baseUrl]. */
    protected fun resolveUrl(url: String): String =
        if (url.startsWith("http")) url else "$baseUrl$url"

    /** Build a GET request. */
    protected fun GET(url: String): Request =
        Request.Builder().url(url).build()

    /** GET [url] off the main thread and return the raw response. */
    protected suspend fun get(url: String): Response =
        withContext(Dispatchers.IO) { client.newCall(GET(url)).execute() }
}
