package io.grimoire.api.source

import io.grimoire.api.model.Filter
import io.grimoire.api.model.Novel

interface CatalogueSource : Source {
    suspend fun getPopularNovels(page: Int): List<Novel>
    suspend fun getLatestUpdates(page: Int): List<Novel>
    suspend fun searchNovels(query: String, page: Int, filters: List<Filter<*>>): List<Novel>
    fun getFilterList(): List<Filter<*>>
}
