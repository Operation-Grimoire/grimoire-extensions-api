package io.grimoire.api.network

import android.webkit.CookieManager
import okhttp3.HttpUrl

/** The process WebView profile. Reads and writes use the same CookieManager singleton. */
internal class WebViewSourceCookieStore : SourceCookieStore {
    private val manager: CookieManager by lazy { CookieManager.getInstance() }

    override fun cookieHeader(url: HttpUrl): String = manager.getCookie(url.toString()).orEmpty()

    override fun setCookie(url: HttpUrl, header: String) {
        manager.setCookie(url.toString(), header)
    }

    override fun flush() {
        manager.flush()
    }
}
