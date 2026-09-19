package com.jarvis.app.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTunerTest {

    private fun graph(): FakeGraph = FakeGraph()
    private fun world(g: FakeGraph): WorldModelService = WorldModelService(g)
    private fun tuner(w: WorldModelService) = PersonaTuner(w)

    // ── AC1: explicit feedback -> durable adjustment persists across sessions ──

    @Test
    fun `explicit be more direct persists as a durable trait adjustment across a new session`() {
        val g = graph()
        val w = world(g)
        val adjustment = tuner(w).ingest("Please be more direct in your replies")
        assertNotNull(adjustment)
        assertEquals("directness", adjustment!!.trait)
        assertEquals("high", adjustment.value)

        // Simulate a NEW session: build a fresh tuner/service over the SAME store.
        val newSessionTuner = tuner(WorldModelService(g))
        assertEquals(
            "the adjustment must be durable and visible in a new session",
            listOf("directness"),
            newSessionTuner.adjustments().map { it.trait }
        )
        assertEquals("high", newSessionTuner.currentValue("directness"))
    }

    // ── AC2: adjustment is traceable (what changed AND what triggered it) ──

    @Test
    fun `adjustment is traceable with what changed and the triggering feedback`() {
        val g = graph()
        val w = world(g)
        tuner(w).ingest("be more formal when writing to clients")

        val adj = tuner(w).adjustments().first { it.trait == "formality" }
        assertEquals("high", adj.value)
        assertEquals("be more formal when writing to clients", adj.triggeredBy)
        // The trigger is persisted in the store, not just held in memory.
        assertTrue(
            "the triggering feedback must be recorded for audit",
            g.getHistory(WorldModelService.USER_NODE_NAME, "persona:formality")
                .single().source.contains("be more formal")
        )
    }

    // ── AC3: ambient conversational tone alone does NOT trigger ───────────

    @Test
    fun `ambient conversational tone alone does not trigger a persona adjustment`() {
        val g = graph()
        val w = world(g)
        val t = tuner(w)

        assertNull("positive ambient tone must not mutate persona", t.ingest("You're so great, thanks!"))
        assertNull("small talk must not mutate persona", t.ingest("Looking forward to the weekend."))
        assertNull("neutral observation must not mutate persona", t.ingest("The weather is nice today."))

        assertTrue("no durable persona fact may be written", t.adjustments().isEmpty())
        assertNull(t.currentValue("directness"))
    }

    // ── AC4: fully offline (no network/cloud in the path) ──────────────────

    @Test
    fun `persona adjustment works with no network - pure local graph`() {
        // FakeGraph is a pure in-memory store with no network path; the tuning
        // flow never touches a network/cloud call.
        val g = graph()
        val w = world(g)
        val adj = tuner(w).ingest("be more like that")
        assertNotNull(adj)
        assertEquals("stylePreference", adj!!.trait)
        assertEquals("moreLikeThat", adj.value)
        assertEquals("moreLikeThat", tuner(w).currentValue("stylePreference"))
    }

    // ── AC5: later contradicting feedback supersedes, not stacks ──────────

    @Test
    fun `contradicting feedback supersedes the earlier adjustment rather than stacking`() {
        val g = graph()
        val w = world(g)
        val t = tuner(w)
        t.ingest("be more direct")
        t.ingest("be less direct")

        assertEquals("the latest feedback wins", "low", t.currentValue("directness"))
        // Current single value - not stacked duplicates.
        assertEquals(1, t.adjustments().count { it.trait == "directness" })

        // The supersession is a real graph supersede, not an overwrite: the old
        // adjustment still exists with a validUntil.
        val history = g.getHistory(WorldModelService.USER_NODE_NAME, "persona:directness")
        assertEquals(2, history.size)
        assertEquals("high", history[0].`object`)
        assertEquals("low", history[1].`object`)
        assertNotNull("the earlier adjustment must be superseded (validUntil set)", history[0].validUntil)
        assertNull(history[1].validUntil)
    }
}
