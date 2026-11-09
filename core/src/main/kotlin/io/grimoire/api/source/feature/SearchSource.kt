package io.grimoire.api.source.feature

import io.grimoire.api.source.Source

import io.grimoire.api.model.filter.Filter
import io.grimoire.api.model.novel.Novel

/** A [Source] that supports free-text / filtered search. */
interface SearchSource : Source {
    suspend fun searchNovels(query: String, page: Int, filters: List<Filter<*>>): List<Novel>

    /**
     * `true` when this source's search endpoint accepts a free-text query AND
     * filters together. Defaults to `false` — most scraped sites expose either
     * `/search?q=...` or `/genre/<name>` but not both. The host UI uses this to
     * decide whether to surface a search field inside the filter sheet.
     */
    val supportsSearchWithFilters: Boolean get() = false
}
