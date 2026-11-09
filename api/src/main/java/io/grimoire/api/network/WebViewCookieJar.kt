package io.grimoire.api.network

import io.grimoire.sessions.BrowserCookieJar
import io.grimoire.sessions.android.WebViewCookieStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Binary-compatible entry point backed by the shared browser-sessions Android adapter. */
class WebViewCookieJar internal constructor(private val delegate: CookieJar) : CookieJar {
    constructor() : this(BrowserCookieJar(WebViewCookieStore()))

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = delegate.saveFromResponse(url, cookies)
    override fun loadForRequest(url: HttpUrl): List<Cookie> = delegate.loadForRequest(url)
}
