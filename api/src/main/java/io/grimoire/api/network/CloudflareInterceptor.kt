package io.grimoire.api.network

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
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

        // A challenge that just defeated the headless solve will defeat it
        // again: it needs an interactive solve in a real WebView. Fail every
        // follow-up request for the host fast instead of stalling each one for
        // another TIMEOUT_SECONDS, so the UI's WebView CTA appears immediately.
        val host = request.url.host
        val failedAt = recentFailures[host]
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_COOLDOWN_MS) {
            throw CloudflareBypassException(urlString)
        }

        val solved = runCatching {
            resolveWithWebView(urlString)
        }.getOrDefault(false)

        if (!solved) {
            recentFailures[host] = System.currentTimeMillis()
            throw CloudflareBypassException(urlString)
        }

        recentFailures.remove(host)
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
            view.webViewClient = WebViewClient()
            view.loadUrl(url)

            // Poll the cookie store directly instead of waiting on page-load
            // events. Cloudflare commonly issues cf_clearance from the
            // challenge's own XHR/redirect without firing a fresh onPageFinished,
            // so gating on that event can hang until the timeout even though
            // clearance landed in a second or two. Polling returns the instant
            // clearance appears. Once cleared, a short grace lets the site's own
            // first-party session cookies follow (some gate downloads on a
            // JS-issued token) — we stop as soon as the cookie set grows past
            // clearance, or the grace elapses. [TIMEOUT_SECONDS] is only the
            // give-up cap for a challenge that never auto-solves.
            val deadline = System.currentTimeMillis() + TIMEOUT_SECONDS * 1000L
            val poll = object : Runnable {
                private var clearedAt = 0L
                private var baseline = 0
                override fun run() {
                    val now = System.currentTimeMillis()
                    if (hasClearance(url)) {
                        if (clearedAt == 0L) {
                            clearedAt = now
                            baseline = cookieCount(url)
                        }
                        if (cookieCount(url) > baseline || now - clearedAt >= SETTLE_MS) {
                            latch.countDown()
                            return
                        }
                    }
                    if (now >= deadline) {
                        latch.countDown()
                        return
                    }
                    handler.postDelayed(this, POLL_MS)
                }
            }
            handler.postDelayed(poll, POLL_MS)
        }

        val obtained = try {
            // Buffer over the poll's own deadline so the poll wins the race and
            // reports clearance, rather than this await expiring first.
            latch.await(TIMEOUT_SECONDS + 2, TimeUnit.SECONDS) && hasClearance(url)
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

        // Definitive header signal — Cloudflare tags challenges with this
        // regardless of the (sometimes absent) Server / cf-ray headers.
        if (header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true) return true

        val snippet = runCatching {
            peekBody(BODY_PEEK_BYTES).string()
        }.getOrNull().orEmpty()

        // An origin/edge 5xx (nginx "503 Service Temporarily Unavailable", a 502
        // bad gateway, Cloudflare's own 52x "web server is down") is a plain
        // server error, not a solvable challenge — veto it before the positive
        // body checks (it has no challenge JS to run).
        if (ORIGIN_ERROR_MARKERS.any { snippet.contains(it, ignoreCase = true) }) return false

        // The challenge shell carries these scripts/phrases. Detect on the body
        // alone — do NOT require Server: cloudflare or a cf-ray: some mirrors
        // front a Cloudflare-style "Just a moment" interstitial without
        // surfacing those headers, and gating on them silently mis-parses the
        // challenge HTML as empty content instead of solving / surfacing it.
        if (CHALLENGE_MARKERS.any { snippet.contains(it, ignoreCase = true) }) return true

        // Marker-less short HTML shell still attributable to Cloudflare by its
        // edge headers — treat as an interstitial (origin errors are vetoed
        // above and are typically larger).
        val isHtml = header("Content-Type")?.contains("text/html", ignoreCase = true) == true
        val fromCloudflareEdge =
            header("Server").orEmpty().contains("cloudflare", ignoreCase = true) ||
                header("cf-ray") != null
        return isHtml && fromCloudflareEdge && snippet.length < SHORT_HTML_BYTES
    }

    companion object {
        private const val CLEARANCE_COOKIE = "cf_clearance"

        // Cap the headless solve. An auto-solvable managed challenge lands its
        // cf_clearance cookie within a few seconds; if it hasn't cleared by this
        // point it almost certainly needs an interactive solve, so fail through
        // to a CloudflareException quickly (the UI then offers the WebView CTA)
        // instead of leaving the request — and its loading spinner — hanging.
        private const val TIMEOUT_SECONDS = 10L

        // How long a failed headless solve short-circuits further attempts for
        // the same host. Long enough to cover a burst of parallel/follow-up
        // requests, short enough to try again on the user's next real action.
        private const val FAILURE_COOLDOWN_MS = 60_000L

        private val recentFailures = ConcurrentHashMap<String, Long>()

        // After Cloudflare clearance, briefly keep polling so the site's own
        // scripts can set first-party session cookies (some gate content /
        // downloads on a JS-issued token). We return the moment the cookie set
        // grows past clearance; SETTLE_MS is only the ceiling when nothing else
        // follows, so keep it short to stay snappy.
        private const val SETTLE_MS = 2000L
        private const val POLL_MS = 200L
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
