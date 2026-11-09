package io.grimoire.api.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SourceNetworkTest {
    private class BrowserJar : CookieJar {
        private val cookies = linkedMapOf<String, Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies["${it.name}:${it.domain}:${it.path}"] = it }
        }
        override fun loadForRequest(url: HttpUrl) = cookies.values
            .filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
    }

    private class Platform(
        override val cookieJar: CookieJar = BrowserJar(),
        override val interceptors: List<Interceptor> = emptyList(),
    ) : SourceHttpPlatform {
        override var userAgent = "HostBrowser/1"
    }

    @Test
    fun `host supplies user agent and explicit request headers survive`() {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setBody("chapter")) }
            val platform = Platform()
            val client = buildSourceHttpClient(platform)
            client.newCall(Request.Builder().url(server.url("/chapter")).build()).execute().close()
            assertEquals("HostBrowser/1", server.takeRequest().getHeader("User-Agent"))
            platform.userAgent = "HostBrowser/2"
            client.newCall(Request.Builder().url(server.url("/chapter")).build()).execute().close()
            assertEquals("HostBrowser/2", server.takeRequest().getHeader("User-Agent"))
            client.newCall(Request.Builder().url(server.url("/chapter")).header("User-Agent", "SourceOverride/1").build()).execute().close()
            assertEquals("SourceOverride/1", server.takeRequest().getHeader("User-Agent"))
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `browser cookie jar receives responses and applies path and secure scope`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().addHeader("Set-Cookie", "session=test; Path=/private; HttpOnly")
                .addHeader("Set-Cookie", "secure_only=test; Path=/; Secure"))
            repeat(3) { server.enqueue(MockResponse().setBody("chapter")) }
            val jar = BrowserJar()
            val client = buildSourceHttpClient(Platform(jar))
            client.newCall(Request.Builder().url(server.url("/login")).build()).execute().close()
            server.takeRequest()
            client.newCall(Request.Builder().url(server.url("/private/chapter")).build()).execute().close()
            assertEquals("session=test", server.takeRequest().getHeader("Cookie"))
            client.newCall(Request.Builder().url(server.url("/public")).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Cookie"))
            jar.saveFromResponse(server.url("/"), listOf(Cookie.Builder().name("browser_login").value("test")
                .hostOnlyDomain(server.hostName).path("/private").httpOnly().build()))
            client.newCall(Request.Builder().url(server.url("/private/chapter")).build()).execute().close()
            assertTrue(server.takeRequest().getHeader("Cookie")!!.contains("browser_login=test"))
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `platform challenge retry reuses cookies and consistent browser user agent`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).addHeader("Set-Cookie", "clearance=test; Path=/"))
            server.enqueue(MockResponse().setBody("chapter"))
            val retry = Interceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 403) { response.close(); chain.proceed(chain.request()) } else response
            }
            val client = buildSourceHttpClient(Platform(interceptors = listOf(retry)))
            client.newCall(Request.Builder().url(server.url("/chapter")).build()).execute().use {
                assertEquals("chapter", it.body!!.string())
            }
            assertEquals("HostBrowser/1", server.takeRequest().getHeader("User-Agent"))
            val second = server.takeRequest()
            assertEquals("HostBrowser/1", second.getHeader("User-Agent"))
            assertEquals("clearance=test", second.getHeader("Cookie"))
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `missing JVM platform fails clearly and installed client retains one pool`() {
        assertThrows(IllegalStateException::class.java) { defaultOkHttpClient() }
        val platform = Platform()
        SourceNetwork.install(platform)
        SourceNetwork.install(platform)
        val first = defaultOkHttpClient()
        assertSame(first, defaultOkHttpClient())
        val derived = first.newBuilder().build()
        assertSame(first.connectionPool, derived.connectionPool)
        assertSame(first.dispatcher, derived.dispatcher)
        assertEquals(30_000, first.connectTimeoutMillis)
        assertThrows(IllegalStateException::class.java) { SourceNetwork.install(Platform()) }
        assertSame(first, defaultOkHttpClient())
    }
}
