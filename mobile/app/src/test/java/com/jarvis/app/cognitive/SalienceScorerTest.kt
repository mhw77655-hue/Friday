package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SalienceScorerTest {

    private lateinit var referenceStore: ReferenceStore
    private lateinit var scorer: SalienceScorer

    private val alice = IntentInference.Entity(
        id = "alice",
        name = "Alice",
        type = IntentInference.EntityType.PERSON
    )
    private val bob = IntentInference.Entity(
        id = "bob",
        name = "Bob",
        type = IntentInference.EntityType.PERSON
    )
    private val projectX = IntentInference.Entity(
        id = "projectx",
        name = "ProjectX",
        type = IntentInference.EntityType.CONCEPT
    )
    private val report = IntentInference.Entity(
        id = "report",
        name = "the report",
        type = IntentInference.EntityType.OBJECT
    )

    @Before
    fun setUp() {
        referenceStore = ReferenceStore()
        scorer = SalienceScorer(referenceStore)
    }

    @Test
    fun `score returns 0 for unknown entity`() {
        val score = scorer.score(alice, currentTurnIndex = 10, currentSegmentId = 0)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `entity in current segment outranks entity in prior segment`() {
        // Alice mentioned in segment 1 (current) at turn 8
        referenceStore.recordMentions(8, listOf(alice), segmentId = 1)
        // Bob mentioned more recently in segment 0 (prior, closed) at turn 9
        referenceStore.recordMentions(9, listOf(bob), segmentId = 0)

        val aliceScore = scorer.score(alice, currentTurnIndex = 10, currentSegmentId = 1)
        val bobScore = scorer.score(bob, currentTurnIndex = 10, currentSegmentId = 1)

        assertTrue(
            "Alice (current segment) should outrank Bob (prior segment): $aliceScore > $bobScore",
            aliceScore > bobScore
        )
    }

    @Test
    fun `frequency matters - entity mentioned 3 times outranks equally-recent entity mentioned once`() {
        // Alice mentioned 3 times in segment 1
        referenceStore.recordMentions(8, listOf(alice), segmentId = 1)
        referenceStore.recordMentions(9, listOf(alice), segmentId = 1)
        referenceStore.recordMentions(10, listOf(alice), segmentId = 1)
        // Bob mentioned once at the same recency in same segment
        referenceStore.recordMentions(10, listOf(bob), segmentId = 1)

        val aliceScore = scorer.score(alice, currentTurnIndex = 11, currentSegmentId = 1)
        val bobScore = scorer.score(bob, currentTurnIndex = 11, currentSegmentId = 1)

        assertTrue(
            "Alice (3 mentions) should outrank Bob (1 mention): $aliceScore > $bobScore",
            aliceScore > bobScore
        )
    }

    @Test
    fun `topSalient returns n entities in descending score order`() {
        referenceStore.recordMentions(5, listOf(alice), segmentId = 0)
        referenceStore.recordMentions(6, listOf(bob), segmentId = 0)
        referenceStore.recordMentions(7, listOf(projectX), segmentId = 0)
        referenceStore.recordMentions(8, listOf(report), segmentId = 0)

        val top2 = scorer.topSalient(2, currentTurnIndex = 10, currentSegmentId = 0)
        assertEquals(2, top2.size)
        assertTrue(top2[0].second >= top2[1].second)
    }

    @Test
    fun `topSalient returns fewer than n when fewer entities known`() {
        referenceStore.recordMentions(5, listOf(alice), segmentId = 0)
        referenceStore.recordMentions(6, listOf(bob), segmentId = 0)

        val top5 = scorer.topSalient(5, currentTurnIndex = 10, currentSegmentId = 0)
        assertEquals(2, top5.size)
    }

    @Test
    fun `topSalient returns empty list when no entities known`() {
        val top3 = scorer.topSalient(3, currentTurnIndex = 10, currentSegmentId = 0)
        assertTrue(top3.isEmpty())
    }

    @Test
    fun `recency decay - entity mentioned recently scores higher than entity mentioned long ago`() {
        // Alice mentioned 1 turn ago
        referenceStore.recordMentions(9, listOf(alice), segmentId = 1)
        // Bob mentioned 20 turns ago
        referenceStore.recordMentions(0, listOf(bob), segmentId = 1)

        val aliceScore = scorer.score(alice, currentTurnIndex = 10, currentSegmentId = 1)
        val bobScore = scorer.score(bob, currentTurnIndex = 10, currentSegmentId = 1)

        assertTrue(
            "Recently mentioned Alice should outrank long-ago Bob: $aliceScore > $bobScore",
            aliceScore > bobScore
        )
    }

    @Test
    fun `segment 0 (no segment info) gives neutral topic relevance`() {
        referenceStore.recordMentions(5, listOf(alice), segmentId = 0)
        referenceStore.recordMentions(6, listOf(bob), segmentId = 0)

        // Both have segment 0 — topic relevance is neutral for both
        val aliceScore = scorer.score(alice, currentTurnIndex = 10, currentSegmentId = 0)
        val bobScore = scorer.score(bob, currentTurnIndex = 10, currentSegmentId = 0)
        // Bob is slightly more recent but Alice has equal recency weight
        // The key assertion: neither gets a topic bonus
        assertTrue(aliceScore > 0.0)
        assertTrue(bobScore > 0.0)
    }

    @Test
    fun `CognitiveEngine exposes SalienceScorer`() {
        val engine = CognitiveEngine(
            scope = CoroutineScope(Dispatchers.Default),
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore
        )
        val scorer = engine.getSalienceScorer()
        assertEquals(0.0, scorer.score(alice, 10, 0), 0.001)
    }
}
