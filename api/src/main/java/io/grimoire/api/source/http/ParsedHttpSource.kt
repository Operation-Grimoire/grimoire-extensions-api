package io.grimoire.api.source.http

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** [HttpSource] for sources that scrape HTML — adds a Jsoup parse helper. */
abstract class ParsedHttpSource : HttpSource() {

    /** Parse this response body as an HTML [Document], using the request URL as base URI. */
    protected fun Response.asJsoup(): Document =
        Jsoup.parse(body!!.string(), request.url.toString())
}
