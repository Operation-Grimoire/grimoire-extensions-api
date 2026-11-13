package io.grimoire.api.network

import android.webkit.CookieManager
import io.grimoire.sessions.BrowserSession
import io.grimoire.sessions.android.WebViewCookieStore
import okhttp3.CookieJar
import okhttp3.Interceptor

/** Service-loaded default preserves source construction before NetworkContext.init. */
class AndroidSourceHttpPlatform :
    SourceHttpPlatform,
    SourceHttpNetworkInterceptors,
    SourceBrowserCookies,
    SourceDocumentBrowser {
    // Unit tests return null from CookieManager.getInstance(). Keep source
    // construction working there; a real process still uses the WebView profile.
    private val session = webViewSession()
    private val documents = AndroidDocumentBrowser()
    override val cookieJar: CookieJar = session?.let { WebViewCookieJar(it.cookieJar) }
        ?: WebViewCookieJar(CookieJar.NO_COOKIES)
    override val cookies: SourceCookieStore = if (session != null) {
        WebViewSourceCookieStore()
    } else {
        JarCookieStore(CookieJar.NO_COOKIES)
    }
    override val userAgent: String get() = NetworkContext.userAgent
    override val interceptors: List<Interceptor> = listOf(CloudflareInterceptor())
    override val networkInterceptors: List<Interceptor> = listOfNotNull(session?.networkInterceptor)
    override val ready: Boolean get() = documents.ready

    override suspend fun read(
        url: String,
        script: String,
        timeoutMillis: Long,
        decide: (SourceBrowserReading) -> SourceDocumentDecision,
    ): SourceBrowserReading = documents.read(url, script, timeoutMillis, decide)
}

private fun webViewSession(): BrowserSession? {
    val manager = CookieManager.getInstance() as CookieManager? ?: return null
    return BrowserSession(WebViewCookieStore(manager))
}
