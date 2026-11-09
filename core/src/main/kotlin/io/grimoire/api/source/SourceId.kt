package io.grimoire.api.source

import java.security.MessageDigest

/**
 * Stable source identity derived from the extension's Android package name, which
 * is globally unique — so authors never hand-pick or coordinate ids, and repos
 * can't collide. The app keys saved novels by this.
 *
 * Low 63 bits of MD5(package) — a fast stable hash (not for security).
 */
fun sourceIdFor(packageName: String): Long {
    val bytes = MessageDigest.getInstance("MD5").digest(packageName.toByteArray(Charsets.UTF_8))
    var id = 0L
    for (i in 0 until 8) {
        id = id or ((bytes[i].toLong() and 0xff) shl (8 * i))
    }
    return id and Long.MAX_VALUE
}
