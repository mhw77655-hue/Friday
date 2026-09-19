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
 * Stage 01B end-to-end integration test.  Drives a realistic multi-turn
 * conversation through the full path: entity establishment → pronoun resolution
 * → ambiguity detection → intent classification → routing.
 *
 * Only the LLM boundary (intentClassificationComplete) is fixture/mocked;
 * everything else uses real CognitiveEngine / ReferenceStore / PronounResolver.
 */
class Stage01BIntegrationTest {

    private lateinit var engine: CognitiveEngine
    private lateinit var intentFixture: MutableMap<String, String>

    @Before
    fun setup() {
        intentFixture = mutableMapOf()
        engine = CognitiveEngine(
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore,
            intentClassificationComplete = { turn -> intentFixture[turn.lowercase()] ?: "STATEMENT" }
        )
    }

    @Test
    fun `full multi-turn scenario through Stage 01B pipeline`() = runBlocking {
        // ---- Turn 1: Establish entity A (PERSON "Alice") ----
        // Simulate entity extraction by recording into ReferenceStore directly
        // (since IntentInference.extractEntities only catches proper-noun pairs)
        engine.getReferenceStore().recordMentions(1, listOf(
            IntentInference.Entity("e1", "Alice", IntentInference.EntityType.PERSON)
        ))
        intentFixture["ask alice about the project"] = "COMMAND"

        // Lowercase the leading word so IntentInference.extractEntities' two-capitalised
        // word rule does not inject a spurious CONCEPT entity that would later compete
        // with "the report" when resolving the pronoun "it".
        val turn1 = engine.process("ask Alice about the project")
        // Intent classification: COMMAND → PLAN
        assertEquals(CognitiveEngine.TurnDecision.PLAN, turn1.decision)
        // No pronoun to resolve
        assertFalse(turn1.cognitiveResult.pronounResolution.ambiguous)

        // ---- Turn 2: Establish entity B (OBJECT "the report") ----
        engine.getReferenceStore().recordMentions(2, listOf(
            IntentInference.Entity("e2", "the report", IntentInference.EntityType.OBJECT)
        ))
        intentFixture["also check the report"] = "COMMAND"

        val turn2 = engine.process("Also check the report")
        assertEquals(CognitiveEngine.TurnDecision.PLAN, turn2.decision)
        assertFalse(turn2.cognitiveResult.pronounResolution.ambiguous)

        // ---- Turn 3: Pronoun "it" resolves to "the report" (OBJECT) ----
        intentFixture["how is it going"] = "QUESTION"

        val turn3 = engine.process("How is it going?")
        // "it" → OBJECT → resolves to "the report" (only OBJECT in store)
        assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, turn3.decision)
        assertFalse(turn3.cognitiveResult.pronounResolution.ambiguous)
        assertEquals("How is the report going?", turn3.cognitiveResult.resolvedText)
        val itResolution = turn3.cognitiveResult.pronounResolution.resolutions.first()
        assertEquals("the report", itResolution.resolvedTo)
        // "How is it going?" → "How is the report going?"

        // ---- Turn 4: Establish another OBJECT, then ambiguous "it" ----
        engine.getReferenceStore().recordMentions(3, listOf(
            IntentInference.Entity("e3", "the spreadsheet", IntentInference.EntityType.OBJECT)
        ))
        intentFixture["can you update it"] = "COMMAND"

        val turn4 = engine.process("Can you update it?")
        // "it" has two OBJECT candidates: "the report" and "the spreadsheet"
        assertEquals(CognitiveEngine.TurnDecision.NEEDS_CLARIFICATION, turn4.decision)
        assertTrue(turn4.cognitiveResult.pronounResolution.ambiguous)
        assertNotNull(turn4.clarificationCandidates)
        val candidateNames = turn4.clarificationCandidates!!.first().candidates.map { it.name }.toSet()
        assertTrue("the report" in candidateNames)
        assertTrue("the spreadsheet" in candidateNames)

        // ---- Turn 5: Plain command, no pronoun ----
        intentFixture["send the summary now"] = "COMMAND"

        val turn5 = engine.process("Send the summary now")
        assertEquals(CognitiveEngine.TurnDecision.PLAN, turn5.decision)
        assertFalse(turn5.cognitiveResult.pronounResolution.ambiguous)
        assertTrue(turn5.cognitiveResult.pronounResolution.resolutions.isEmpty())
    }

    @Test
    fun `all Stage 01B stories pass on full re-run`() = runBlocking {
        // Quick sanity: all 4 prior stories' core behaviors work in one flow
        val store = engine.getReferenceStore()

        // REFERENCE-STORE: record + mostRecent + all
        store.recordMentions(1, listOf(
            IntentInference.Entity("e1", "Alice", IntentInference.EntityType.PERSON)
        ))
        store.recordMentions(2, listOf(
            IntentInference.Entity("e2", "London", IntentInference.EntityType.LOCATION)
        ))
        assertEquals("London", store.mostRecent()!!.name)
        assertEquals("Alice", store.mostRecent(IntentInference.EntityType.PERSON)!!.name)
        assertEquals(2, store.all().size)

        // PRONOUN-RESOLUTION: resolve "it" to OBJECT (none → unresolved)
        val resolver = engine.getPronounResolver()
        val res = resolver.resolve("How is it?")
        assertFalse(res.ambiguous) // no OBJECT in store, "it" stays unresolved
        assertEquals("How is it?", res.resolvedText)

        // INTENT-CLASSIFICATION: classify + route
        intentFixture["what time is it"] = "QUESTION"
        intentFixture["turn on the lights"] = "COMMAND"
        assertEquals(CognitiveEngine.IntentCategory.QUESTION, engine.classifyIntent("what time is it"))
        assertEquals(CognitiveEngine.IntentCategory.COMMAND, engine.classifyIntent("turn on the lights"))

        // AMBIGUOUS-REFERENCE-CLARIFICATION: trigger clarification
        store.recordMentions(3, listOf(
            IntentInference.Entity("e3", "report", IntentInference.EntityType.OBJECT)
        ))
        store.recordMentions(4, listOf(
            IntentInference.Entity("e4", "spreadsheet", IntentInference.EntityType.OBJECT)
        ))
        intentFixture["update it"] = "COMMAND"
        val ambiguous = engine.process("Update it")
        assertEquals(CognitiveEngine.TurnDecision.NEEDS_CLARIFICATION, ambiguous.decision)
        assertTrue(ambiguous.cognitiveResult.pronounResolution.ambiguous)
    }
}
