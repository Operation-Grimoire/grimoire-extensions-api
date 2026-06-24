package io.grimoire.api.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Default client used by every [HttpSource].
 *
 * It shares cookies with the system WebView ([WebViewCookieJar]), sends a
 * consistent User-Agent ([UserAgentInterceptor]), and transparently solves
 * Cloudflare challenges ([CloudflareInterceptor]). The Cloudflare interceptor
 * is added as an application interceptor so a solved request is retried once
 * after clearance is obtained.
 *
 * ## One client, one connection pool — on purpose
 *
 * This returns a single process-wide instance rather than building a fresh
 * client per call. Every `new OkHttpClient` owns its own [ConnectionPool] and
 * dispatcher; each pooled HTTP/2 connection keeps a live reader running on the
 * shared `OkHttp TaskRunner` and may buffer up to OkHttp's 16 MiB receive
 * window. Sources are re-instantiated on every extension rescan (which the app
 * triggers often — returning to the extensions screen, opening browse, toggling
 * a source), and each [HttpSource] grabs its client field on construction. A
 * per-call client therefore minted a new pool on every rescan; the used ones
 * kept their connections (and buffers) alive until the idle timeout, so heavy
 * navigation piled up pools faster than they expired and eventually OOM'd the
 * reader thread mid-read.
 *
 * Sharing one instance bounds that to a single pool. It is safe because every
 * collaborator is stateless or process-global: [UserAgentInterceptor] and
 * [CloudflareInterceptor] hold no per-request state, and [WebViewCookieJar]
 * delegates to the system [android.webkit.CookieManager] singleton. Sources that
 * need to customise the client must derive from this via [OkHttpClient.newBuilder]
 * (as the host app and e.g. the LibGen extension do) so the derived client keeps
 * sharing this pool and dispatcher instead of allocating its own.
 */
fun defaultOkHttpClient(): OkHttpClient = sharedClient

private val sharedClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Cap idle sockets explicitly so the shared pool's memory ceiling is
        // visible at a glance; the value matches OkHttp's own default.
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .cookieJar(WebViewCookieJar())
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(CloudflareInterceptor())
        .build()
}
