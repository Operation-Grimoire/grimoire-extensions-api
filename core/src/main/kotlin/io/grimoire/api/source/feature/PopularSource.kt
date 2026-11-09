package io.grimoire.api.source.feature

import io.grimoire.api.source.Source

import io.grimoire.api.model.novel.Novel

/** A [Source] that exposes a browseable list of popular novels. */
interface PopularSource : Source {
    suspend fun getPopularNovels(page: Int): List<Novel>
}
