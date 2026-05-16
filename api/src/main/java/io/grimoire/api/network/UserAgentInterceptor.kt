package io.grimoire.api.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ensures every request carries a consistent `User-Agent`. Cloudflare binds the
 * `cf_clearance` cookie to the User-Agent that solved the challenge, so the
 * value here must match the one the WebView uses (see [NetworkContext.userAgent]).
 * Requests that already set a User-Agent explicitly are left untouched.
 */
class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("User-Agent") != null) {
            return chain.proceed(request)
        }
        val withUa = request.newBuilder()
            .header("User-Agent", NetworkContext.userAgent)
            .build()
        return chain.proceed(withUa)
    }
}
