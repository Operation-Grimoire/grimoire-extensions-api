package io.grimoire.api.source.feature

import io.grimoire.api.source.Source

import io.grimoire.api.model.novel.Novel

/** A [Source] that exposes a browseable list of latest-updated novels. */
interface LatestSource : Source {
    suspend fun getLatestUpdates(page: Int): List<Novel>
}
