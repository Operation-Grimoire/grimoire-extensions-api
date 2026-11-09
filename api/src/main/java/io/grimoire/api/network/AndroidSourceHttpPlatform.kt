package io.grimoire.api.network

import okhttp3.CookieJar
import okhttp3.Interceptor

/** Service-loaded default preserves source construction before NetworkContext.init. */
class AndroidSourceHttpPlatform : SourceHttpPlatform {
    override val cookieJar: CookieJar = WebViewCookieJar()
    override val userAgent: String get() = NetworkContext.userAgent
    override val interceptors: List<Interceptor> = listOf(CloudflareInterceptor())
}
