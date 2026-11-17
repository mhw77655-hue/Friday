package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.AttentionTarget
import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.contract.PresenceMode
import com.jarvis.app.companioncore.presence.PresenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Presence is continuous; activity does not replace Friday's identity state. */
class PresenceEngineTest {
    private val clock = ManualEpochClock()
    private val activeModes = listOf(
        PresenceMode.PRESENT,
        PresenceMode.LISTENING,
        PresenceMode.SPEAKING,
        PresenceMode.SHUTTING_DOWN
    )

    private fun engine(initial: PresenceMode = PresenceMode.PRESENT): PresenceEngine =
        PresenceEngine(nowMs = { clock.now }, initialMode = initial)

    private fun signal(action: CurrentAction = CurrentAction.IDLE): CompanionSignal =
        CompanionSignal.neutral().copy(currentAction = action)

    @Test fun `canonical activity transitions preserve one continuous presence mode`() {
        for (from in activeModes) {
            for (to in activeModes) {
                val e = engine(from)
                e.requestTransition(to, "test", "canonical transition")
                e.tick()
                assertEquals("from=$from to=$to", to, e.mode)
                assertEquals(e.mode, e.state.value.presenceMode)
            }
        }
    }

    @Test fun `legacy asleep idle waking and thinking labels normalize to present`() {
        for (legacy in listOf(
            PresenceMode.ASLEEP,
            PresenceMode.WAKING,
            PresenceMode.IDLE,
            PresenceMode.THINKING
        )) {
            assertEquals(PresenceMode.PRESENT, engine(legacy).mode)
            val e = engine()
            e.requestTransition(legacy, "legacy-caller", "compatibility")
            e.tick()
            assertEquals(PresenceMode.PRESENT, e.mode)
        }
    }

    @Test fun `exactly one canonical presence mode is active under request pressure`() {
        val e = engine()
        repeat(80) { i ->
            e.requestTransition(activeModes[i % activeModes.size], "test", "stress")
            e.tick()
            assertTrue(e.mode in activeModes)
            assertEquals(e.mode, e.state.value.presenceMode)
        }
    }

    @Test fun `shutdown outranks listening when both queued same tick`() {
        val e = engine(PresenceMode.PRESENT)
        e.requestTransition(PresenceMode.LISTENING, "mic", "open")
        e.requestTransition(PresenceMode.SHUTTING_DOWN, "sys", "power-off")
        val rejected = mutableListOf<PresenceEngine.TransitionRequest>()
        e.onRejected = { req, _ -> rejected.add(req) }

        e.tick()

        assertEquals(PresenceMode.SHUTTING_DOWN, e.mode)
        assertEquals(1, rejected.size)
        assertEquals(PresenceMode.LISTENING, rejected.single().mode)
    }

    @Test fun `user interruption returns attention to present without entering idle`() {
        val e = engine(PresenceMode.SPEAKING)
        e.interruptToPresent("user", "new request")
        e.tick()
        assertEquals(PresenceMode.PRESENT, e.mode)
    }

    @Test fun `explicit present request wins before action derivation`() {
        val e = engine(PresenceMode.LISTENING)
        e.requestTransition(PresenceMode.PRESENT, "user", "attention returned")
        e.tick(signal = signal(CurrentAction.RECEIVING_INPUT))
        assertEquals(PresenceMode.PRESENT, e.mode)
    }

    @Test fun `stale signal falls back to present from an active interaction`() {
        val e = engine(PresenceMode.LISTENING)
        e.tick(signal = signal(), stale = true)
        assertEquals(PresenceMode.PRESENT, e.mode)
    }

    @Test fun `stale signal never interrupts orderly shutdown`() {
        val e = engine(PresenceMode.SHUTTING_DOWN)
        e.tick(signal = signal(), stale = true)
        assertEquals(PresenceMode.SHUTTING_DOWN, e.mode)
    }

    @Test fun `generation and tool use never create a thinking presence mode`() {
        val e = engine(PresenceMode.PRESENT)
        repeat(PresenceEngine.ACTION_DWELL_TICKS + 3) {
            e.tick(signal = signal(CurrentAction.GENERATING))
        }
        assertEquals(PresenceMode.PRESENT, e.mode)
        repeat(PresenceEngine.ACTION_DWELL_TICKS + 3) {
            e.tick(signal = signal(CurrentAction.TOOL_CALL))
        }
        assertEquals(PresenceMode.PRESENT, e.mode)
    }

    @Test fun `receiving input derives listening after the dwell window`() {
        val e = engine()
        repeat(PresenceEngine.ACTION_DWELL_TICKS) {
            e.tick(signal = signal(CurrentAction.RECEIVING_INPUT))
        }
        assertEquals(PresenceMode.PRESENT, e.mode)
        e.tick(signal = signal(CurrentAction.RECEIVING_INPUT))
        assertEquals(PresenceMode.LISTENING, e.mode)
    }

    @Test fun `completed work returns to present after the dwell window`() {
        val e = engine(PresenceMode.LISTENING)
        repeat(PresenceEngine.ACTION_DWELL_TICKS + 1) {
            e.tick(signal = signal(CurrentAction.DONE))
        }
        assertEquals(PresenceMode.PRESENT, e.mode)
    }

    @Test fun `action flicker cannot make Friday disappear into a work state`() {
        val e = engine()
        e.tick(signal = signal(CurrentAction.GENERATING))
        e.tick(signal = signal(CurrentAction.IDLE))
        e.tick(signal = signal(CurrentAction.IDLE))
        assertEquals(PresenceMode.PRESENT, e.mode)
    }

    @Test fun `attention target follows current activity without changing identity mode`() {
        val e = engine()
        e.tick(signal = signal(CurrentAction.GENERATING), stale = false)
        assertEquals(PresenceMode.PRESENT, e.mode)
        assertEquals(AttentionTarget.USER, e.state.value.attentionTarget)
        e.tick(signal = signal(CurrentAction.IDLE), stale = false)
        assertEquals(AttentionTarget.NONE, e.state.value.attentionTarget)
    }

    @Test fun `camera presence changes attention without storing frames`() {
        val e = engine()
        e.reportUserPresence(true)
        e.tick(signal = signal(CurrentAction.IDLE))
        assertEquals(AttentionTarget.USER, e.state.value.attentionTarget)
        e.reportUserPresence(false)
        e.tick(signal = signal(CurrentAction.IDLE))
        assertEquals(AttentionTarget.NONE, e.state.value.attentionTarget)
        assertEquals(PresenceMode.PRESENT, e.mode)
    }
}
