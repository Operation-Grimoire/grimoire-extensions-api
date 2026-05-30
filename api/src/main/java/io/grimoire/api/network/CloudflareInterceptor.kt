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
 * If no Android context is available (see [NetworkContext]) — or the silent
 * WebView resolve fails — a [CloudflareException] is thrown so callers can
 * surface a dedicated error state (e.g. an "open webview to solve the
 * challenge" CTA) instead of silently parsing the challenge HTML as content
 * and ending up with an empty result.
 */
class CloudflareInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isCloudflareChallenge()) {
            return response
        }

        val urlString = request.url.toString()
        val context = NetworkContext.context
        if (context == null) {
            response.close()
            throw CloudflareException(urlString)
        }
        response.close()

        val solved = runCatching {
            resolveWithWebView(urlString)
        }.getOrDefault(false)

        if (!solved) {
            throw CloudflareBypassException(urlString)
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

        // An origin/edge 5xx (nginx "503 Service Temporarily Unavailable", a 502
        // bad gateway, Cloudflare's own 52x "web server is down") is proxied with
        // Server: cloudflare + a cf-ray too, but it's a plain server error, not a
        // solvable challenge — don't mistake it for one (it has no challenge JS).
        if (ORIGIN_ERROR_MARKERS.any { snippet.contains(it, ignoreCase = true) }) return false

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

        // Plain server/origin errors proxied through Cloudflare. Present in the
        // body of a 5xx, absent from a genuine challenge shell — so they veto the
        // short-HTML+cf-ray heuristic below.
        private val ORIGIN_ERROR_MARKERS = listOf(
            "service temporarily unavailable",
            "502 bad gateway",
            "504 gateway time-out",
            "504 gateway timeout",
            "web server is down",
            "origin is unreachable",
            "<center>nginx</center>",
            "internal server error",
        )
    }
}

/**
 * Thrown when the network layer detects a Cloudflare challenge / block that
 * was not (or could not be) automatically solved. The UI is expected to
 * surface a dedicated state telling the user to open the source in a WebView
 * so they can solve the challenge interactively; the resulting `cf_clearance`
 * cookie is shared via [WebViewCookieJar] and the next request succeeds.
 *
 * Extends [IOException] so existing `runCatching` / `catch (e: IOException)`
 * paths continue to work — branch on the type when a dedicated message is
 * desired.
 */
open class CloudflareException(val url: String, message: String) : IOException(message) {
    constructor(url: String) : this(url, "Cloudflare challenge detected for $url")
}

class CloudflareBypassException(url: String) :
    CloudflareException(url, "Failed to bypass Cloudflare protection for $url")
