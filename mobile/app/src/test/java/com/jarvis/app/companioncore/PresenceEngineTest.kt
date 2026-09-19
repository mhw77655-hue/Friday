package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.contract.PresenceMode
import com.jarvis.app.companioncore.presence.PresenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.1 — Presence Engine tests.
 *
 * Exhaustive 7×7 transition coverage (spec §2.1 Testing), priority
 * arbitration under simulated simultaneous requests, the single-mode
 * invariant, stale-signal → IDLE fallback (audit M-15), and the
 * current_action dwell derivation.
 */
class PresenceEngineTest {

    private val clock = ManualEpochClock()

    private fun engine(initial: PresenceMode = PresenceMode.ASLEEP): PresenceEngine =
        PresenceEngine(nowMs = { clock.now }, initialMode = initial)

    private fun signal(action: CurrentAction = CurrentAction.IDLE): CompanionSignal =
        CompanionSignal.neutral().copy(currentAction = action)

    // ------------------------------------------------------------------ 7×7 table

    @Test
    fun `7x7 exhaustive transition table - every from to every to`() {
        val modes = PresenceMode.entries
        for (from in modes) {
            for (to in modes) {
                val e = engine(from)
                e.requestTransition(to, "test", "exhaustive")
                e.tick()
                assertEquals("from=$from to=$to should land on $to", to, e.mode)
            }
        }
    }

    @Test
    fun `single mode invariant - exactly one mode is active at all times`() {
        val e = engine()
        val modes = PresenceMode.entries
        repeat(60) { i ->
            e.requestTransition(modes[i % modes.size], "test", "stress")
            e.tick()
            // The engine exposes exactly one current mode, and the broadcast
            // CompanionState carries exactly the same mode (single-source).
            assertEquals(e.mode, e.state.value.presenceMode)
        }
    }

    // ------------------------------------------------------------------ priority

    @Test
    fun `shutdown outranks listening when both queued same tick`() {
        val e = engine(PresenceMode.IDLE)
        e.requestTransition(PresenceMode.LISTENING, "mic", "open")
        e.requestTransition(PresenceMode.SHUTTING_DOWN, "sys", "power-off")

        val rejected = mutableListOf<PresenceEngine.TransitionRequest>()
        e.onRejected = { req, _ -> rejected.add(req) }

        e.tick()
        assertEquals(PresenceMode.SHUTTING_DOWN, e.mode)
        assertEquals(1, rejected.size)
        assertEquals(PresenceMode.LISTENING, rejected.single().mode)
    }

    @Test
    fun `user interrupt outranks listening and speaking`() {
        val e = engine(PresenceMode.IDLE)
        e.requestTransition(PresenceMode.LISTENING, "mic", "open")
        e.interruptToIdle("user", "tap")
        e.tick()
        // User interrupt (450) beats listening (400) → IDLE wins.
        assertEquals(PresenceMode.IDLE, e.mode)
    }

    @Test
    fun `equal priority - most recently queued wins and loser is logged`() {
        val e = engine(PresenceMode.ASLEEP)
        e.requestTransition(PresenceMode.IDLE, "a", "first", priority = 100)
        e.requestTransition(PresenceMode.IDLE, "b", "second", priority = 100)
        val rejected = mutableListOf<PresenceEngine.TransitionRequest>()
        e.onRejected = { req, _ -> rejected.add(req) }
        e.tick()
        assertEquals(PresenceMode.IDLE, e.mode)
        assertEquals("second request wins the tie", 1, rejected.size)
    }

    @Test
    fun `explicit request wins the tick it arrives in, before action derivation`() {
        val e = engine(PresenceMode.IDLE)
        // Action says GENERATING → THINKING, but the explicit IDLE request
        // queued in the same tick wins arbitration that tick.
        e.requestTransition(PresenceMode.IDLE, "user", "stay")
        e.tick(signal = signal(CurrentAction.GENERATING))
        assertEquals(PresenceMode.IDLE, e.mode)
    }

    // ------------------------------------------------------------------ stale fallback

    @Test
    fun `stale signal falls back to IDLE from an active mode`() {
        val e = engine(PresenceMode.THINKING)
        e.tick(signal = signal(), stale = true)
        assertEquals(PresenceMode.IDLE, e.mode)
    }

    @Test
    fun `stale signal does not yank an orderly shutdown`() {
        val e = engine(PresenceMode.SHUTTING_DOWN)
        e.tick(signal = signal(), stale = true)
        assertEquals(PresenceMode.SHUTTING_DOWN, e.mode)
    }

    @Test
    fun `stale signal keeps IDLE at IDLE`() {
        val e = engine(PresenceMode.IDLE)
        e.tick(signal = signal(), stale = true)
        assertEquals(PresenceMode.IDLE, e.mode)
    }

    @Test
    fun `stale signal does not yank the waking bloom`() {
        val e = engine(PresenceMode.WAKING)
        e.tick(signal = signal(), stale = true)
        // The startup handoff owns WAKING→IDLE (audit F7); a stale signal must
        // not cut the bloom short and snap the orb straight to IDLE.
        assertEquals(PresenceMode.WAKING, e.mode)
    }

    @Test
    fun `fresh signal does not force IDLE`() {
        val e = engine(PresenceMode.THINKING)
        e.tick(signal = signal(), stale = false)
        assertEquals(PresenceMode.THINKING, e.mode)
    }

    // ------------------------------------------------------------------ action derivation

    @Test
    fun `receiving input derives listening after the dwell window`() {
        val e = engine(PresenceMode.IDLE)
        // The suggestion is established on its first occurrence, then must
        // persist ACTION_DWELL_TICKS consecutive ticks (1 + N = N+1 total).
        repeat(PresenceEngine.ACTION_DWELL_TICKS) {
            e.tick(signal = signal(CurrentAction.RECEIVING_INPUT))
        }
        assertEquals("dwell not yet met", PresenceMode.IDLE, e.mode)
        e.tick(signal = signal(CurrentAction.RECEIVING_INPUT))
        assertEquals(PresenceMode.LISTENING, e.mode)
    }

    @Test
    fun `generating derives thinking after the dwell window`() {
        val e = engine(PresenceMode.IDLE)
        repeat(PresenceEngine.ACTION_DWELL_TICKS) {
            e.tick(signal = signal(CurrentAction.GENERATING))
        }
        assertEquals("dwell not yet met", PresenceMode.IDLE, e.mode)
        e.tick(signal = signal(CurrentAction.GENERATING))
        assertEquals(PresenceMode.THINKING, e.mode)
    }

    @Test
    fun `idle action returns from thinking after the dwell window`() {
        val e = engine(PresenceMode.THINKING)
        repeat(PresenceEngine.ACTION_DWELL_TICKS) {
            e.tick(signal = signal(CurrentAction.IDLE))
        }
        assertEquals("dwell not yet met", PresenceMode.THINKING, e.mode)
        e.tick(signal = signal(CurrentAction.IDLE))
        assertEquals(PresenceMode.IDLE, e.mode)
    }

    @Test
    fun `action flicker is damped - a blip does not move the mode`() {
        val e = engine(PresenceMode.IDLE)
        e.tick(signal = signal(CurrentAction.GENERATING)) // suggestion set, dwell 0
        e.tick(signal = signal(CurrentAction.IDLE))       // suggestion flips to IDLE
        e.tick(signal = signal(CurrentAction.IDLE))       // no-op
        assertEquals("a one-tick GENERATING blip must not move the mode", PresenceMode.IDLE, e.mode)
    }

    // ------------------------------------------------------------------ emitted state

    @Test
    fun `emitted companion state mirrors the active mode and emotion`() {
        val e = engine(PresenceMode.LISTENING)
        e.tick(
            signal = signal(CurrentAction.RECEIVING_INPUT),
            emotion = com.jarvis.app.companioncore.contract.EmotionSnapshot(
                valence = 0.7, arousal = 0.9, confidence = 0.5, timestamp = clock.now
            )
        )
        val emitted = e.state.value
        assertEquals(PresenceMode.LISTENING, emitted.presenceMode)
        assertEquals(0.7, emitted.emotionVector.valence, 0.0)
    }

    @Test
    fun `attention target follows activity`() {
        val e = engine(PresenceMode.IDLE)
        e.tick(signal = signal(CurrentAction.GENERATING), stale = false)
        assertEquals(
            com.jarvis.app.companioncore.contract.AttentionTarget.USER,
            e.state.value.attentionTarget
        )
        e.tick(signal = signal(CurrentAction.IDLE), stale = false)
        assertEquals(
            com.jarvis.app.companioncore.contract.AttentionTarget.NONE,
            e.state.value.attentionTarget
        )
    }
}
