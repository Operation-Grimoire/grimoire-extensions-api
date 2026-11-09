package io.grimoire.api.network

import java.io.IOException

/** The host could not complete the source's browser challenge. */
open class CloudflareException(val url: String, message: String) : IOException(message) {
    constructor(url: String) : this(url, "Cloudflare challenge detected for $url")
}

class CloudflareBypassException(url: String) :
    CloudflareException(url, "Failed to bypass Cloudflare protection for $url")
