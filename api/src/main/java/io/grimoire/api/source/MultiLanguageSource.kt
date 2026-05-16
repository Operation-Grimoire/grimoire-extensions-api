package io.grimoire.api.source

/**
 * A [Source] that serves content in many languages (it typically declares
 * `lang = "all"`). The host renders a built-in per-source "Content languages"
 * picker from [availableLanguages] and pushes the user's selection back via
 * [setEnabledLanguages]; the source then restricts popular/latest/search to
 * those languages — server-side when the site supports it, otherwise by
 * dropping non-matching results.
 *
 * Languages are plain English names (e.g. "English", "Spanish"). An empty
 * enabled set means "no filter — return every language". [setEnabledLanguages]
 * must be cheap and side-effect free (just store the set); it may be called
 * before the source is used and again whenever the user changes the selection.
 */
interface MultiLanguageSource : Source {
    fun availableLanguages(): List<String>

    fun setEnabledLanguages(languages: Set<String>)
}
