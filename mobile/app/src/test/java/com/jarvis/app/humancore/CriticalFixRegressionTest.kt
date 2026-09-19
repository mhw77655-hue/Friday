package com.jarvis.app.humancore

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.mod.UserAdaptation
import com.jarvis.app.humancore.protocol.Exchange
import com.jarvis.app.humancore.store.PreferenceSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression tests that PIN the behavior of the audit fixes (HUMAN_CORE_AUDIT
 * findings C-3, C-4, C-6, m-5). Each test would FAIL against the pre-fix code,
 * so these are the evidence that the fix is real and stays fixed.
 */
class CriticalFixRegressionTest {

    // Global StateBus teardown between tests (HUMAN_CORE_AUDIT M-9).
    @After
    fun tearDownBus() {
        StateBus.reset()
    }

    /** C-3 — a nudge must fold elapsed decay, not restart the clock. */
    @Test
    fun `mood nudge folds elapsed decay instead of erasing it`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock)
        val mood = g.registry.mood

        mood.applyNudge(0.8, 0.0, clock.now)
        assertEquals(0.8, mood.effective(clock.now).valence, 1e-9)

        // 2 hours later, half-life 1h: effective valence ~0.2.
        clock.hours(2)
        val decayed = mood.effective(clock.now).valence
        assertTrue("valence must decay toward baseline, got $decayed", decayed < 0.5)

        // A -0.2 nudge must build on the DECAYED value (~0.2 - 0.2 ~= 0.0),
        // not on the stored 0.8 (which would give 0.6). The pre-fix code did
        // the latter, making mood permanently sticky (C-3).
        mood.applyNudge(-0.2, 0.0, clock.now)
        val after = mood.effective(clock.now).valence
        assertTrue("nudge must start from the decayed value, got $after", after < 0.3)
    }

    /** C-4 — trust event history rolls over to the NEWEST events. */
    @Test
    fun `trust event history keeps the newest entries`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock)
        val rel = g.registry.relationship

        repeat(70) { i ->
            rel.applyTrust(0.9, 0.5, "event $i", clock.now)
            clock.minutes(1)
        }

        val events = rel.read().trust.events
        assertEquals(60, events.size)
        assertTrue("newest event retained", events.last().summary == "event 69")
        assertTrue("oldest event pruned", events.none { it.summary == "event 0" })
    }

    /** C-6 — explicit adaptation preferences survive a process restart. */
    @Test
    fun `explicit adaptation preferences survive process death`() {
        val clock = ManualClock()
        val dir = tempDir()

        val g = newGraph(dir, clock)
        g.adaptation.captureFromMessage("please be more concise", clock.now)
        val explicit = g.adaptation.allNumeric()[UserAdaptation.DIM_VERBOSITY]
        assertEquals("captured as explicit", PreferenceSource.EXPLICIT, explicit?.source)

        // Simulate process death: a brand-new graph on the same storage.
        val g2 = newGraph(dir, clock)
        val restored = g2.adaptation.allNumeric()[UserAdaptation.DIM_VERBOSITY]
        assertEquals("explicit preference must persist", PreferenceSource.EXPLICIT, restored?.source)
        assertEquals(explicit?.value, restored?.value)
    }

    /** m-5 — the trust audit trail carries the real before/after delta. */
    @Test
    fun `trust delta events carry the real change, not a false zero`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock, modelPort = FixedModelPort())
        StateBus.clearAudit()

        val perception = g.perception.run("you're wrong about that")
        val styled = g.expression.run("Okay.", null)
        g.integration.onExchange(
            Exchange("you're wrong about that", "Okay.", styled, clock.now),
            perception
        )

        val deltas = StateBus.auditTrail().filterIsInstance<HcEvent.TrustDelta>()
        assertTrue("a trust delta must be published", deltas.isNotEmpty())
        // Initial trust 0.5, a criticism observation drops it — the delta is
        // real and negative, never the hardcoded 0.0 of the pre-fix code.
        assertTrue("delta must reflect the real change, got ${deltas.first().delta}", deltas.first().delta != 0.0)
    }

    /**
     * m-13 — a corrupt Identity store must publish a severity-1 event and fall
     * back to the documented baseline, never fail silently. The pre-fix code
     * had a comment claiming "severity-1 event" but emitted nothing.
     */
    @Test
    fun `corrupt identity store publishes severity-1 integrity event`() {
        val clock = ManualClock()
        val dir = tempDir()

        // Write a corrupt identity file BEFORE the graph loads it.
        File(dir, "humancore/identity.json").apply {
            parentFile?.mkdirs()
            writeText("{ this is not valid json")
        }

        val g = newGraph(dir, clock)

        val integrity = StateBus.auditTrail().filterIsInstance<HcEvent.IdentityIntegrity>()
        assertTrue("must publish IdentityIntegrity, got none", integrity.isNotEmpty())
        assertEquals("identity loss is severity-1", 1, integrity.first().severity)
        // And the subsystem must keep functioning on the documented fallback.
        assertTrue("must fall back to the baseline identity", g.identityKernel.isFallback())
    }
}
