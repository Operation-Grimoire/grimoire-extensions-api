package io.grimoire.api.network

import okhttp3.Response

/** Shared detection only; the host decides whether to solve or report a browser challenge. */
fun Response.isSourceBrowserChallenge(): Boolean {
    if (code != 403 && code != 503) return false

    // Definitive header signal — Cloudflare tags challenges with this
    // regardless of the (sometimes absent) Server / cf-ray headers.
    if (header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true) return true

    val snippet = runCatching {
        peekBody(BODY_PEEK_BYTES).string()
    }.getOrNull().orEmpty()

    // An origin/edge 5xx (nginx "503 Service Temporarily Unavailable", a 502
    // bad gateway, Cloudflare's own 52x "web server is down") is a plain
    // server error, not a solvable challenge — veto it before the positive
    // body checks (it has no challenge JS to run).
    if (ORIGIN_ERROR_MARKERS.any { snippet.contains(it, ignoreCase = true) }) return false

    // The challenge shell carries these scripts/phrases. Detect on the body
    // alone — do NOT require Server: cloudflare or a cf-ray: some mirrors
    // front a Cloudflare-style "Just a moment" interstitial without
    // surfacing those headers, and gating on them silently mis-parses the
    // challenge HTML as empty content instead of solving / surfacing it.
    // Edge headers and small HTML bodies also occur on ordinary access-denied
    // and origin errors. Only challenge-specific evidence warrants a browser solve.
    return CHALLENGE_MARKERS.any { snippet.contains(it, ignoreCase = true) }
}

private const val BODY_PEEK_BYTES = 128L * 1024L

private val CHALLENGE_MARKERS = listOf(
    "_cf_chl_opt",
    "cf-browser-verification",
    "challenge-platform",
    "cf_chl_",
    "/cdn-cgi/challenge-platform",
    "just a moment",
    "checking your browser",
    "cf-spinner",
)

// Plain server/origin errors proxied through Cloudflare. Present in the
// body of a 5xx, absent from a genuine challenge shell — so they veto the
// short-HTML+cf-ray heuristic below.
private val ORIGIN_ERROR_MARKERS = listOf(
    "service temporarily unavailable",
    "502 bad gateway",
    "504 gateway time-out",
    "504 gateway timeout",
    "web server is down",
    "origin is unreachable",
    "<center>nginx</center>",
    "internal server error",
)
