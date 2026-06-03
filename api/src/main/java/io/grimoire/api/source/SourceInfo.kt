package io.grimoire.api.source

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SourceInfo(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val versionCode: Int = 1,
    /**
     * NovelUpdates release-group names this source corresponds to, if any.
     *
     * A source whose site is itself listed as a translation group/publisher on
     * NovelUpdates (e.g. Webnovel) declares the group name(s) here. The value is
     * picked up by `scripts/generate_index.py` and surfaced in `index.json`, so
     * the app can tell — without installing the extension — that a series'
     * release group is available as a source. Names are matched
     * case-insensitively against the group strings scraped from NovelUpdates.
     *
     * Defaulted to empty, so adding it is append-only / binary-compatible with
     * already-built extensions.
     */
    val novelUpdatesGroups: Array<String> = [],
)
