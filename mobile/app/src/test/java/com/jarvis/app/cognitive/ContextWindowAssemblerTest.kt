package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.humancore.HumanCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextWindowAssemblerTest {

    private lateinit var tracker: TopicTracker
    private lateinit var referenceStore: ReferenceStore
    private lateinit var scorer: SalienceScorer
    private lateinit var assembler: ContextWindowAssembler

    private val alice = IntentInference.Entity(id = "alice", name = "Alice", type = IntentInference.EntityType.PERSON)
    private val bob = IntentInference.Entity(id = "bob", name = "Bob", type = IntentInference.EntityType.PERSON)
    private val report = IntentInference.Entity(id = "report", name = "the report", type = IntentInference.EntityType.OBJECT)

    @Before
    fun setUp() {
        tracker = TopicTracker(windowSize = 3, similarityThreshold = 0.15)
        referenceStore = ReferenceStore()
        scorer = SalienceScorer(referenceStore)
        assembler = ContextWindowAssembler(tracker, scorer)
    }

    @Test
    fun `assembled window includes only current segment turns`() {
        // Segment 0: turns 1-3 about project
        tracker.recordTurn(1, "Tell me about the project deadline")
        referenceStore.recordMentions(1, listOf(project), segmentId = 0)
        tracker.recordTurn(2, "What are the project deliverables")
        referenceStore.recordMentions(2, listOf(alice), segmentId = 0)
        tracker.recordTurn(3, "How is the project timeline")

        // Segment 1: turn 4 about weather (boundary fires)
        tracker.recordTurn(4, "What is the weather like in Paris today")
        referenceStore.recordMentions(4, listOf(bob), segmentId = 1)

        val window = assembler.assemble(currentTurnIndex = 4, currentSegmentId = 1)

        assertEquals(1, window.segmentId)
        assertEquals(1, window.turns.size)
        assertEquals(4L, window.turns[0].turnIndex)
        assertFalse(window.wasBounded)
    }

    @Test
    fun `assembled window includes salient entities from current segment`() {
        // Alice in segment 0
        referenceStore.recordMentions(1, listOf(alice), segmentId = 0)
        // Bob in segment 1 (current)
        referenceStore.recordMentions(4, listOf(bob), segmentId = 1)

        tracker.recordTurn(1, "Tell me about the project deadline")
        tracker.recordTurn(2, "What are the project deliverables")
        tracker.recordTurn(3, "How is the project timeline")
        tracker.recordTurn(4, "What is the weather like in Paris today")

        val window = assembler.assemble(currentTurnIndex = 5, currentSegmentId = 1)

        assertTrue(window.salientEntities.isNotEmpty())
        // Bob should be in the salient list (current segment)
        val entityNames = window.salientEntities.map { it.entity.name }
        assertTrue("Bob should be in salient entities", "Bob" in entityNames)
    }

    @Test
    fun `max turns bound is enforced`() {
        val boundedAssembler = ContextWindowAssembler(tracker, scorer, maxTurns = 3)

        // Create 10 turns in same topic (all about project)
        for (i in 1..10) {
            tracker.recordTurn(i.toLong(), "project deadline deliverables timeline update report status meeting")
        }

        val window = boundedAssembler.assemble(currentTurnIndex = 10, currentSegmentId = 0)

        assertTrue("Window should have at most 3 turns", window.turns.size <= 3)
        assertTrue("wasBounded should be true", window.wasBounded)
        assertEquals(10, window.turnCount)
    }

    @Test
    fun `long conversation spanning 2 segments only includes current segment`() {
        // Segment 0: turns 1-3
        tracker.recordTurn(1, "Tell me about the project deadline")
        tracker.recordTurn(2, "What are the project deliverables")
        tracker.recordTurn(3, "How is the project timeline")

        // Segment 1: turns 4-6 (all share "weather" keyword)
        tracker.recordTurn(4, "What is the weather like in Paris today")
        tracker.recordTurn(5, "Will the weather be nice for the weather forecast")
        tracker.recordTurn(6, "How is the weather in New York right now")

        val window = assembler.assemble(currentTurnIndex = 6, currentSegmentId = 1)

        assertEquals(1, window.segmentId)
        assertEquals(3, window.turns.size)
        // All turns should be from segment 1 (turns 4-6)
        assertTrue(window.turns.all { it.turnIndex >= 4L })
    }

    @Test
    fun `formatForPrompt produces readable context string`() {
        tracker.recordTurn(1, "Tell me about the project deadline")
        tracker.recordTurn(2, "What are the project deliverables")
        referenceStore.recordMentions(2, listOf(alice), segmentId = 0)

        val window = assembler.assemble(currentTurnIndex = 2, currentSegmentId = 0)
        val prompt = assembler.formatForPrompt(window)

        assertTrue("Prompt should mention segment", prompt.contains("segment 0"))
        assertTrue("Prompt should contain turn text", prompt.contains("project deadline"))
        assertTrue("Prompt should list entities", prompt.contains("Alice"))
    }

    @Test
    fun `empty segment produces minimal context`() {
        val window = assembler.assemble(currentTurnIndex = 0, currentSegmentId = 0)

        assertEquals(0, window.segmentId)
        assertTrue(window.turns.isEmpty())
        assertTrue(window.salientEntities.isEmpty())
    }

    @Test
    fun `engine exposes ContextWindowAssembler`() {
        val engine = CognitiveEngine(
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore
        )
        val assembler = engine.getContextWindowAssembler()
        assertNotNull(assembler)
        assertEquals(20, assembler.maxTurns)
    }

    /** Convenience — track segment id for ReferenceStore calls. */
    private val project get() = IntentInference.Entity(id = "project", name = "ProjectX", type = IntentInference.EntityType.CONCEPT)
}
