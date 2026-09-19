package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.engine.BridgePhase
import com.jarvis.app.companioncore.engine.BridgeStatusClassifier
import com.jarvis.app.companioncore.engine.CurrentActionDiscriminator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `current_action` discriminator transition table (plan §4.2.1, audit
 * A-4/M-12/R-P6). Exercises the explicit state machine:
 *
 * ```
 * IDLE ──user text submitted──▶ RECEIVING_INPUT
 * RECEIVING_INPUT ──bridge sending──▶ GENERATING (conservative: tool+gen both)
 * GENERATING ──ok + first segment ready──▶ DONE
 * DONE ──utterance completed / idle timeout──▶ IDLE
 * ```
 *
 * plus the conservative fallback (TOOL_CALL is never produced locally) and
 * dwell hysteresis (GENERATING cannot flop to IDLE on a transient error).
 */
class CurrentActionDiscriminatorTest {

    @Test
    fun `starts idle`() {
        assertEquals(CurrentAction.IDLE, CurrentActionDiscriminator().current)
    }

    @Test
    fun `user submission moves to receiving input`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        assertEquals(CurrentAction.RECEIVING_INPUT, d.current)
    }

    @Test
    fun `receiving input plus sending status moves to generating`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onBridgeStatus("sending (local)")
        assertEquals(CurrentAction.GENERATING, d.current)
    }

    @Test
    fun `retry status also moves to generating (conservative fallback)`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onBridgeStatus("retrying local (attempt 2)")
        assertEquals(CurrentAction.GENERATING, d.current)
    }

    @Test
    fun `tool path maps to generating, never tool_call (conservative fallback)`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onBridgeStatus("sending (local)")
        // The bridge exposes no tool-vs-generation distinction; plan §4.2.1
        // mandates mapping both to GENERATING. TOOL_CALL is never local.
        assertEquals(CurrentAction.GENERATING, d.current)
    }

    @Test
    fun `generating plus ok status stays generating until segment ready`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onBridgeStatus("sending (local)")
        d.onBridgeStatus("ok (local)")
        assertEquals(CurrentAction.GENERATING, d.current)
    }

    @Test
    fun `generating plus ok plus first segment moves to done`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onBridgeStatus("sending (local)")
        d.onBridgeStatus("ok (local)")
        d.onFirstSegmentReady()
        assertEquals(CurrentAction.DONE, d.current)
    }

    @Test
    fun `done plus utterance completed returns to idle`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onBridgeStatus("sending (local)")
        d.onBridgeStatus("ok (local)")
        d.onFirstSegmentReady()
        d.onUtteranceCompleted()
        assertEquals(CurrentAction.IDLE, d.current)
    }

    @Test
    fun `done falls back to idle after idle timeout`() {
        val clock = ManualEpochClock()
        val d = CurrentActionDiscriminator(now = { clock.now }, minDwellMs = 1_500L)
        d.onUserSubmitted()
        d.onBridgeStatus("sending (local)")
        d.onBridgeStatus("ok (local)")
        d.onFirstSegmentReady()
        clock.advance(CurrentActionDiscriminator.DEFAULT_DONE_IDLE_TIMEOUT_MS + 1)
        d.tickIdleTimeout()
        assertEquals(CurrentAction.IDLE, d.current)
    }

    @Test
    fun `error during generating falls back to idle after dwell`() {
        val clock = ManualEpochClock()
        val d = CurrentActionDiscriminator(now = { clock.now }, minDwellMs = 1_500L)
        d.onUserSubmitted()
        d.onBridgeStatus("sending (local)")
        clock.advance(2_000) // dwell exceeded
        d.onBridgeStatus("local brain unreachable after 3 attempts")
        assertEquals(CurrentAction.IDLE, d.current)
    }

    @Test
    fun `error before dwell does not flop the mode (hysteresis)`() {
        val clock = ManualEpochClock()
        val d = CurrentActionDiscriminator(now = { clock.now }, minDwellMs = 1_500L)
        d.onUserSubmitted()
        d.onBridgeStatus("sending (local)")
        // Transient error within the dwell window: must NOT drop to IDLE,
        // otherwise Thinking->Idle->Thinking oscillation (audit R-P6).
        d.onBridgeStatus("local brain error 500: oops")
        assertEquals(CurrentAction.GENERATING, d.current)
    }

    @Test
    fun `error does not move idle`() {
        val d = CurrentActionDiscriminator()
        d.onBridgeStatus("local brain error 500: oops")
        assertEquals(CurrentAction.IDLE, d.current)
    }

    @Test
    fun `unknown status does not change state`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onBridgeStatus("idle")
        assertEquals(CurrentAction.RECEIVING_INPUT, d.current)
    }

    @Test
    fun `classifier maps all known statuses`() {
        assertEquals(BridgePhase.SENDING, BridgeStatusClassifier.classify("sending (local)"))
        assertEquals(BridgePhase.SENDING, BridgeStatusClassifier.classify("retrying local (attempt 2)"))
        assertEquals(BridgePhase.OK, BridgeStatusClassifier.classify("ok (local)"))
        assertEquals(BridgePhase.ERROR, BridgeStatusClassifier.classify("failed to build local request"))
        assertEquals(BridgePhase.ERROR, BridgeStatusClassifier.classify("local brain unreachable after 3 attempts"))
        assertEquals(BridgePhase.ERROR, BridgeStatusClassifier.classify("parse failed: boom"))
        assertEquals(BridgePhase.UNKNOWN, BridgeStatusClassifier.classify("idle"))
        assertEquals(BridgePhase.UNKNOWN, BridgeStatusClassifier.classify(null))
    }

    // ---- latency-first seam: onFastPathAck (the orb reaches THINKING without
    // waiting on the bridge's first status observation) ----

    @Test
    fun `fast path ack moves receiving input to generating immediately`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onFastPathAck()
        assertEquals(CurrentAction.GENERATING, d.current)
    }

    @Test
    fun `fast path ack from done starts a fresh generating exchange`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onFastPathAck()
        d.onFirstSegmentReady()
        assertEquals(CurrentAction.DONE, d.current)
        d.onFastPathAck()
        assertEquals(CurrentAction.GENERATING, d.current)
    }

    @Test
    fun `fast path ack does not move idle`() {
        // No input received yet — nothing is being generated, so the ack must
        // not fabricate a generating state.
        val d = CurrentActionDiscriminator()
        d.onFastPathAck()
        assertEquals(CurrentAction.IDLE, d.current)
    }

    @Test
    fun `fast path ack does not churn an already-generating exchange`() {
        val d = CurrentActionDiscriminator()
        d.onUserSubmitted()
        d.onFastPathAck()
        assertEquals(CurrentAction.GENERATING, d.current)
        d.onFastPathAck() // no-op — stays GENERATING, no dwell churn
        assertEquals(CurrentAction.GENERATING, d.current)
    }
}
