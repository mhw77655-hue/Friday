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

class StaleContextDecayTest {

    private lateinit var tracker: TopicTracker
    private lateinit var referenceStore: ReferenceStore
    private lateinit var scorer: SalienceScorer
    private lateinit var resolver: PronounResolver

    private val report = IntentInference.Entity(id = "report", name = "the report", type = IntentInference.EntityType.OBJECT)
    private val document = IntentInference.Entity(id = "document", name = "the document", type = IntentInference.EntityType.OBJECT)
    private val projectX = IntentInference.Entity(id = "projectx", name = "ProjectX", type = IntentInference.EntityType.CONCEPT)
    private val budget = IntentInference.Entity(id = "budget", name = "the budget", type = IntentInference.EntityType.OBJECT)

    @Before
    fun setUp() {
        tracker = TopicTracker(windowSize = 3, similarityThreshold = 0.15)
        referenceStore = ReferenceStore()
        scorer = SalienceScorer(referenceStore)
        resolver = PronounResolver(referenceStore, scorer)
    }

    @Test
    fun `topic shift stops prior-segment entity from dominating candidates`() {
        // Segment 0: report mentioned at turns 1-3
        referenceStore.recordMentions(1, listOf(report), segmentId = 0)
        referenceStore.recordMentions(2, listOf(report), segmentId = 0)
        referenceStore.recordMentions(3, listOf(report), segmentId = 0)
        tracker.recordTurn(1, "Tell me about the report status")
        tracker.recordTurn(2, "How is the report going")
        tracker.recordTurn(3, "What is the report deadline")

        // Segment 1: document mentioned at turns 4-6
        referenceStore.recordMentions(4, listOf(document), segmentId = 1)
        referenceStore.recordMentions(5, listOf(document), segmentId = 1)
        referenceStore.recordMentions(6, listOf(document), segmentId = 1)
        tracker.recordTurn(4, "What is the weather like in Paris today")
        tracker.recordTurn(5, "Will the weather be nice for the weather forecast")
        tracker.recordTurn(6, "How is the weather in New York right now")

        // Turn 7: use "it" — should resolve to document (current segment), not report (prior)
        resolver.setContext(7, 1)
        val result = resolver.resolve("How is it going")

        // "it" should resolve to the document (current segment, higher salience)
        val itResolution = result.resolutions.firstOrNull { it.surface.lowercase() == "it" }
        assertNotNull(itResolution)
        assertEquals("the document", itResolution!!.resolvedTo)
    }

    @Test
    fun `return to prior topic restores that segments entities as viable`() {
        // Segment 0: report
        referenceStore.recordMentions(1, listOf(report), segmentId = 0)
        tracker.recordTurn(1, "Tell me about the report status")

        // Segment 1: document
        referenceStore.recordMentions(4, listOf(document), segmentId = 1)
        tracker.recordTurn(4, "What is the weather like in Paris today")
        tracker.recordTurn(5, "Will the weather be nice for the weather forecast")
        tracker.recordTurn(6, "How is the weather in New York right now")

        // Segment 2: back to project/report topic
        referenceStore.recordMentions(9, listOf(report), segmentId = 2)
        tracker.recordTurn(9, "Let us revisit the report and the project status")
        tracker.recordTurn(10, "How is the report and project coming along")
        tracker.recordTurn(11, "What is the report and project timeline")

        // Turn 12: use "it" — should resolve to report (now in current segment 2)
        resolver.setContext(12, 2)
        val result = resolver.resolve("How is it looking")

        val itResolution = result.resolutions.firstOrNull { it.surface.lowercase() == "it" }
        assertNotNull(itResolution)
        assertEquals("the report", itResolution!!.resolvedTo)
    }

    @Test
    fun `ReferenceStore all returns every entity regardless of decay`() {
        referenceStore.recordMentions(1, listOf(report), segmentId = 0)
        referenceStore.recordMentions(2, listOf(document), segmentId = 0)
        referenceStore.recordMentions(3, listOf(projectX), segmentId = 1)
        referenceStore.recordMentions(4, listOf(budget), segmentId = 1)

        val all = referenceStore.all()
        assertEquals(4, all.size)

        val entityNames = all.map { it.entity.name }.toSet()
        assertTrue("the report" in entityNames)
        assertTrue("the document" in entityNames)
        assertTrue("ProjectX" in entityNames)
        assertTrue("the budget" in entityNames)
    }

    @Test
    fun `existing ambiguity behavior unchanged - 2 same-type still ambiguous`() {
        // Two OBJECT entities in same segment
        referenceStore.recordMentions(1, listOf(report), segmentId = 0)
        referenceStore.recordMentions(2, listOf(document), segmentId = 0)
        tracker.recordTurn(1, "Tell me about the report status")
        tracker.recordTurn(2, "How is the document going")

        resolver.setContext(3, 0)
        val result = resolver.resolve("Tell me about it")

        assertTrue("Should be ambiguous with 2 same-type entities", result.ambiguous)
    }

    @Test
    fun `salience scoring favors current segment entity over prior segment entity`() {
        // Report in segment 0 (old, but mentioned more recently in raw turn count)
        referenceStore.recordMentions(8, listOf(report), segmentId = 0)
        // Document in segment 1 (current)
        referenceStore.recordMentions(5, listOf(document), segmentId = 1)

        val reportScore = scorer.score(report, currentTurnIndex = 10, currentSegmentId = 1)
        val docScore = scorer.score(document, currentTurnIndex = 10, currentSegmentId = 1)

        assertTrue(
            "Document (current segment) should outrank report (prior segment): $docScore > $reportScore",
            docScore > reportScore
        )
    }

    @Test
    fun `engine exposes PronounResolver with salience scorer`() {
        val engine = CognitiveEngine(
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore
        )
        val resolver = engine.getPronounResolver()
        assertNotNull(resolver)
    }
}
