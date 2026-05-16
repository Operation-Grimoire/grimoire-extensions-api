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
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    if (hasClearance(url)) {
                        latch.countDown()
                    }
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

    private fun Response.isCloudflareChallenge(): Boolean {
        if (code != 403 && code != 503) return false
        val server = header("Server").orEmpty()
        if (!server.contains("cloudflare", ignoreCase = true)) return false
        if (header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true) return true

        val snippet = runCatching {
            peekBody(BODY_PEEK_BYTES).string()
        }.getOrNull().orEmpty()
        return CHALLENGE_MARKERS.any { snippet.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val CLEARANCE_COOKIE = "cf_clearance"
        private const val TIMEOUT_SECONDS = 60L
        private const val BODY_PEEK_BYTES = 128L * 1024L

        private val CHALLENGE_MARKERS = listOf(
            "_cf_chl_opt",
            "cf-browser-verification",
            "challenge-platform",
            "cf_chl_",
            "Just a moment...",
        )
    }
}

class CloudflareBypassException(url: String) :
    IOException("Failed to bypass Cloudflare protection for $url")
