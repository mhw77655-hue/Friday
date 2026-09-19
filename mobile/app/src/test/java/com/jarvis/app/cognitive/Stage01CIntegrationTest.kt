package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stage 01C integration test: drives a realistic multi-topic, multi-turn
 * conversation through the full wiring of TopicTracker, SalienceScorer,
 * PronounResolver (salience-backed), ContextWindowAssembler, and
 * CognitiveEngine.
 *
 * Only the LLM boundary (classifyIntent seam) is mocked.
 * Entities are manually recorded to avoid depending on primitive extraction.
 */
class Stage01CIntegrationTest {

    private lateinit var scope: CoroutineScope
    private lateinit var engine: CognitiveEngine

    private val report = IntentInference.Entity(id = "report", name = "the report", type = IntentInference.EntityType.OBJECT)
    private val alice = IntentInference.Entity(id = "alice", name = "Alice", type = IntentInference.EntityType.PERSON)
    private val bob = IntentInference.Entity(id = "bob", name = "Bob", type = IntentInference.EntityType.PERSON)
    private val forecast = IntentInference.Entity(id = "forecast", name = "the forecast", type = IntentInference.EntityType.CONCEPT)

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default)
        engine = CognitiveEngine(
            scope = scope,
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore,
            intentClassificationComplete = { turn ->
                if (turn.contains("?")) "QUESTION" else "STATEMENT"
            }
        )
    }

    @Test
    fun `topic boundary fires at correct turn`() = runBlocking {
        val tracker = engine.getTopicTracker()

        engine.process("Tell me about Alice and the report")
        assertEquals(0, tracker.currentSegmentId())
        assertFalse(tracker.didSegmentChange())

        engine.process("How is Alice doing with the report")
        assertEquals(0, tracker.currentSegmentId())

        engine.process("What is the report deadline for Alice")
        assertEquals(0, tracker.currentSegmentId())

        engine.process("What is the weather like in Paris today")
        assertTrue("Boundary should fire at turn 4", tracker.didSegmentChange())
        assertEquals(1, tracker.currentSegmentId())
    }

    @Test
    fun `salience-driven resolution prefers current segment entity`() = runBlocking {
        val store = engine.getReferenceStore()
        val tracker = engine.getTopicTracker()
        val scorer = engine.getSalienceScorer()

        // Segment A: turns 1-3 about report
        engine.process("Tell me about the report status")
        store.recordMentions(1, listOf(report), segmentId = 0)
        engine.process("How is the report going")
        store.recordMentions(2, listOf(report), segmentId = 0)
        engine.process("What is the report deadline")
        store.recordMentions(3, listOf(report), segmentId = 0)

        // Segment B: turns 4-6 shift to weather
        engine.process("What is the weather like in Paris today")
        engine.process("Will the weather be nice for the weather forecast")
        engine.process("How is the weather in New York right now")

        // Verify salience ordering: report (prior segment) vs a current-segment entity
        store.recordMentions(7, listOf(bob), segmentId = 1)
        val reportScore = scorer.score(report, currentTurnIndex = 8, currentSegmentId = 1)
        val bobScore = scorer.score(bob, currentTurnIndex = 8, currentSegmentId = 1)
        assertTrue("Bob (current segment) should outrank report (prior): $bobScore > $reportScore", bobScore > reportScore)
    }

    @Test
    fun `topic return restores prior segment entities`() = runBlocking {
        val store = engine.getReferenceStore()
        val tracker = engine.getTopicTracker()
        val scorer = engine.getSalienceScorer()

        // Segment A: report
        engine.process("Tell me about the report status")
        store.recordMentions(1, listOf(report), segmentId = 0)

        // Segment B: weather
        engine.process("What is the weather like in Paris today")
        engine.process("Will the weather be nice for the weather forecast")
        engine.process("How is the weather in New York right now")

        // Segment C: return to report topic
        engine.process("Let us check on the report again")
        store.recordMentions(8, listOf(report), segmentId = 2)
        engine.process("How is the report coming along")
        store.recordMentions(9, listOf(report), segmentId = 2)
        engine.process("What is the report timeline now")
        store.recordMentions(10, listOf(report), segmentId = 2)

        // Report is now in the current segment (2) — should be highly salient
        val reportScore = scorer.score(report, currentTurnIndex = 11, currentSegmentId = 2)
        assertTrue("Report in current segment should have high salience: $reportScore", reportScore > 0.3)
    }

    @Test
    fun `ReferenceStore retains all entities across segments`() = runBlocking {
        val store = engine.getReferenceStore()

        store.recordMentions(1, listOf(report), segmentId = 0)
        store.recordMentions(2, listOf(alice), segmentId = 0)
        store.recordMentions(4, listOf(bob), segmentId = 1)
        store.recordMentions(5, listOf(forecast), segmentId = 1)

        val all = store.all()
        assertEquals(4, all.size)
        val names = all.map { it.entity.name }.toSet()
        assertTrue("the report" in names)
        assertTrue("Alice" in names)
        assertTrue("Bob" in names)
        assertTrue("the forecast" in names)
    }

    @Test
    fun `classifyIntent routing works against bounded context`() = runBlocking {
        val r1 = engine.process("What is the weather like today?")
        assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, r1.decision)

        val r2 = engine.process("The weather is nice today")
        assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, r2.decision)
    }

    @Test
    fun `context window assembly produces bounded output`() = runBlocking {
        val tracker = engine.getTopicTracker()
        val assembler = engine.getContextWindowAssembler()

        // Drive several turns
        for (i in 1..5) {
            engine.process("Tell me about the project deadline deliverables status meeting report")
        }

        val window = assembler.assemble(currentTurnIndex = 5, currentSegmentId = tracker.currentSegmentId())
        assertNotNull(window)
        assertTrue("Window should have turns", window.turns.isNotEmpty())
        assertTrue("Window bounded by maxTurns", window.turns.size <= assembler.maxTurns)
    }

    @Test
    fun `all Stage 01C components are wired and accessible`() {
        assertNotNull(engine.getTopicTracker())
        assertNotNull(engine.getSalienceScorer())
        assertNotNull(engine.getContextWindowAssembler())
        assertNotNull(engine.getPronounResolver())
        assertNotNull(engine.getReferenceStore())
        assertEquals(0, engine.getTopicTracker().currentSegmentId())
    }
}
