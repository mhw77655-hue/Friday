package com.jarvis.app.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ReferenceStore: tracks recently-mentioned entities per conversation turn,
 * recency-ranked, with optional type filtering.
 */
class ReferenceStoreTest {

    private lateinit var store: ReferenceStore

    @Before
    fun setup() {
        store = ReferenceStore()
    }

    @Test
    fun `mostRecent returns null when store is empty`() {
        assertNull(store.mostRecent())
    }

    @Test
    fun `mostRecent returns the most recently recorded entity`() {
        val e1 = IntentInference.Entity("e1", "coffee", IntentInference.EntityType.OBJECT)
        val e2 = IntentInference.Entity("e2", "London", IntentInference.EntityType.LOCATION)

        store.recordMentions(1, listOf(e1))
        store.recordMentions(2, listOf(e2))

        val result = store.mostRecent()
        assertNotNull(result)
        assertEquals("London", result!!.name)
        assertEquals(IntentInference.EntityType.LOCATION, result.type)
    }

    @Test
    fun `mostRecent type filter excludes non-matching entities`() {
        val person = IntentInference.Entity("e1", "Alice", IntentInference.EntityType.PERSON)
        val location = IntentInference.Entity("e2", "London", IntentInference.EntityType.LOCATION)

        store.recordMentions(1, listOf(person))
        store.recordMentions(2, listOf(location))

        val result = store.mostRecent(IntentInference.EntityType.PERSON)
        assertNotNull(result)
        assertEquals("Alice", result!!.name)

        // LOCATION should not be returned when filtering for PERSON
        val locationResult = store.mostRecent(IntentInference.EntityType.LOCATION)
        assertNotNull(locationResult)
        assertEquals("London", locationResult!!.name)

        // Unknown type returns null
        assertNull(store.mostRecent(IntentInference.EntityType.TIME))
    }

    @Test
    fun `mostRecent returns correct entity across 3 turns of same type`() {
        val task1 = IntentInference.Entity("e1", "Research topic", IntentInference.EntityType.OBJECT)
        val task2 = IntentInference.Entity("e2", "Write outline", IntentInference.EntityType.OBJECT)
        val task3 = IntentInference.Entity("e3", "Draft report", IntentInference.EntityType.OBJECT)

        store.recordMentions(1, listOf(task1))
        store.recordMentions(2, listOf(task2))
        store.recordMentions(3, listOf(task3))

        // Most recent OBJECT should be task3
        val result = store.mostRecent(IntentInference.EntityType.OBJECT)
        assertNotNull(result)
        assertEquals("Draft report", result!!.name)
    }

    @Test
    fun `all returns entities in recency order`() {
        val e1 = IntentInference.Entity("e1", "A", IntentInference.EntityType.CONCEPT)
        val e2 = IntentInference.Entity("e2", "B", IntentInference.EntityType.CONCEPT)
        val e3 = IntentInference.Entity("e3", "C", IntentInference.EntityType.CONCEPT)

        store.recordMentions(1, listOf(e1))
        store.recordMentions(2, listOf(e2))
        store.recordMentions(3, listOf(e3))

        val all = store.all()
        assertEquals(3, all.size)
        assertEquals("C", all[0].entity.name)
        assertEquals("B", all[1].entity.name)
        assertEquals("A", all[2].entity.name)
        assertEquals(3, all[0].turnIndex)
        assertEquals(2, all[1].turnIndex)
        assertEquals(1, all[2].turnIndex)
    }

    @Test
    fun `recordMentions with multiple entities in one turn`() {
        val e1 = IntentInference.Entity("e1", "Alice", IntentInference.EntityType.PERSON)
        val e2 = IntentInference.Entity("e2", "London", IntentInference.EntityType.LOCATION)

        store.recordMentions(1, listOf(e1, e2))

        val all = store.all()
        assertEquals(2, all.size)
        // Both recorded at turn 1, same recency
        assertEquals(1L, all[0].turnIndex)
        assertEquals(1L, all[1].turnIndex)
    }

    @Test
    fun `engine exposes referenceStore via getter`() {
        val engine = com.jarvis.app.cognitive.CognitiveEngine(
            memoryStore = object : com.jarvis.app.body.MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<com.jarvis.app.body.MemoryItem>()
            },
            humanCore = com.jarvis.app.humancore.HumanCore
        )
        val store = engine.getReferenceStore()
        assertNotNull(store)
        assertTrue(store is ReferenceStore)
    }
}
