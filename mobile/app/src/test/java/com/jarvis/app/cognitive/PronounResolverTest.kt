package com.jarvis.app.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PronounResolver: detects pronouns/references in user text and resolves each
 * to a concrete entity from ReferenceStore, type-checked against grammatical role.
 */
class PronounResolverTest {

    private lateinit var store: ReferenceStore
    private lateinit var resolver: PronounResolver

    @Before
    fun setup() {
        store = ReferenceStore()
        resolver = PronounResolver(store)
    }

    @Test
    fun `pronoun resolved to correct entity from 2 prior turns`() {
        // Turn 1: establish a PERSON
        store.recordMentions(1, listOf(
            IntentInference.Entity("e1", "Alice", IntentInference.EntityType.PERSON)
        ))
        // Turn 2: establish an OBJECT
        store.recordMentions(2, listOf(
            IntentInference.Entity("e2", "the report", IntentInference.EntityType.OBJECT)
        ))

        // Turn 3: "it" should resolve to "the report" (most recent OBJECT)
        val result = resolver.resolve("How is it going?")
        assertEquals("How is the report going?", result.resolvedText)
        assertFalse(result.ambiguous)
        assertEquals(1, result.resolutions.size)
        assertEquals("the report", result.resolutions[0].resolvedTo)
    }

    @Test
    fun `type-mismatched candidates excluded`() {
        // Only a PERSON in the store
        store.recordMentions(1, listOf(
            IntentInference.Entity("e1", "Alice", IntentInference.EntityType.PERSON)
        ))

        // "it" should NOT resolve to "Alice" (PERSON is not OBJECT/CONCEPT)
        val result = resolver.resolve("How is it?")
        assertEquals("How is it?", result.resolvedText) // unchanged
        assertEquals(1, result.resolutions.size)
        assertNull(result.resolutions[0].resolvedTo)
        assertFalse(result.ambiguous) // no candidates, not ambiguous
    }

    @Test
    fun `turn with no pronoun is unaffected`() {
        store.recordMentions(1, listOf(
            IntentInference.Entity("e1", "Alice", IntentInference.EntityType.PERSON)
        ))

        val result = resolver.resolve("Tell me about Alice")
        assertEquals("Tell me about Alice", result.resolvedText)
        assertTrue(result.resolutions.isEmpty())
        assertFalse(result.ambiguous)
    }

    @Test
    fun `ambiguous resolution for 2 same-type candidates`() {
        // Two OBJECTs mentioned in different turns
        store.recordMentions(1, listOf(
            IntentInference.Entity("e1", "the report", IntentInference.EntityType.OBJECT)
        ))
        store.recordMentions(2, listOf(
            IntentInference.Entity("e2", "the spreadsheet", IntentInference.EntityType.OBJECT)
        ))

        // "it" has two OBJECT candidates — should be ambiguous
        val result = resolver.resolve("Can you update it?")
        assertEquals("Can you update it?", result.resolvedText) // unchanged
        assertTrue(result.ambiguous)
        assertEquals(1, result.resolutions.size)
        assertTrue(result.resolutions[0].ambiguous)
        assertEquals(2, result.resolutions[0].candidates.size)
    }

    @Test
    fun `he resolves to most recent PERSON when only one exists`() {
        store.recordMentions(1, listOf(
            IntentInference.Entity("e1", "Bob", IntentInference.EntityType.PERSON)
        ))

        val result = resolver.resolve("Where is he?")
        assertEquals("Where is Bob?", result.resolvedText)
        assertEquals("Bob", result.resolutions[0].resolvedTo)
    }

    @Test
    fun `he is ambiguous when multiple PERSONs exist`() {
        store.recordMentions(1, listOf(
            IntentInference.Entity("e1", "Bob", IntentInference.EntityType.PERSON)
        ))
        store.recordMentions(2, listOf(
            IntentInference.Entity("e2", "Carol", IntentInference.EntityType.PERSON)
        ))

        val result = resolver.resolve("Where is he?")
        assertEquals("Where is he?", result.resolvedText) // unchanged
        assertTrue(result.ambiguous)
        assertEquals(2, result.resolutions[0].candidates.size)
    }

    @Test
    fun `ordinal reference resolves to chronological position`() {
        // Mentioned in order: Alice (1), Bob (2), Carol (3)
        store.recordMentions(1, listOf(
            IntentInference.Entity("e1", "Alice", IntentInference.EntityType.PERSON)
        ))
        store.recordMentions(2, listOf(
            IntentInference.Entity("e2", "Bob", IntentInference.EntityType.PERSON)
        ))
        store.recordMentions(3, listOf(
            IntentInference.Entity("e3", "Carol", IntentInference.EntityType.PERSON)
        ))

        val result = resolver.resolve("What about the second one?")
        assertEquals("What about Bob?", result.resolvedText)
        assertEquals("Bob", result.resolutions[0].resolvedTo)
    }

    @Test
    fun `engine exposes pronounResolver`() {
        val engine = com.jarvis.app.cognitive.CognitiveEngine(
            memoryStore = object : com.jarvis.app.body.MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<com.jarvis.app.body.MemoryItem>()
            },
            humanCore = com.jarvis.app.humancore.HumanCore
        )
        val resolver = engine.getPronounResolver()
        assertNotNull(resolver)
        assertTrue(resolver is PronounResolver)
    }
}
