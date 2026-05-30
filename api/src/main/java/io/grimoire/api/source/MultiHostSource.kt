package io.grimoire.api.source

/**
 * A [Source] whose traffic can be served from any of several interchangeable
 * mirror hosts (e.g. LibGen's `libgen.la` / `.vg` / `.bz` / `.gl`, which are
 * byte-for-byte equivalent).
 *
 * [hosts] is the ordered preference list, most-preferred first, as
 * scheme-qualified origins (`"https://libgen.la"`). The host app may pin the
 * mirror to route through via [setActiveHost] — for example after detecting an
 * outage, by rotating to the next entry in [hosts]. Sources should *also* fail
 * over internally so they stay resilient before the app coordinates anything;
 * [activeHost] reports the mirror currently in use (which a resilient source may
 * advance on its own when a request fails over).
 *
 * [setActiveHost] must be cheap and side-effect free (just store the value); it
 * may be called before the source is used and again whenever routing changes.
 * Passing a host that is not in [hosts] is allowed (the app may know a mirror
 * the source doesn't); passing a blank value resets to `hosts.first()`.
 */
interface MultiHostSource : Source {
    val hosts: List<String>

    val activeHost: String

    fun setActiveHost(host: String)
}
