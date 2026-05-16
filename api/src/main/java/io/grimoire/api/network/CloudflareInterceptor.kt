package io.grimoire.api.network

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Detects Cloudflare "I'm Under Attack" / managed-challenge interstitials and
 * transparently solves them.
 *
 * When a challenge response is seen, a headless [WebView] loads the same URL,
 * runs Cloudflare's JavaScript, and obtains a `cf_clearance` cookie. That
 * cookie is persisted through [WebViewCookieJar] (shared with OkHttp), after
 * which the original request is retried and succeeds.
 *
 * If no Android context is available (see [NetworkContext]) the original
 * challenge response is returned unchanged so callers can handle it.
 */
class CloudflareInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isCloudflareChallenge()) {
            return response
        }

        val context = NetworkContext.context ?: return response
        response.close()

        val url = request.url
        val solved = runCatching {
            resolveWithWebView(url.toString())
        }.getOrDefault(false)

        if (!solved) {
            throw CloudflareBypassException(url.toString())
        }

        return chain.proceed(request)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(url: String): Boolean {
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null

        handler.post {
            val view = WebView(NetworkContext.context!!)
            webView = view
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = NetworkContext.userAgent
            }
            view.webViewClient = object : WebViewClient() {
                @Volatile
                private var settling = false

                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    if (!hasClearance(url) || settling) return
                    // Clearance is set, but the site's own scripts may still
                    // need to run to issue first-party session cookies (e.g.
                    // tokens gating downloads). Instead of a blind delay, poll
                    // the cookie store and continue as soon as cookies grow
                    // beyond clearance (the JS token landed), capping the extra
                    // wait at SETTLE_MS so the common case stays fast.
                    settling = true
                    val deadline = System.currentTimeMillis() + SETTLE_MS
                    val baseline = cookieCount(url)
                    val poll = object : Runnable {
                        override fun run() {
                            if (cookieCount(url) > baseline ||
                                System.currentTimeMillis() >= deadline
                            ) {
                                latch.countDown()
                            } else {
                                handler.postDelayed(this, SETTLE_POLL_MS)
                            }
                        }
                    }
                    handler.postDelayed(poll, SETTLE_POLL_MS)
                }
            }
            view.loadUrl(url)
        }

        val obtained = try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) && hasClearance(url)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
        }
        return obtained
    }

    private fun hasClearance(url: String): Boolean {
        val cookies = android.webkit.CookieManager.getInstance().getCookie(url) ?: return false
        return cookies.contains(CLEARANCE_COOKIE)
    }

    private fun cookieCount(url: String): Int =
        android.webkit.CookieManager.getInstance().getCookie(url)
            ?.split(';')?.count { it.isNotBlank() } ?: 0

    private fun Response.isCloudflareChallenge(): Boolean {
        if (code != 403 && code != 503) return false
        val server = header("Server").orEmpty()
        if (!server.contains("cloudflare", ignoreCase = true)) return false
        if (header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true) return true

        val snippet = runCatching {
            peekBody(BODY_PEEK_BYTES).string()
        }.getOrNull().orEmpty()
        if (CHALLENGE_MARKERS.any { snippet.contains(it, ignoreCase = true) }) return true

        // Lightweight interstitials (e.g. some per-user mirrors) carry none of
        // the usual script markers — just a "checking your browser" HTML body
        // served with a cf-ray. A short HTML body from Cloudflare on a 403/503
        // with a ray id is an interstitial, not an origin error (origin errors
        // are proxied without a Cloudflare-generated HTML challenge shell).
        val isHtml = header("Content-Type")?.contains("text/html", ignoreCase = true) == true
        return isHtml && header("cf-ray") != null && snippet.length < SHORT_HTML_BYTES
    }

    companion object {
        private const val CLEARANCE_COOKIE = "cf_clearance"
        private const val TIMEOUT_SECONDS = 60L

        // After Cloudflare clearance, keep the WebView alive until the site's
        // own scripts set first-party session cookies (some sites gate
        // content/downloads on a JS-issued token), polling so the common case
        // returns quickly; SETTLE_MS is only the worst-case ceiling.
        private const val SETTLE_MS = 5000L
        private const val SETTLE_POLL_MS = 200L
        private const val BODY_PEEK_BYTES = 128L * 1024L

        // Cloudflare interstitial bodies are tiny shells; real origin error
        // pages proxied through Cloudflare are typically larger.
        private const val SHORT_HTML_BYTES = 30 * 1024

        private val CHALLENGE_MARKERS = listOf(
            "_cf_chl_opt",
            "cf-browser-verification",
            "challenge-platform",
            "cf_chl_",
            "/cdn-cgi/challenge-platform",
            "just a moment",
            "checking your browser",
            "cf-spinner",
        )
    }
}

class CloudflareBypassException(url: String) :
    IOException("Failed to bypass Cloudflare protection for $url")
