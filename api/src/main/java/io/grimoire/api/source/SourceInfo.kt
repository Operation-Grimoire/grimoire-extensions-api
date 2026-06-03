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
     * NovelUpdates release-group identifiers this source corresponds to, if any.
     *
     * A source whose site is itself listed as a translation group/publisher on
     * NovelUpdates (e.g. Webnovel) declares it here. Each entry may be either the
     * group's URL **slug** (the stable `novelupdates.com/group/<slug>/` id, which
     * survives display-name renames — preferred) or its **display name**; the app
     * matches an entry case-insensitively against both the slug parsed from a
     * release's group link and the shown group/publisher name. Declaring the slug
     * is the robust choice for multi-word groups whose displayed name differs from
     * the hyphenated slug.
     *
     * The value is picked up by `scripts/generate_index.py` and surfaced in
     * `index.json`, so the app can tell — without installing the extension — that
     * a series' release group is available as a source.
     *
     * Defaulted to empty, so adding it is append-only / binary-compatible with
     * already-built extensions.
     */
    val novelUpdatesGroups: Array<String> = [],
)
