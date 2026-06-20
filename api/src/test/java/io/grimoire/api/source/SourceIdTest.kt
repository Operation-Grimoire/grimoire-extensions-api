package io.grimoire.api.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceIdTest {

    @Test
    fun `is stable for the same package`() {
        assertEquals(
            sourceIdFor("io.grimoire.extension.en.foxaholic18"),
            sourceIdFor("io.grimoire.extension.en.foxaholic18"),
        )
    }

    @Test
    fun `is always non-negative`() {
        listOf(
            "io.grimoire.extension.all.libgen",
            "io.grimoire.extension.en.foxaholic18",
            "io.grimoire.extension.en.royalroad",
            "",
        ).forEach { assertTrue(sourceIdFor(it) >= 0L, "id for '$it' must be non-negative") }
    }

    @Test
    fun `distinct packages get distinct ids`() {
        // The exact pair that collided under hand-assigned ids (both declared 9L).
        assertNotEquals(
            sourceIdFor("io.grimoire.extension.all.libgen"),
            sourceIdFor("io.grimoire.extension.en.foxaholic18"),
        )
    }

    @Test
    fun `all known extension packages are collision-free`() {
        val packages = listOf(
            "io.grimoire.extension.en.novelfull",
            "io.grimoire.extension.en.novelbuddy",
            "io.grimoire.extension.en.allnovel",
            "io.grimoire.extension.en.novgo",
            "io.grimoire.extension.en.foxaholic",
            "io.grimoire.extension.en.zlibrary",
            "io.grimoire.extension.en.lightnovelstranslations",
            "io.grimoire.extension.en.webnovel",
            "io.grimoire.extension.all.libgen",
            "io.grimoire.extension.en.azurechronicles",
            "io.grimoire.extension.en.caleredhair",
            "io.grimoire.extension.en.royalroad",
            "io.grimoire.extension.en.foxaholic18",
        )
        val ids = packages.map(::sourceIdFor)
        assertEquals(packages.size, ids.toSet().size, "two packages hashed to the same id")
    }
}
