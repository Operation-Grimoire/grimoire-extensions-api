package io.grimoire.api.network

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostCookieStoreTest {
    @Test
    fun `host cookie store is preferred over the request jar`() {
        val store = RecordingStore()
        SourceNetwork.install(platform(store))
        SourceCookies.set("https://example.com/account", "uid=7; Path=/")
        assertEquals("https://example.com/account", store.writes.single().first)
        assertEquals("uid=7; Path=/", store.writes.single().second)
        assertEquals("uid=7", SourceCookies.header("https://example.com/account"))
        SourceCookies.flush()
        assertTrue(store.flushed)
    }

}

class JarCookieFallbackTest {
    @Test
    fun `jar fallback stores a session and drops an expired cookie`() {
        val jar = MemoryJar()
        SourceNetwork.install(platform(cookies = null, jar = jar))
        SourceCookies.set("https://example.com/", "session=1; Path=/")
        assertTrue(SourceCookies.header("https://example.com/private").contains("session=1"))
        SourceCookies.set("https://example.com/", "session=; Max-Age=0; Path=/")
        assertFalse(SourceCookies.header("https://example.com/").contains("session="))
    }

}

class DocumentBrowserReadTest {
    @Test
    fun `document read returns the observation the source accepts`() {
        SourceNetwork.install(object : SourceHttpPlatform, SourceDocumentBrowser {
            override val cookieJar = CookieJar.NO_COOKIES
            override val userAgent = "Host/1"
            override val ready = true
            override suspend fun read(
                url: String,
                script: String,
                timeoutMillis: Long,
                decide: (SourceBrowserReading) -> SourceDocumentDecision,
            ): SourceBrowserReading {
                assertEquals("https://galaxynovels.com/novel/", url)
                assertEquals("read-body", script)
                val challenge = SourceBrowserReading(url, 403, "challenge")
                assertEquals(SourceDocumentDecision.Wait, decide(challenge))
                val page = SourceBrowserReading(url, 200, "page")
                assertEquals(SourceDocumentDecision.Accept, decide(page))
                return page
            }
        })
        val reading = runBlocking {
            SourceBrowser.read("https://galaxynovels.com/novel/", "read-body") { snapshot ->
                if (snapshot.status == 200) SourceDocumentDecision.Accept else SourceDocumentDecision.Wait
            }
        }
        assertEquals(200, reading.status)
        assertEquals("page", reading.javascript)
    }

}

class DocumentBrowserMissingTest {
    @Test
    fun `document read fails while the host has no browser`() {
        SourceNetwork.install(platform(cookies = null))
        assertFalse(SourceBrowser.ready)
        val error = assertThrows(CloudflareBypassException::class.java) {
            runBlocking {
                SourceBrowser.read("https://example.com/book", "body") { SourceDocumentDecision.Accept }
            }
        }
        assertEquals("https://example.com/book", error.url)
    }
}

private fun platform(cookies: SourceCookieStore?, jar: CookieJar = CookieJar.NO_COOKIES): SourceHttpPlatform {
        if (cookies == null) {
            return object : SourceHttpPlatform {
                override val cookieJar = jar
                override val userAgent = "Host/1"
            }
        }
        val store = cookies
        return object : SourceHttpPlatform, SourceBrowserCookies {
            override val cookieJar = jar
            override val userAgent = "Host/1"
            override val cookies: SourceCookieStore = store
        }
    }

private class RecordingStore : SourceCookieStore {
        val writes = mutableListOf<Pair<String, String>>()
        var flushed = false
        private val headers = linkedMapOf<String, String>()

        override fun cookieHeader(url: HttpUrl): String = headers[url.toString()].orEmpty()

        override fun setCookie(url: HttpUrl, header: String) {
            writes += url.toString() to header
            headers[url.toString()] = header.substringBefore(';')
        }

        override fun flush() {
            flushed = true
        }
    }

private class MemoryJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.removeAll { existing ->
                cookies.any { it.name == existing.name && it.domain == existing.domain && it.path == existing.path }
            }
            this.cookies += cookies.filter { it.expiresAt > System.currentTimeMillis() }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
    }
