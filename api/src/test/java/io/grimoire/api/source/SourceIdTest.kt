package io.grimoire.api.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceIdTest {

    @Test
    fun `is stable for the same package`() {
        assertEquals(sourceIdFor("io.example.source.foo"), sourceIdFor("io.example.source.foo"))
    }

    @Test
    fun `is always non-negative`() {
        listOf("io.example.source.foo", "a", "").forEach {
            assertTrue(sourceIdFor(it) >= 0L, "id for '$it' must be non-negative")
        }
    }

    @Test
    fun `distinct packages get distinct ids`() {
        assertNotEquals(sourceIdFor("io.example.source.foo"), sourceIdFor("io.example.source.bar"))
    }

    @Test
    fun `large set of distinct packages is collision-free`() {
        val packages = (0 until 10_000).map { "io.example.source.s$it" }
        assertEquals(packages.size, packages.map(::sourceIdFor).toSet().size, "two packages hashed to the same id")
    }
}
