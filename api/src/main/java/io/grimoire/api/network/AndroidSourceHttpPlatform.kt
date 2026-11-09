package io.grimoire.api.network

import io.grimoire.sessions.BrowserSession
import io.grimoire.sessions.android.WebViewCookieStore
import okhttp3.CookieJar
import okhttp3.Interceptor

/** Service-loaded default preserves source construction before NetworkContext.init. */
class AndroidSourceHttpPlatform : SourceHttpPlatform, SourceHttpNetworkInterceptors {
    private val session = BrowserSession(WebViewCookieStore())
    override val cookieJar: CookieJar = WebViewCookieJar(session.cookieJar)
    override val userAgent: String get() = NetworkContext.userAgent
    override val interceptors: List<Interceptor> = listOf(CloudflareInterceptor())
    override val networkInterceptors: List<Interceptor> = listOf(session.networkInterceptor)
}
