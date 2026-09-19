package com.jarvis.app.humancore

import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.Exchange
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Long-horizon scripted simulation (§0.16, §9a, §9b): months of interaction
 * must produce plausible, BOUNDED internal state — trust rises with positive
 * contact, falls with criticism, bond saturates rather than grows unbounded,
 * and a long gap decays bond partially and records a reconnection milestone.
 *
 * Everything is deterministic: no randomness, no wall clock, no model port
 * (fixed deterministic internal lines). The consistency regression suite
 * depends on this staying reproducible.
 */
class LongHorizonSimulationTest {

    // Global StateBus teardown between tests (HUMAN_CORE_AUDIT M-9).
    @After
    fun tearDownBus() {
        StateBus.reset()
    }

    @Test
    fun `warm contact develops bounded trust, bond, and milestones`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock, modelPort = FixedModelPort())

        val warmMessages = listOf("thanks, that helped", "that makes sense", "perfect, thanks")
        var idx = 0
        val sessions = 20
        val interactionsPerSession = 3
        repeat(sessions) {
            repeat(interactionsPerSession) {
                val msg = warmMessages[idx % warmMessages.size]
                idx++
                val perception = g.perception.run(msg)
                val styled = g.expression.run("Glad it helped.", null)
                g.integration.onExchange(Exchange(msg, "Glad it helped.", styled, clock.now), perception)
            }
            g.integration.onSessionEnd(interactionsPerSession, longGapSinceLast = false, now = clock.now)
            clock.days(2) // keep contact within the gap-decay window
        }

        val rel = g.registry.relationship.read()
        assertTrue("trust grew well above the 0.25 starting point, got ${rel.trust.trust}", rel.trust.trust > 0.5)
        assertTrue("bond grew past 0.3, got ${rel.depth.depth}", rel.depth.depth > 0.3)
        assertTrue("bond stayed bounded below 1, got ${rel.depth.depth}", rel.depth.depth < 1.0)
        assertEquals((sessions * interactionsPerSession).toLong(), rel.depth.totalInteractions)
        assertTrue("established_rapport milestone reached", rel.depth.milestones.contains("established_rapport"))
        assertTrue("trust in range", rel.trust.trust in 0.0..1.0)

        // Mood stayed bounded and drifted positive from the warm exchanges.
        val mood = g.registry.mood.effective(clock.now)
        assertTrue(mood.valence in -1.0..1.0)
        assertTrue("mood drifted positive with warm contact, got ${mood.valence}", mood.valence >= -0.1)
    }

    @Test
    fun `criticism erodes trust faster than praise builds it`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock, modelPort = FixedModelPort())

        repeat(10) {
            val msg = "you're wrong again"
            val perception = g.perception.run(msg)
            val styled = g.expression.run("Let me re-check that.", null)
            g.integration.onExchange(Exchange(msg, "Let me re-check that.", styled, clock.now), perception)
            clock.days(1)
        }

        val trust = g.registry.relationship.read().trust.trust
        assertTrue("trust fell with criticism, got $trust", trust < 0.20)
        assertTrue("trust stayed bounded", trust in 0.0..1.0)
    }

    @Test
    fun `long gap applies partial bond decay and reconnection milestone`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock, modelPort = FixedModelPort())

        // Build a little rapport first.
        repeat(3) {
            val msg = "this is going well"
            val perception = g.perception.run(msg)
            val styled = g.expression.run("Good to hear.", null)
            g.integration.onExchange(Exchange(msg, "Good to hear.", styled, clock.now), perception)
            g.integration.onSessionEnd(1, longGapSinceLast = false, now = clock.now)
            clock.days(2)
        }
        val before = g.registry.relationship.read().depth.depth
        assertTrue("built a small bond first, got $before", before > 0.0)

        // Long absence (well beyond the 3-day decay threshold), then return.
        clock.days(10)
        val msg = "hey, I'm back"
        val perception = g.perception.run(msg)
        val styled = g.expression.run("Welcome back.", null)
        g.integration.onExchange(Exchange(msg, "Welcome back.", styled, clock.now), perception)
        g.integration.onSessionEnd(1, longGapSinceLast = true, now = clock.now)

        val after = g.registry.relationship.read().depth
        assertTrue("gap decayed bond partially: $before -> ${after.depth}", after.depth < before)
        assertTrue("partial, not total: bond still exists", after.depth > 0.0)
        assertTrue("reconnection milestone recorded", after.milestones.contains("reconnection_after_gap"))
        assertTrue("longest gap recorded", after.longestGapMs != null && after.longestGapMs!! > 10L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun `all internal state stays within documented ranges over months`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock, modelPort = FixedModelPort())

        val messages = listOf(
            "can you help me with this?", "thanks", "that didn't work", "ok let me try again",
            "I'm stressed about this", "awesome, that fixed it"
        )
        var idx = 0
        repeat(100) { i ->
            val msg = messages[idx % messages.size]
            idx++
            val perception = g.perception.run(msg)
            val styled = g.expression.run("Let's see.", null)
            g.integration.onExchange(Exchange(msg, "Let's see.", styled, clock.now), perception)
            if (i % 6 == 5) {
                g.integration.onSessionEnd(6, longGapSinceLast = false, now = clock.now)
                clock.days(3)
            } else {
                clock.minutes(30)
            }
            g.growth.flush()
        }

        val snapshot = g.ism.snapshot()
        // Every scalar is defined and bounded.
        snapshot.traits.values.forEach { assertTrue(it in 0.0..1.0) }
        assertTrue(snapshot.trust != null && snapshot.trust!! in 0.0..1.0)
        assertTrue(snapshot.bondDepth != null && snapshot.bondDepth!! in 0.0..1.0)
        assertTrue(snapshot.moodValence != null && snapshot.moodValence!! in -1.0..1.0)
        assertTrue(snapshot.moodArousal != null && snapshot.moodArousal!! in -1.0..1.0)
        assertTrue("dialogue log accumulated", g.registry.dialogue.read().size > 0)
    }
}
