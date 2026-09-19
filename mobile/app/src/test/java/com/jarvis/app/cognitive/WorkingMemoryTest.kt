package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryType
import com.jarvis.app.cognitive.WorkingMemory.InsertResult
import com.jarvis.app.cognitive.WorkingMemory.AccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WorkingMemory: bounded, activation-based. Covers insertion, access
 * activation, decay, eviction (capacity + low activation), the explicit
 * activation request, query relevance, goal boosting, and the failure cases
 * (missing memory, overloaded working memory).
 */
class WorkingMemoryTest {

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun wm(config: WorkingMemory.Config = WorkingMemory.Config()): WorkingMemory {
        val scope = CoroutineScope(Dispatchers.Default)
        scopes.add(scope)
        return WorkingMemory(scope = scope, config = config)
    }

    private fun item(
        id: String,
        activation: Float = 0.5f,
        relevance: Float = 0.5f,
        goalAlignment: Float = 0.5f,
        uncertainty: Float = 0.1f,
        content: String = "memory $id"
    ) = ActiveMemory(
        id = id, sourceId = "s_$id", content = content,
        memoryType = MemoryType.FACT, activation = activation,
        relevance = relevance, goalAlignment = goalAlignment, uncertainty = uncertainty
    )

    @Test
    fun `insert then get returns the stored item`() {
        val w = wm()
        assertTrue(w.insert(item("m1", activation = 0.8f, content = "Test memory")) is InsertResult.SUCCESS)

        val retrieved = w.get("m1")
        assertNotNull(retrieved)
        assertEquals("Test memory", retrieved!!.content)
        assertEquals(0.8f, retrieved.activation, 0.0001f)
    }

    @Test
    fun `insertFromBodyMemory maps a store item into working memory`() {
        val w = wm()
        val bodyMem = MemoryItem(
            id = "body_1", type = MemoryType.EPISODIC,
            content = "User likes coffee", timestamp = System.currentTimeMillis(),
            tags = listOf("preference", "user"), relevance = 0.8f
        )
        w.insertFromBodyMemory(bodyMem, initialActivation = 0.7f)

        // insertFromBodyMemory prefixes ids so they don't collide with local items
        val stored = w.get("wm_body_1")
        assertNotNull(stored)
        assertEquals("User likes coffee", stored!!.content)
        assertEquals(MemoryType.EPISODIC, stored.memoryType)
    }

    @Test
    fun `access boosts activation and records access`() {
        val w = wm()
        w.insert(item("m1", activation = 0.5f))
        val result = w.access("m1")

        assertTrue(result is AccessResult.SUCCESS)
        assertEquals(0.6f, (result as AccessResult.SUCCESS).item.activation, 0.0001f)
        assertEquals(1L, w.getStats().totalAccesses)
    }

    @Test
    fun `access on a missing memory is not found`() {
        val w = wm()
        w.insert(item("m1"))
        assertEquals(AccessResult.NOT_FOUND, w.access("does-not-exist"))
    }

    @Test
    fun `activate raises activation for an explicit request`() {
        val w = wm()
        w.insert(item("m1", activation = 0.5f))
        val result = w.activate("m1", amount = 0.2f)

        assertTrue(result is AccessResult.SUCCESS)
        assertEquals(0.7f, (result as AccessResult.SUCCESS).item.activation, 0.0001f)
    }

    @Test
    fun `activate on a missing memory is not found`() {
        val w = wm()
        assertEquals(AccessResult.NOT_FOUND, w.activate("nope", amount = 0.2f))
    }

    @Test
    fun `decay lowers activation and low activation items are evicted`() {
        val w = wm(
            WorkingMemory.Config(maxItems = 10, baseDecayRate = 0.1f, minActivationForRetention = 0.2f)
        )
        w.insert(item("m1", activation = 0.5f))

        repeat(3) { w.decayOnce() } // 0.5 -> 0.4 -> 0.3 -> 0.2
        assertEquals(0.2f, w.get("m1")!!.activation, 0.0001f)

        w.decayOnce() // 0.2 -> 0.1, below retention floor -> evicted
        assertNull(w.get("m1"))
        assertTrue(w.getStats().totalEvictions >= 1L)
    }

    @Test
    fun `working memory stays bounded on overload`() {
        val w = wm(WorkingMemory.Config(maxItems = 3))
        repeat(20) { w.insert(item("m$it", activation = 0.5f)) }
        assertEquals(3, w.getSnapshot().size)
        assertTrue(w.getStats().totalInsertions >= 20L)
        assertTrue(w.getStats().totalEvictions >= 17L)
    }

    @Test
    fun `capacity eviction drops the oldest lowest-priority item`() {
        val w = wm(WorkingMemory.Config(maxItems = 2))
        w.insert(item("m1", activation = 0.5f))
        w.insert(item("m2", activation = 0.5f))
        w.insert(item("m3", activation = 0.9f))

        assertEquals(2, w.getSnapshot().size)
        assertNull(w.get("m1")) // oldest of the two tied items
        assertNotNull(w.get("m2"))
        assertNotNull(w.get("m3"))
    }

    @Test
    fun `query returns only memories relevant to the query`() {
        val w = wm()
        w.insert(item("m1", relevance = 0.8f, content = "User likes coffee in the morning"))
        w.insert(item("m2", relevance = 0.7f, content = "User prefers tea at night"))
        w.insert(item("m3", relevance = 0.5f, content = "Weather forecast for today"))

        val results = w.query("coffee morning", limit = 5)
        assertEquals(1, results.size)
        assertTrue(results[0].content.contains("coffee"))
    }

    @Test
    fun `query ranks the best keyword overlap first`() {
        val w = wm()
        w.insert(item("m1", activation = 0.7f, content = "User likes coffee in the morning"))
        w.insert(item("m2", activation = 0.6f, content = "User prefers tea at night"))

        val results = w.query("user prefers", limit = 5)
        assertTrue(results.isNotEmpty())
        assertTrue(results[0].content.contains("tea"))
    }

    @Test
    fun `getActive returns only items above the activation floor`() {
        val w = wm()
        w.insert(item("m1", activation = 0.9f))
        w.insert(item("m2", activation = 0.05f))

        val active = w.getActive(threshold = 0.3f)
        assertEquals(1, active.size)
        assertEquals("m1", active[0].id)
    }

    @Test
    fun `boostForGoal raises activation and alignment only for matching items`() {
        val w = wm()
        w.insert(item("m1", activation = 0.5f, goalAlignment = 0.2f, content = "Learn Kotlin programming"))
        w.insert(item("m2", activation = 0.5f, goalAlignment = 0.2f, content = "Weather forecast"))

        w.boostForGoal("goal_1", "Learn Kotlin")

        assertEquals(0.7f, w.get("m1")!!.activation, 0.0001f)
        assertTrue(w.get("m1")!!.goalAlignment > 0.2f)
        assertEquals(0.5f, w.get("m2")!!.activation, 0.0001f)
    }

    @Test
    fun `remove returns the item and clear empties the working memory`() {
        val w = wm()
        w.insert(item("m1"))
        w.insert(item("m2"))

        assertTrue(w.remove("m1"))
        assertNull(w.get("m1"))
        assertFalse(w.remove("m1")) // already gone

        w.clear()
        assertEquals(0, w.getSnapshot().size)
    }

    @Test
    fun `stats reflect insertions not every refresh`() {
        val w = wm(WorkingMemory.Config(maxItems = 5))
        w.insert(item("m1", activation = 0.8f))
        w.insert(item("m2", activation = 0.4f))
        w.access("m1") // one explicit access, not part of the insertion count

        val stats = w.getStats()
        assertEquals(2, stats.currentSize)
        assertEquals(2L, stats.totalInsertions)
        assertEquals(1L, stats.totalAccesses)
        // access("m1") boosted 0.8 -> 0.9, so avg = (0.9 + 0.4) / 2
        assertEquals(0.65f, stats.avgActivation, 0.0001f)
    }
}
