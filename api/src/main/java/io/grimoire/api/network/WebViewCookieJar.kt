package io.grimoire.api.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * OkHttp [CookieJar] backed by the system WebView's [CookieManager]. Sharing one
 * cookie store lets a WebView sign-in (see
 * [io.grimoire.api.source.feature.WebViewLoginSource]) and a solved Cloudflare
 * challenge replay automatically on the source's OkHttp requests, and vice versa.
 */
class WebViewCookieJar : CookieJar {

    private val cookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        cookies.forEach { cookieManager.setCookie(urlString, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val rawCookies = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return rawCookies.split(";")
            .mapNotNull { Cookie.parse(url, it.trim()) }
    }
}
