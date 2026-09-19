package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.humancore.HumanCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TopicTrackerTest {

    private lateinit var tracker: TopicTracker

    @Before
    fun setUp() {
        tracker = TopicTracker(windowSize = 3, similarityThreshold = 0.15)
    }

    @Test
    fun `initial segment id is 0`() {
        assertEquals(0, tracker.currentSegmentId())
        assertFalse(tracker.didSegmentChange())
    }

    @Test
    fun `stable topic keeps same segment id`() {
        tracker.recordTurn(1, "Tell me about the project deadline and deliverables")
        tracker.recordTurn(2, "What are the project deliverables and timeline")
        tracker.recordTurn(3, "How is the project timeline looking")
        tracker.recordTurn(4, "Any update on the project deadline")
        assertFalse(tracker.didSegmentChange())
        assertEquals(0, tracker.currentSegmentId())
    }

    @Test
    fun `clear topic shift triggers exactly one boundary`() {
        // Turns 1-3: project topic
        tracker.recordTurn(1, "Tell me about the project deadline")
        tracker.recordTurn(2, "What are the project deliverables")
        tracker.recordTurn(3, "How is the project timeline")

        // Turn 4: completely unrelated topic
        tracker.recordTurn(4, "What is the weather like in Paris today")
        assertTrue(tracker.didSegmentChange())
        assertEquals(1, tracker.currentSegmentId())

        // Turn 5: same new topic — no further boundary (skipped after boundary)
        tracker.recordTurn(5, "Will it rain tomorrow in London")
        assertFalse(tracker.didSegmentChange())
        assertEquals(1, tracker.currentSegmentId())
    }

    @Test
    fun `threshold is exposed as constructor parameter`() {
        val strict = TopicTracker(similarityThreshold = 0.9)
        strict.recordTurn(1, "project deadline deliverables timeline")
        strict.recordTurn(2, "completely different unrelated topic here")
        assertTrue(strict.didSegmentChange())
    }

    @Test
    fun `no boundary when similarity is above threshold`() {
        val lenient = TopicTracker(similarityThreshold = 0.05)
        lenient.recordTurn(1, "project deadline deliverables timeline report")
        lenient.recordTurn(2, "project status update timeline review")
        assertFalse(lenient.didSegmentChange())
    }

    @Test
    fun `jaccard similarity computes correctly`() {
        val a = setOf("project", "deadline", "timeline")
        val b = setOf("project", "deadline", "report")
        // intersection = {project, deadline} = 2, union = 4 → 0.5
        assertEquals(0.5, tracker.jaccard(a, b), 0.001)
    }

    @Test
    fun `jaccard with identical sets returns 1`() {
        val a = setOf("foo", "bar", "baz")
        assertEquals(1.0, tracker.jaccard(a, a), 0.001)
    }

    @Test
    fun `jaccard with disjoint sets returns 0`() {
        val a = setOf("foo", "bar")
        val b = setOf("baz", "qux")
        assertEquals(0.0, tracker.jaccard(a, b), 0.001)
    }

    @Test
    fun `extractKeywords removes stopwords and short words`() {
        val keywords = TopicTracker.extractKeywords("The quick brown fox jumps over the lazy dog")
        assertTrue("quick" in keywords)
        assertTrue("brown" in keywords)
        assertTrue("fox" in keywords)
        assertTrue("jumps" in keywords)
        assertTrue("lazy" in keywords)
        assertTrue("dog" in keywords)
        assertFalse("the" in keywords)
        assertFalse("over" in keywords)
    }

    @Test
    fun `window size limits rolling average`() {
        val small = TopicTracker(windowSize = 2, similarityThreshold = 0.15)
        small.recordTurn(1, "apple banana cherry")
        small.recordTurn(2, "apple banana cherry")
        small.recordTurn(3, "apple banana cherry")
        // Turn 4: same topic, window only holds turns 2-3
        small.recordTurn(4, "apple banana cherry")
        assertFalse(small.didSegmentChange())
    }

    @Test
    fun `empty text produces no keywords and no crash`() {
        tracker.recordTurn(1, "")
        tracker.recordTurn(2, "actual topic with real words here")
        // Empty turn has no keywords — Jaccard vs real topic is 0.0, correctly triggers boundary
        assertTrue(tracker.didSegmentChange())
        assertEquals(1, tracker.currentSegmentId())
    }

    @Test
    fun `CognitiveEngine exposes TopicTracker`() {
        val engine = CognitiveEngine(
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore
        )
        val tracker = engine.getTopicTracker()
        assertEquals(0, tracker.currentSegmentId())
    }
}
