package io.grimoire.api.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Host-owned cookie profile. Matching and persistence belong to the browser that
 * signed the user in, not to a second copy inside the source.
 */
interface SourceCookieStore {
    /** `Cookie` header value for [url], or an empty string when none are visible. */
    fun cookieHeader(url: HttpUrl): String

    /** Apply one `Set-Cookie` header, including an expiry that signs the user out. */
    fun setCookie(url: HttpUrl, header: String)

    fun flush()
}

/** Optional [SourceHttpPlatform] capability for hosts that own the browser cookie profile. */
interface SourceBrowserCookies {
    val cookies: SourceCookieStore
}

/**
 * OkHttp jar adapter for hosts that have not installed [SourceBrowserCookies].
 * Attribute-preserving browser stores should implement [SourceCookieStore] directly.
 */
class JarCookieStore(private val jar: CookieJar) : SourceCookieStore {
    override fun cookieHeader(url: HttpUrl): String =
        jar.loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }

    override fun setCookie(url: HttpUrl, header: String) {
        val cookie = Cookie.parse(url, header) ?: return
        jar.saveFromResponse(url, listOf(cookie))
    }

    override fun flush() = Unit
}

/**
 * Cookie access for extension code. A host store wins. An older Android host still
 * reaches `CookieManager` so sign-out keeps working before that host is updated.
 * Every other host uses its installed [CookieJar].
 */
object SourceCookies {
    fun header(url: String): String {
        val httpUrl = url.toHttpUrl()
        browserStore()?.let { return it.cookieHeader(httpUrl) }
        if (LegacyAndroidCookies.available) return LegacyAndroidCookies.header(url)
        return JarCookieStore(SourceNetwork.platform().cookieJar).cookieHeader(httpUrl)
    }

    fun set(url: String, header: String) {
        val httpUrl = url.toHttpUrl()
        browserStore()?.let {
            it.setCookie(httpUrl, header)
            return
        }
        if (LegacyAndroidCookies.available) {
            LegacyAndroidCookies.set(url, header)
            return
        }
        JarCookieStore(SourceNetwork.platform().cookieJar).setCookie(httpUrl, header)
    }

    fun flush() {
        browserStore()?.let {
            it.flush()
            return
        }
        if (LegacyAndroidCookies.available) LegacyAndroidCookies.flush()
    }

    private fun browserStore(): SourceCookieStore? =
        (SourceNetwork.platform() as? SourceBrowserCookies)?.cookies
}

/**
 * Present only when the process can load Android's cookie manager. Inspector and
 * desktop hosts do not have that class, so this stays unused there.
 */
private object LegacyAndroidCookies {
    private val type = runCatching { Class.forName("android.webkit.CookieManager") }.getOrNull()
    private val getInstance = type?.getMethod("getInstance")
    private val getCookie = type?.getMethod("getCookie", String::class.java)
    private val setCookie = type?.getMethod("setCookie", String::class.java, String::class.java)
    private val flushCookie = type?.methods?.firstOrNull { it.name == "flush" && it.parameterCount == 0 }

    val available: Boolean = type != null && getInstance != null && getCookie != null && setCookie != null

    fun header(url: String): String {
        val manager = getInstance!!.invoke(null)
        return (getCookie!!.invoke(manager, url) as String?).orEmpty()
    }

    fun set(url: String, header: String) {
        setCookie!!.invoke(getInstance!!.invoke(null), url, header)
    }

    fun flush() {
        flushCookie?.invoke(getInstance!!.invoke(null))
    }
}
