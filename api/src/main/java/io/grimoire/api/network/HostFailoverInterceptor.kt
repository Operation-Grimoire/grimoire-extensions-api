package io.grimoire.api.network

import io.grimoire.api.source.feature.MultiHostSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared mirror failover for a [MultiHostSource]. Sits on the source's OkHttp
 * client and transparently routes *rotatable* requests across the source's
 * [MultiHostSource.hosts], so individual extensions don't each hand-roll the
 * same logic.
 *
 * Policy:
 * - Only requests whose host is one of the source's mirrors **and** that pass
 *   [rotatable] fail over. Host-bound traffic (downloads, login, per-user files)
 *   declares itself non-rotatable and is left on its own host.
 * - The order is `[activeHost] + hosts`, so the user's selected mirror is tried
 *   first; [MultiHostSource.activeHost] is never mutated (the selection stands).
 * - Only a connection-level [IOException] (host unreachable: DNS failure,
 *   refused, timeout) advances to the next mirror. An HTTP response of any
 *   status is returned as-is, so a Cloudflare 503 is left for
 *   [CloudflareInterceptor] to solve in place rather than being bounced to
 *   another (equally challenged) mirror.
 * - A host that throws is put in a [cooldownMs] cooldown and skipped while it
 *   cools, so the request *after* a failover goes straight to the live mirror
 *   instead of re-paying the dead host's timeout every call. After the TTL the
 *   host is re-probed, restoring the user's selection automatically once it
 *   recovers. If every host is cooling, the full list is still tried (cooldown
 *   only reorders, never strands).
 * - [accept] lets a source reject a reachable-but-useless response (e.g. a
 *   200-but-empty listing from a flaky mirror) and move to the next host without
 *   cooling the current one. The default accepts every response.
 * - When [addReferer] is set, a same-host `Referer` is added to every request to
 *   a known mirror (some mirrors require it); off by default.
 */
class HostFailoverInterceptor(
    private val source: MultiHostSource,
    private val rotatable: (HttpUrl) -> Boolean = { true },
    private val cooldownMs: Long = 90_000L,
    private val addReferer: Boolean = false,
    private val accept: (Response) -> Boolean = { true },
) : Interceptor {

    // host (authority only) -> millis the cooldown expires.
    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val mirrors = source.hosts.mapNotNull { it.toHttpUrlOrNull() }
        val knownHosts = mirrors.map { it.host }.toSet()

        // Unknown host (e.g. a CDN cover/file host) — leave entirely alone.
        if (original.url.host !in knownHosts) return chain.proceed(original)

        // Known host but host-bound (download/login) — no rotation, just the
        // optional same-host Referer.
        if (!rotatable(original.url)) return chain.proceed(original.withRefererIfEnabled())

        val ordered = orderedHosts(mirrors)
        var lastError: IOException? = null
        // A reachable response that [accept] rejected — kept so that if no host
        // produces an accepted one, we still return a real response (e.g. a
        // genuinely empty listing) instead of failing the request.
        var rejected: Response? = null
        for (host in ordered) {
            val request = original.rewrittenTo(host).withRefererIfEnabled()
            try {
                val response = chain.proceed(request)
                if (accept(response)) {
                    rejected?.close()
                    return response
                }
                // Reachable but unusable (e.g. empty listing): try the next
                // mirror without cooling this one — it's up, just unhelpful.
                rejected?.close()
                rejected = response
                lastError = IOException("rejected response from ${host.host}")
            } catch (e: IOException) {
                cooldownUntil[host.host] = System.currentTimeMillis() + cooldownMs
                lastError = e
            }
        }
        rejected?.let { return it }
        throw lastError ?: IOException("All mirrors are unreachable")
    }

    // Active host first, then the rest; hosts in cooldown sink to the end so
    // they're a last resort but never fully stranded.
    private fun orderedHosts(mirrors: List<HttpUrl>): List<HttpUrl> {
        val active = source.activeHost.toHttpUrlOrNull()
        val all = (listOfNotNull(active) + mirrors).distinctBy { it.host }
        val now = System.currentTimeMillis()
        val (cooling, live) = all.partition { (cooldownUntil[it.host] ?: 0L) > now }
        return live + cooling
    }

    private fun Request.rewrittenTo(host: HttpUrl): Request {
        if (url.host == host.host && url.scheme == host.scheme) return this
        return newBuilder()
            .url(url.newBuilder().scheme(host.scheme).host(host.host).build())
            .build()
    }

    private fun Request.withRefererIfEnabled(): Request {
        if (!addReferer || header("Referer") != null) return this
        return newBuilder().header("Referer", "${url.scheme}://${url.host}/").build()
    }
}

/**
 * Convenience builder: the shared [defaultOkHttpClient] with a
 * [HostFailoverInterceptor] for this source, so an extension's `client` override
 * is a single line. See [HostFailoverInterceptor] for the parameters.
 */
fun MultiHostSource.failoverClient(
    rotatable: (HttpUrl) -> Boolean = { true },
    cooldownMs: Long = 90_000L,
    addReferer: Boolean = false,
    accept: (Response) -> Boolean = { true },
): OkHttpClient = defaultOkHttpClient().newBuilder()
    .addInterceptor(HostFailoverInterceptor(this, rotatable, cooldownMs, addReferer, accept))
    .build()
