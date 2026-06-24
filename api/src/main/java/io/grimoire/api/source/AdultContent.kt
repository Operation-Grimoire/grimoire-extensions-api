package io.grimoire.api.source

/**
 * How much adult (R18) content a source serves. Declared via [SourceInfo] and
 * surfaced in the extension index so the host can label / filter sources before
 * they're installed.
 */
enum class AdultContent(val displayName: String) {
    /** No adult content. */
    NONE("None"),

    /** A mix — some adult content, some not. */
    PARTIAL("Partial"),

    /** Entirely adult content. */
    FULL("Full"),
}
