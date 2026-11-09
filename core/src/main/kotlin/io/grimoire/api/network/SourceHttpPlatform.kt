package io.grimoire.api.network

import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.ServiceLoader

/** Host-owned browser/session integration. Implementations must outlive loaded sources. */
interface SourceHttpPlatform {
    val cookieJar: CookieJar
    val userAgent: String
    val interceptors: List<Interceptor> get() = emptyList()
}

/** One platform and one source HTTP pool per process. Configure before loading extensions. */
object SourceNetwork {
    private val lock = Any()
    private var configured: SourceHttpPlatform? = null
    private var shared: OkHttpClient? = null

    fun install(platform: SourceHttpPlatform) = synchronized(lock) {
        check(configured == null || configured === platform) {
            "Source networking is already initialized; keep the platform for the process lifetime"
        }
        configured = platform
    }

    internal fun platform(): SourceHttpPlatform = synchronized(lock) {
        configured ?: run {
            // Use the host API loader, not an extension's context loader.
            val providers = ServiceLoader.load(SourceHttpPlatform::class.java, SourceHttpPlatform::class.java.classLoader).iterator()
            check(providers.hasNext()) { "Install a SourceHttpPlatform before loading extensions" }
            val platform = providers.next()
            check(!providers.hasNext()) { "Multiple SourceHttpPlatform providers; install one explicitly" }
            configured = platform
            platform
        }
    }

    internal fun client(): OkHttpClient = synchronized(lock) {
        shared ?: buildSourceHttpClient(platform()).also { shared = it }
    }
}
