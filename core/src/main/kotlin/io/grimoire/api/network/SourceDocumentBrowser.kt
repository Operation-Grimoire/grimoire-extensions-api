package io.grimoire.api.network

import java.io.IOException

/**
 * One observation from the host browser. [javascript] is the string returned by
 * evaluating the source's script, still in the form the browser callback produced.
 */
data class SourceBrowserReading(
    val requestedUrl: String,
    val status: Int,
    val javascript: String,
)

/** What the source wants the host to do with the latest browser observation. */
sealed interface SourceDocumentDecision {
    data object Wait : SourceDocumentDecision
    data object Accept : SourceDocumentDecision
    data class Reject(val error: IOException) : SourceDocumentDecision
}

/**
 * Optional host capability for a source that has to render a page.
 * [ready] is false until the host can create a browser, for example before
 * Android `NetworkContext.init`.
 */
interface SourceDocumentBrowser {
    val ready: Boolean

    suspend fun read(
        url: String,
        script: String,
        timeoutMillis: Long,
        decide: (SourceBrowserReading) -> SourceDocumentDecision,
    ): SourceBrowserReading
}

/** Entry point extension code uses. Fails with [CloudflareBypassException] when no browser is ready. */
object SourceBrowser {
    val ready: Boolean
        get() = (SourceNetwork.platform() as? SourceDocumentBrowser)?.ready == true

    suspend fun read(
        url: String,
        script: String,
        timeoutMillis: Long = 30_000,
        decide: (SourceBrowserReading) -> SourceDocumentDecision,
    ): SourceBrowserReading {
        val browser = SourceNetwork.platform() as? SourceDocumentBrowser
        if (browser == null || !browser.ready) throw CloudflareBypassException(url)
        return browser.read(url, script, timeoutMillis, decide)
    }
}
