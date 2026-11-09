package io.grimoire.api.source

import io.grimoire.api.model.lang.Language

/**
 * Compile-time metadata on a source class. Read statically (without loading the
 * extension's code) by `scripts/generate_index.py` to build the extension
 * `index.json`, and at runtime by the host via reflection. Every concrete source
 * class is annotated with this.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SourceInfo(
    /** Display name, matching the class's `name` property. */
    val name: String,
    /** Content language; [Language.MULTI] for multi-language sources. */
    val lang: Language,
    /** Site origin (scheme + host), e.g. `https://novelfull.com`. */
    val baseUrl: String,
    /** Bumped on every behavioural change so the CI rebuilds the extension. */
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

    /** How much adult (R18) content this source serves. Surfaced in the index. */
    val adultContent: AdultContent = AdultContent.NONE,
)
