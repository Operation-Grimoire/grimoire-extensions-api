package io.grimoire.api.network

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.CookieManagerCompat
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBrowserSessionTest {
    @Test fun productionProviderPreservesRedirectCookieAttributesBeforeAndAfterInitialization() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var browser: WebView
        instrumentation.runOnMainSync { browser = WebView(instrumentation.targetContext) }
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/reader")
                    .addHeader("Set-Cookie", "api_login=provider; Path=/; HttpOnly; SameSite=Strict; Max-Age=3600"))
                server.enqueue(MockResponse().setBody("chapter"))
                val client = defaultOkHttpClient()
                assertTrue(client.cookieJar is WebViewCookieJar)
                assertTrue(client.interceptors.any { it is CloudflareInterceptor })
                client.newCall(Request.Builder().url(server.url("/login")).build()).execute().use { assertEquals(200, it.code) }
                server.takeRequest()
                assertTrue(server.takeRequest().getHeader("Cookie")!!.contains("api_login=provider"))
                val metadata = CookieManagerCompat.getCookieInfo(CookieManager.getInstance(), server.url("/reader").toString())
                    .single { it.startsWith("api_login=") }
                assertTrue(metadata.contains("SameSite=Strict", ignoreCase = true))
                assertTrue(metadata.contains("HttpOnly", ignoreCase = true))
                NetworkContext.init(instrumentation.targetContext)
                assertSame(client, defaultOkHttpClient())
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        } finally { instrumentation.runOnMainSync { browser.destroy() } }
    }
}
