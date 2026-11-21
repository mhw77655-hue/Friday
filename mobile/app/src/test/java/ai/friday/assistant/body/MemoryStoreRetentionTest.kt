package ai.friday.assistant.body

import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryStoreRetentionTest {
    @Test
    fun `retention removes oldest entries by map key rather than value identity`() {
        val facts = ConcurrentHashMap<String, Long>()
        facts["owner-fact-key"] = 10L
        facts["second-fact-key"] = 20L
        facts["third-fact-key"] = 30L

        trimOldestEntries(facts, 2) { it }

        assertEquals(2, facts.size)
        assertEquals(setOf("second-fact-key", "third-fact-key"), facts.keys)
    }

    @Test
    fun `retention is a no-op below the configured bound`() {
        val preferences = ConcurrentHashMap<String, Long>()
        preferences["preferred-language"] = 10L
        preferences["preferred-tone"] = 20L

        trimOldestEntries(preferences, 2) { it }

        assertEquals(mapOf("preferred-language" to 10L, "preferred-tone" to 20L), preferences)
    }
}
