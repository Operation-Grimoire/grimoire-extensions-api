package io.grimoire.api.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation

/** Renders one page in the app WebView and lets the source accept, reject, or keep waiting. */
internal class AndroidDocumentBrowser : SourceDocumentBrowser {
    override val ready: Boolean
        get() = NetworkContext.context != null

    override suspend fun read(
        url: String,
        script: String,
        timeoutMillis: Long,
        decide: (SourceBrowserReading) -> SourceDocumentDecision,
    ): SourceBrowserReading {
        if (!ready) throw CloudflareBypassException(url)
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    val context = NetworkContext.context
                    if (context == null) {
                        continuation.resumeWithException(CloudflareBypassException(url))
                        return@post
                    }
                    open(context, url, script, handler, continuation, decide)
                }
            }
        } ?: throw CloudflareBypassException(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun open(
        context: Context,
        url: String,
        script: String,
        handler: Handler,
        continuation: CancellableContinuation<SourceBrowserReading>,
        decide: (SourceBrowserReading) -> SourceDocumentDecision,
    ) {
        val view = WebView(context)
        var status = 200
        var closed = false
        fun close() {
            if (closed) return
            closed = true
            handler.removeCallbacksAndMessages(null)
            view.stopLoading()
            view.destroy()
        }
        continuation.invokeOnCancellation { handler.post { close() } }
        fun fail(error: Exception) {
            if (!continuation.isActive) return
            close()
            continuation.resumeWithException(error)
        }
        fun consider() {
            if (!continuation.isActive) return
            view.evaluateJavascript(script) { value ->
                if (!continuation.isActive) return@evaluateJavascript
                val reading = SourceBrowserReading(url, status, value.orEmpty())
                try {
                    when (val decision = decide(reading)) {
                        SourceDocumentDecision.Accept -> {
                            close()
                            continuation.resume(reading)
                        }
                        is SourceDocumentDecision.Reject -> fail(decision.error)
                        SourceDocumentDecision.Wait -> handler.postDelayed({ consider() }, 500)
                    }
                } catch (error: Exception) {
                    fail(error as? IOException ?: IOException("Invalid browser response", error))
                }
            }
        }
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !sameOrigin(request.url.toString(), url)

            override fun onPageStarted(view: WebView, target: String?, favicon: Bitmap?) {
                status = 200
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                response: WebResourceResponse,
            ) {
                if (request.isForMainFrame) status = response.statusCode
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) fail(IOException("Browser network error ${error.errorCode}"))
            }

            override fun onPageFinished(view: WebView, target: String?) {
                if (target != null && target != "about:blank") consider()
            }
        }
        view.loadUrl(url)
    }

    private fun sameOrigin(value: String, requested: String): Boolean = runCatching {
        val target = value.toHttpUrl()
        val origin = requested.toHttpUrl()
        target.isHttps && target.host == origin.host && target.port == origin.port
    }.getOrDefault(false)
}
