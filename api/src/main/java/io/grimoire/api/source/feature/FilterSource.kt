package io.grimoire.api.source.feature

import io.grimoire.api.source.Source

import io.grimoire.api.model.filter.Filter

/**
 * A [Source] that exposes a filter list to refine browsing/search. Usually paired
 * with [SearchSource], whose `searchNovels` receives the applied filters.
 */
interface FilterSource : Source {
    /**
     * Returns the source's filter list. May contain placeholder entries (e.g. an
     * empty [Filter.Group]) when values must be populated via [fetchFilterOptions].
     */
    fun getFilterList(): List<Filter<*>>

    /**
     * `true` when this source requires a network call to populate filter values
     * (e.g. genre list scraped from the homepage). When `true` the UI must expose
     * a button to invoke [fetchFilterOptions] and block filtering until it succeeds.
     */
    val hasDynamicFilters: Boolean get() = false

    /**
     * Performs the network fetch that populates dynamic filter options and returns
     * the resulting filter list. Sources without dynamic filters fall back to
     * [getFilterList]. Must only be invoked off the main thread.
     */
    suspend fun fetchFilterOptions(): List<Filter<*>> = getFilterList()
}
