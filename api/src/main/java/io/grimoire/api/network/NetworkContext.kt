package io.grimoire.api.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings

/**
 * Holds the application [Context] needed by network components that must spin
 * up a [android.webkit.WebView] (e.g. [CloudflareInterceptor]).
 *
 * Extensions are instantiated through a no-arg constructor, so there is no way
 * to pass a Context down to a [HttpSource]. The host app is expected to call
 * [init] once during application startup. Components degrade gracefully when
 * the context is absent (Cloudflare challenges are simply not solved).
 */
object NetworkContext {

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedUserAgent: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context?
        get() = appContext

    /**
     * The User-Agent used for every request. Cloudflare ties the issued
     * `cf_clearance` cookie to the User-Agent, so OkHttp and the WebView that
     * solves the challenge must send the exact same value.
     */
    val userAgent: String
        get() {
            cachedUserAgent?.let { return it }
            val resolved = runCatching {
                appContext?.let { WebSettings.getDefaultUserAgent(it) }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_USER_AGENT
            cachedUserAgent = resolved
            return resolved
        }

    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
}
