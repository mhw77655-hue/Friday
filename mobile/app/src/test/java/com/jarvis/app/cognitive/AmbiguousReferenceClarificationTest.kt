package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AmbiguousReferenceClarification: when PronounResolver finds 2+ equally-plausible
 * candidate entities for a reference, CognitiveEngine produces a
 * NEEDS_CLARIFICATION outcome carrying the candidates — not a silent guess.
 */
class AmbiguousReferenceClarificationTest {

    private lateinit var engine: CognitiveEngine

    @Before
    fun setup() {
        engine = CognitiveEngine(
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore
        )
    }

    @Test
    fun `ambiguous same-type candidates produce NEEDS_CLARIFICATION`() = runBlocking {
        // Establish two OBJECTs in the ReferenceStore
        engine.getReferenceStore().recordMentions(1, listOf(
            IntentInference.Entity("e1", "the report", IntentInference.EntityType.OBJECT)
        ))
        engine.getReferenceStore().recordMentions(2, listOf(
            IntentInference.Entity("e2", "the spreadsheet", IntentInference.EntityType.OBJECT)
        ))

        // "it" is ambiguous between two OBJECTs
        val result = engine.process("Can you update it?")
        assertEquals(CognitiveEngine.TurnDecision.NEEDS_CLARIFICATION, result.decision)
        assertNotNull(result.clarificationCandidates)
        assertTrue(result.clarificationCandidates!!.isNotEmpty())
        assertTrue(result.clarificationCandidates!!.all { it.ambiguous })
    }

    @Test
    fun `clarification outcome carries actual candidate entities`() = runBlocking {
        engine.getReferenceStore().recordMentions(1, listOf(
            IntentInference.Entity("e1", "report", IntentInference.EntityType.OBJECT)
        ))
        engine.getReferenceStore().recordMentions(2, listOf(
            IntentInference.Entity("e2", "spreadsheet", IntentInference.EntityType.OBJECT)
        ))

        val result = engine.process("Can you update it?")
        assertEquals(CognitiveEngine.TurnDecision.NEEDS_CLARIFICATION, result.decision)

        val candidates = result.clarificationCandidates!!.first().candidates
        assertEquals(2, candidates.size)
        val names = candidates.map { it.name }.toSet()
        assertTrue("report" in names)
        assertTrue("spreadsheet" in names)
    }

    @Test
    fun `single plausible candidate resolves normally without clarification`() = runBlocking {
        // Only one OBJECT in the store
        engine.getReferenceStore().recordMentions(1, listOf(
            IntentInference.Entity("e1", "the report", IntentInference.EntityType.OBJECT)
        ))

        val result = engine.process("How is it going?")
        // Should NOT be NEEDS_CLARIFICATION — single candidate resolves cleanly
        assertFalse(result.decision == CognitiveEngine.TurnDecision.NEEDS_CLARIFICATION)
        assertNull(result.clarificationCandidates)
    }

    @Test
    fun `turn with no pronoun does not trigger clarification`() = runBlocking {
        engine.getReferenceStore().recordMentions(1, listOf(
            IntentInference.Entity("e1", "report", IntentInference.EntityType.OBJECT)
        ))
        engine.getReferenceStore().recordMentions(2, listOf(
            IntentInference.Entity("e2", "spreadsheet", IntentInference.EntityType.OBJECT)
        ))

        // No pronoun — "Tell me about the report" has no reference to resolve
        val result = engine.process("Tell me about the report")
        assertFalse(result.decision == CognitiveEngine.TurnDecision.NEEDS_CLARIFICATION)
    }

    private fun assertNull(value: Any?) {
        org.junit.Assert.assertNull(value)
    }
}
