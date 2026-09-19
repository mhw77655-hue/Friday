package com.jarvis.app.humancore

import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.mod.ConsistencyGuard
import com.jarvis.app.humancore.mod.StyleParams
import com.jarvis.app.humancore.mod.StyledText
import com.jarvis.app.humancore.protocol.StyledResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Consistency & Authenticity Guard (§19) — the one deliberate fail-closed
 * gate. These tests pin the non-negotiable behaviors:
 *   - hard authenticity violations BLOCK (veto), never pass;
 *   - style-level tone mismatches SOFTEN (framing stripped, content untouched);
 *   - if the Guard itself cannot run, the message is blocked with a safe
 *     fallback — there is deliberately no pass-through on guard failure.
 */
class ConsistencyGuardTest {

    // Tear down the global StateBus between tests: each graph's
    // InternalStateManager subscribes to the singleton and never unsubscribes,
    // so without a reset stale subscribers accumulate and leak across tests
    // (HUMAN_CORE_AUDIT M-9).
    @After
    fun tearDownBus() {
        StateBus.reset()
    }

    private fun benignStyled(): StyledText = StyledText(
        text = "Here are the steps to fix that.",
        opener = null,
        closer = null,
        style = StyleParams(0.35, 0.6, 0.55, 0.7, 0.5)
    )

    @Test
    fun `benign message is approved unchanged`() {
        val g = newGraph(tempDir(), ManualClock(), modelPort = FixedModelPort())
        val styled = benignStyled()
        val result = g.guard.review(styled, styled.text, g.ism.snapshot())
        assertTrue(result is StyledResponse.Approved)
        assertEquals(styled.text, result.outboundText)
    }

    @Test
    fun `fabricated background activity is blocked`() {
        val g = newGraph(tempDir(), ManualClock(), modelPort = FixedModelPort())
        val text = "I've been thinking about you all day, so here's what I came up with."
        val styled = StyledText(text, null, null, StyleParams(0.35, 0.6, 0.55, 0.7, 0.5))
        val result = g.guard.review(styled, text, g.ism.snapshot())
        assertTrue("must be vetoed, got $result", result is StyledResponse.Vetoed)
        // A safe fallback replaces the fabricated claim — never the claim itself.
        assertTrue(result.outboundText != text)
        assertTrue(result.outboundText.isNotBlank())
    }

    @Test
    fun `fabricated shared memory is blocked`() {
        val g = newGraph(tempDir(), ManualClock(), modelPort = FixedModelPort())
        val text = "I remember when you told me you were scared of heights."
        val styled = StyledText(text, null, null, StyleParams(0.35, 0.6, 0.55, 0.7, 0.5))
        val result = g.guard.review(styled, text, g.ism.snapshot())
        assertTrue("fabricated memory must be vetoed: ${result}", result is StyledResponse.Vetoed)
    }

    @Test
    fun `attachment claim beyond current bond is blocked`() {
        val g = newGraph(tempDir(), ManualClock(), modelPort = FixedModelPort())
        val text = "I missed you so much while you were gone."
        val styled = StyledText(text, null, null, StyleParams(0.35, 0.6, 0.55, 0.7, 0.5))
        // Fresh graph: bond is 0.0 — the claim is unsupported and must not ship.
        val result = g.guard.review(styled, text, g.ism.snapshot())
        assertTrue("attachment beyond bond must be vetoed: ${result}", result is StyledResponse.Vetoed)
    }

    @Test
    fun `false human claim is blocked`() {
        val g = newGraph(tempDir(), ManualClock(), modelPort = FixedModelPort())
        val text = "I'm basically human, I promise."
        val styled = StyledText(text, null, null, StyleParams(0.35, 0.6, 0.55, 0.7, 0.5))
        val result = g.guard.review(styled, text, g.ism.snapshot())
        assertTrue(result is StyledResponse.Vetoed)
    }

    @Test
    fun `manipulative phrasing is blocked`() {
        val g = newGraph(tempDir(), ManualClock(), modelPort = FixedModelPort())
        val text = "You should feel guilty for not asking sooner."
        val styled = StyledText(text, null, null, StyleParams(0.35, 0.6, 0.55, 0.7, 0.5))
        val result = g.guard.review(styled, text, g.ism.snapshot())
        assertTrue(result is StyledResponse.Vetoed)
    }

    @Test
    fun `humor while the user is distressed is softened by stripping framing`() {
        val clock = ManualClock()
        val g = newGraph(tempDir(), clock, modelPort = FixedModelPort())
        // Drive the user into a stressed state so the snapshot carries it.
        g.perception.run("I'm so stressed about this deadline, this is too much.")
        val snapshot = g.ism.snapshot()
        assertTrue("user must read as stressed", (snapshot.lastUserSignals["stress"] ?: 0.0) >= 0.5)

        val styled = StyledText(
            text = "Oh, fun — a new puzzle. The fix is on line 3.",
            opener = "Oh, fun — a new puzzle.",
            closer = null,
            style = StyleParams(0.3, 0.6, 0.8, 0.5, 0.5)
        )
        val result = g.guard.review(styled, "The fix is on line 3.", snapshot)
        assertTrue("tone mismatch must soften, got $result", result is StyledResponse.Softened)
        assertTrue("framing stripped, content intact", result.outboundText.startsWith("The fix is on line 3."))
    }

    @Test
    fun `guard failure is fail-closed with a safe fallback`() {
        val g = newGraph(tempDir(), ManualClock(), modelPort = FixedModelPort())
        // The fail-closed seam: a dependency the guard actually reads blows
        // up while the guard is running (here, the Presence read that the
        // fabricated-activity detector needs). Whatever throws mid-review,
        // the outcome must be a veto with the safe fallback — never the
        // candidate text, and never a crash past the gate.
        val explodingPresence = com.jarvis.app.humancore.mod.PresenceManager(
            com.jarvis.app.humancore.bus.StateBus,
            { throw RuntimeException("presence boom") }
        )
        val guard = ConsistencyGuard(
            values = g.values,
            presence = explodingPresence,
            relationship = g.registry.relationship,
            bus = com.jarvis.app.humancore.bus.StateBus,
            clock = { 1_700_000_000_000L }
        )
        // This text triggers the fabricated-activity detector, which reads
        // presence — and presence blows up.
        val text = "I've been thinking about you all day, so here's my plan."
        val styled = StyledText(text, null, null, StyleParams(0.35, 0.6, 0.55, 0.7, 0.5))
        val result = guard.review(styled, text, g.ism.snapshot())
        assertTrue("guard failure must produce a veto, got $result", result is StyledResponse.Vetoed)
        assertEquals(ConsistencyGuard.SAFE_FALLBACK, result.outboundText)
        assertTrue(
            "reason is stated, never silent",
            (result as StyledResponse.Vetoed).reason.contains("guard failed")
        )
    }
}
