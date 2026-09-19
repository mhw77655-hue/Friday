package com.jarvis.app.identity

import com.jarvis.app.cognitive.ContextWindowAssembler
import com.jarvis.app.cognitive.ReferenceStore
import com.jarvis.app.cognitive.SalienceScorer
import com.jarvis.app.cognitive.TopicTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserModelTest {

    private fun graph(): FakeGraph = FakeGraph()
    private fun world(graph: FakeGraph): WorldModelService {
        WorldModelService(graph).registerEntity(WorldModelService.USER_NODE_NAME, EntityType.USER)
        return WorldModelService(graph)
    }

    // ── AC: single ambiguous utterance is NOT persisted ─────────────────────

    @Test
    fun `single ambiguous utterance does not become a durable preference`() {
        val g = graph()
        val profile = UserProfile(world(g))
        val update = profile.ingestUtterance("Maybe you could be more concise sometimes", "session-1")
        assertNull("a single ambiguous utterance must not write a durable preference", update)
        assertNull(profile.getPreference("communicationStyle"))
        assertTrue(profile.allPreferences().isEmpty())
    }

    // ── AC: explicit statement IS persisted ─────────────────────────────────

    @Test
    fun `explicit statement becomes a durable preference on the user node`() {
        val g = graph()
        val profile = UserProfile(world(g))
        val update = profile.ingestUtterance("I prefer detailed answers", "session-1")
        assertNotNull(update)
        assertEquals(UserProfile.PromotionReason.EXPLICIT, update!!.reason)
        assertEquals("detailed answers", profile.getPreference("communicationStyle"))
        // Written as a fact on the Venon node via WorldModelService -> MemoryGraphStore.
        assertEquals(1, g.getHistory("Venon", "preference:communicationStyle").size)
    }

    // ── AC: repeated pattern across sessions IS persisted ───────────────────

    @Test
    fun `repeated preference across distinct sessions becomes durable, not a single mention`() {
        val g = graph()
        val profile = UserProfile(world(g))
        // First mention alone is NOT durable.
        assertNull(profile.ingestUtterance("I'd prefer dark mode", "session-1"))
        assertNull(profile.getPreference("appearance"))
        // Repeats in a second distinct session -> durable.
        val update = profile.ingestUtterance("I'd prefer dark mode", "session-2")
        assertNotNull(update)
        assertEquals(UserProfile.PromotionReason.REPEATED_PATTERN, update!!.reason)
        assertEquals("dark mode", profile.getPreference("appearance"))
    }

    // ── AC: contradiction supersedes via MemoryGraphStore supersession ──────

    @Test
    fun `contradicting durable preference supersedes old value not overwrite it`() {
        val g = graph()
        val world = world(g)
        val profile = UserProfile(world)
        profile.setPreference("communicationStyle", "brief", "profile")
        profile.setPreference("communicationStyle", "detailed", "profile")

        assertEquals("detailed", profile.getPreference("communicationStyle"))
        // Both entries remain: the old one is superseded (validUntil set), never deleted.
        val history = g.getHistory("Venon", "preference:communicationStyle")
        assertEquals(2, history.size)
        assertEquals("brief", history[0].`object`)
        assertEquals("detailed", history[1].`object`)
        assertNotNull("the superseded value must keep a validUntil, not be overwritten", history[0].validUntil)
        assertNull(history[1].validUntil)
    }

    // ── AC: mental-state estimate varies per turn while profile stays stable ──

    @Test
    fun `mental-state estimate changes turn to turn while the durable profile does not`() {
        val g = graph()
        val world = world(g)
        val profile = UserProfile(world)
        profile.setPreference("communicationStyle", "brief")

        val est = UserMentalStateEstimator()
        val h1 = est.estimateForTurn("Why do the logs keep failing?")
        val h2 = est.estimateForTurn("Fix this NOW!")
        val h3 = est.estimateForTurn("I prefer detailed answers")

        assertNotEquals("estimate must change turn-to-turn", h1.mood, h2.mood)
        assertEquals("curious", h1.mood)
        assertEquals("urgent", h2.mood)
        assertTrue(h2.unstatedNeed.contains("speed"))

        // Durable profile unchanged by any turn.
        assertEquals("brief", profile.getPreference("communicationStyle"))
        // The estimator itself persists nothing.
        assertEquals(1, g.getHistory("Venon", "preference:communicationStyle").size)
    }

    // ── AC: ContextWindowAssembler incorporates the estimate as an additional ──
    // ──  signal without writing to durable memory ───────────────────────────

    @Test
    fun `context window incorporates the mental-state estimate without any durable write`() {
        val tt = TopicTracker()
        val scorer = SalienceScorer(ReferenceStore())
        tt.recordTurn(0, "How does the weather look today?")
        val assembler = ContextWindowAssembler(
            topicTracker = tt,
            salienceScorer = scorer,
            mentalStateEstimator = UserMentalStateEstimator()
        )

        val window = assembler.assemble(0, tt.currentSegmentId(), "How does the weather look today?")
        assertNotNull("mental-state estimate must be present as an additional signal", window.mentalState)
        assertEquals("seek information", window.mentalState!!.goal)

        // And it is NOT durable: no graph/estimator write occurs through the assembler.
        // (UserMentalStateEstimator has no storage target; enrichment is purely additive.)
        val prompt = assembler.formatForPrompt(window)
        assertTrue(prompt.contains("Estimated user mental state this turn"))
    }

    @Test
    fun `context window has null mental state when no estimator wired`() {
        val tt = TopicTracker()
        tt.recordTurn(0, "hello there")
        val assembler = ContextWindowAssembler(tt, SalienceScorer(ReferenceStore()))
        val window = assembler.assemble(0, tt.currentSegmentId(), "hello there")
        assertNull(window.mentalState)
    }
}
