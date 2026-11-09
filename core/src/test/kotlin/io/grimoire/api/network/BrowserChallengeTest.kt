package io.grimoire.api.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BrowserChallengeTest {
    private fun response(code: Int, body: String, vararg headers: Pair<String, String>): Response =
        Response.Builder().request(Request.Builder().url("https://example.com/").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody())
            .apply { headers.forEach { (key, value) -> addHeader(key, value) } }.build()

    @Test fun `recognizes challenge headers and scripts without consuming content`() {
        listOf(
            response(403, "blocked", "cf-mitigated" to "CHALLENGE"),
            response(503, "<script>_cf_chl_opt</script>"),
        ).forEach { it.use { r -> assertTrue(r.isSourceBrowserChallenge()); assertFalse(r.body!!.string().isEmpty()) } }
    }

    @Test fun `successful content and ordinary origin failures are not challenges`() {
        listOf(
            response(200, "just a moment"),
            response(403, "permission denied"),
            response(503, "Service Temporarily Unavailable", "Content-Type" to "text/html", "Server" to "cloudflare"),
            response(503, "x".repeat(31 * 1024), "Content-Type" to "text/html", "Server" to "cloudflare"),
        ).forEach { it.use { r -> assertFalse(r.isSourceBrowserChallenge()) } }
    }

    @Test fun `ordinary short and empty Cloudflare HTML responses are not challenges`() {
        listOf(403, 503).forEach { code ->
            listOf("", "<html>Access denied</html>").forEach { body ->
                response(code, body, "Content-Type" to "text/html", "Server" to "cloudflare", "cf-ray" to "fixture").use {
                    assertFalse(it.isSourceBrowserChallenge())
                    assertEquals(body, it.body!!.string())
                }
            }
        }
    }
}
