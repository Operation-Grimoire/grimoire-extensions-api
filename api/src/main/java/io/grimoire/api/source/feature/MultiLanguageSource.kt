package io.grimoire.api.source.feature

import io.grimoire.api.model.lang.Language
import io.grimoire.api.source.Source

/**
 * A [Source] that serves content in many languages (it typically declares
 * `lang = "all"`). The host renders a built-in per-source "Content languages"
 * picker from [availableLanguages] and pushes the user's selection back via
 * [setEnabledLanguages]; the source then restricts popular/latest/search to
 * those languages — server-side when the site supports it, otherwise by
 * dropping non-matching results.
 *
 * Languages are [Language] values. An empty enabled set means "no filter —
 * return every language". [setEnabledLanguages]
 * must be cheap and side-effect free (just store the set); it may be called
 * before the source is used and again whenever the user changes the selection.
 */
interface MultiLanguageSource : Source {
    /**
     * The languages this source offers. May fetch the list from the site (e.g.
     * scraped from a language menu); sources that know their languages statically
     * just return a constant. Must run off the main thread and should cache the
     * result so repeated calls don't refetch.
     */
    suspend fun availableLanguages(): List<Language>

    fun setEnabledLanguages(languages: Set<Language>)
}
