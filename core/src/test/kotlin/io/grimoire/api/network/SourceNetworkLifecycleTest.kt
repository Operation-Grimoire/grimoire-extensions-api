package io.grimoire.api.network

import okhttp3.CookieJar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Runs alone in a worker JVM; the production singleton has no reset API. */
class SourceNetworkLifecycleTest {
    private fun platform() = object : SourceHttpPlatform {
        override val cookieJar = CookieJar.NO_COOKIES
        override val userAgent = "LifecycleTest/1"
    }

    @Test
    fun `missing JVM platform fails clearly and installed client retains one pool`() {
        assertThrows(IllegalStateException::class.java) { defaultOkHttpClient() }
        val owner = platform()
        SourceNetwork.install(owner)
        SourceNetwork.install(owner)
        val first = defaultOkHttpClient()
        assertSame(first, defaultOkHttpClient())
        val derived = first.newBuilder().build()
        assertSame(first.connectionPool, derived.connectionPool)
        assertSame(first.dispatcher, derived.dispatcher)
        assertEquals(30_000, first.connectTimeoutMillis)
        assertThrows(IllegalStateException::class.java) { SourceNetwork.install(platform()) }
        assertSame(first, defaultOkHttpClient())
    }
}
