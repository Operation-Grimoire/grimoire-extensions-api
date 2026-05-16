package io.grimoire.api.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Default client used by every [HttpSource].
 *
 * It shares cookies with the system WebView ([WebViewCookieJar]), sends a
 * consistent User-Agent ([UserAgentInterceptor]), and transparently solves
 * Cloudflare challenges ([CloudflareInterceptor]). The Cloudflare interceptor
 * is added as an application interceptor so a solved request is retried once
 * after clearance is obtained.
 */
fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .cookieJar(WebViewCookieJar())
    .addInterceptor(UserAgentInterceptor())
    .addInterceptor(CloudflareInterceptor())
    .build()
