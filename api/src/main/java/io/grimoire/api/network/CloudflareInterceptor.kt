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

        if (!response.isSourceBrowserChallenge()) {
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

        // Trust but verify: a stale/rejected cf_clearance can make the solve
        // look successful while the edge still answers with a challenge. If
        // the retry is challenged too, fail properly instead of handing the
        // challenge HTML to the caller as content (it parses as an empty
        // page and the failure becomes invisible).
        val retried = chain.proceed(request)
        if (retried.isSourceBrowserChallenge()) {
            retried.close()
            recentFailures[host] = System.currentTimeMillis()
            throw CloudflareBypassException(urlString)
        }

        recentFailures.remove(host)
        return retried
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(url: String): Boolean {
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null

        // The request that got us here was challenged, so any cf_clearance
        // already in the cookie store is stale or rejected. "Solved" means a
        // *different* clearance value appears — merely seeing the old cookie
        // again must not count, or the solve returns instantly without having
        // done anything.
        val staleClearance = clearanceValue(url)

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
                    if (hasFreshClearance(url, staleClearance)) {
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
            latch.await(TIMEOUT_SECONDS + 2, TimeUnit.SECONDS) && hasFreshClearance(url, staleClearance)
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

    /** Current cf_clearance cookie value for [url], or null when absent. */
    private fun clearanceValue(url: String): String? =
        android.webkit.CookieManager.getInstance().getCookie(url)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$CLEARANCE_COOKIE=") }
            ?.substringAfter('=')

    /** True when a clearance exists that differs from the pre-solve [stale] value. */
    private fun hasFreshClearance(url: String, stale: String?): Boolean {
        val current = clearanceValue(url) ?: return false
        return current != stale
    }

    private fun cookieCount(url: String): Int =
        android.webkit.CookieManager.getInstance().getCookie(url)
            ?.split(';')?.count { it.isNotBlank() } ?: 0


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
    }
}
