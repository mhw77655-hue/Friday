package com.jarvis.app.cognitive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AttentionEngine: ranks what matters now. Covers scoring, the bounded
 * spotlight, novelty decay, threshold filtering, and uncertainty metadata.
 */
class AttentionEngineTest {

    private fun engine(maxSpotlight: Int = 5, minScore: Float = 0.3f) = AttentionEngine(
        scope = CoroutineScope(Dispatchers.Default),
        config = AttentionEngine.Config(
            maxSpotlightSize = maxSpotlight,
            minScoreForSpotlight = minScore
        )
    )

    private fun item(
        id: String,
        content: String = id,
        salience: Float = 0.5f,
        urgency: Float = 0.5f,
        goalRelevance: Float = 0.5f,
        novelty: Float = 0.5f,
        source: AttentionSource = AttentionSource.USER_INPUT,
        metadata: Map<String, String> = emptyMap()
    ) = AttentionItem(
        id = id, content = content, source = source,
        salience = salience, urgency = urgency, goalRelevance = goalRelevance,
        novelty = novelty, metadata = metadata
    )

    @Test
    fun `submitted item gets a positive score and enters the spotlight`() {
        val e = engine()
        val result = e.submit(item("a1", salience = 0.8f, urgency = 0.7f, goalRelevance = 0.6f, novelty = 1.0f))

        assertTrue(result.score > 0f)
        assertTrue(result.inSpotlight)
        assertEquals(1, result.rank)
    }

    @Test
    fun `high priority item ranks above low priority`() {
        val e = engine()
        e.submit(item("a1", salience = 0.3f, urgency = 0.2f, goalRelevance = 0.2f, novelty = 0.2f))
        e.submit(item("a2", salience = 0.9f, urgency = 0.8f, goalRelevance = 0.8f, novelty = 0.9f))

        val spotlight = e.getSpotlight().items
        assertEquals("a2", spotlight[0].id)
        assertEquals(1, e.getRank("a2"))
    }

    @Test
    fun `spotlight is capped at max size and keeps the top items`() {
        val e = engine(maxSpotlight = 5)
        (1..10).forEach { i ->
            e.submit(item("a$i", salience = 0.5f + i * 0.05f))
        }

        val spotlight = e.getSpotlight().items
        assertEquals(5, spotlight.size)
        assertEquals("a10", spotlight[0].id)
    }

    @Test
    fun `re-submitting the same item lowers its novelty and score`() {
        val e = engine()
        val first = e.submit(item("a1", salience = 0.7f, novelty = 1.0f))
        val second = e.submit(item("a1", salience = 0.7f, novelty = 1.0f))

        assertTrue(second.score <= first.score)
    }

    @Test
    fun `items below the spotlight threshold are excluded`() {
        val e = engine(maxSpotlight = 5, minScore = 0.5f)
        e.submit(item("a1", salience = 0.9f, urgency = 0.9f, goalRelevance = 0.9f, novelty = 0.9f))
        e.submit(item("a2", salience = 0.1f, urgency = 0.1f, goalRelevance = 0.1f, novelty = 0.1f))

        val spotlight = e.getSpotlight().items
        assertEquals(listOf("a1"), spotlight.map { it.id })
        assertFalse(spotlight.any { it.id == "a2" })
    }

    @Test
    fun `uncertainty metadata raises the score`() {
        val e = engine()
        val withUncertainty = e.submit(item("u1", salience = 0.5f, metadata = mapOf("uncertainty" to "0.9")))
        val without = e.submit(item("u2", salience = 0.5f))

        assertTrue(withUncertainty.score > without.score)
    }

    @Test
    fun `non numeric uncertainty metadata does not crash scoring`() {
        val e = engine()
        val result = e.submit(item("u1", salience = 0.5f, metadata = mapOf("uncertainty" to "high")))
        assertTrue(result.score > 0f)
    }

    @Test
    fun `getAboveThreshold filters ranked items`() {
        val e = engine()
        e.submit(item("a1", salience = 0.9f, urgency = 0.9f, goalRelevance = 0.9f, novelty = 0.9f))
        e.submit(item("a2", salience = 0.3f, urgency = 0.2f, goalRelevance = 0.2f, novelty = 0.2f))

        val above = e.getAboveThreshold(0.5f)
        assertEquals(listOf("a1"), above.map { it.item.id })
    }

    @Test
    fun `remove drops an item and clear empties everything`() {
        val e = engine()
        e.submit(item("a1", salience = 0.9f))
        e.submit(item("a2", salience = 0.8f))

        assertTrue(e.remove("a1"))
        assertFalse(e.remove("a1"))
        assertNotNull(e.getSpotlight().items.firstOrNull { it.id == "a2" })

        e.clear()
        assertEquals(0, e.getAllRanked().size)
        assertEquals(0, e.getSpotlight().items.size)
    }

    @Test
    fun `stats track the spotlight`() {
        val e = engine()
        e.submit(item("a1", salience = 0.9f))
        e.submit(item("a2", salience = 0.5f))

        val stats = e.getStats()
        assertEquals(2, stats.currentSpotlightSize)
        assertEquals("a1", stats.topItemId)
        assertTrue(stats.avgSpotlightScore > 0f)
    }
}
