package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.contract.UserPresenceEvent
import com.jarvis.app.companioncore.engine.HumanCoreIntegration
import com.jarvis.app.companioncore.engine.RawCoreState
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.store.FileStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * HumanCoreIntegration (§2.30): staleness fallback, sole-writer discipline,
 * conversation-boundary events, and the outward `UserPresenceEvent` path via
 * the additive `HumanCore.adviseUserPresence` facade (audit A-7/V-2).
 */
class HumanCoreIntegrationTest {

    @After
    fun tearDownBus() {
        StateBus.reset()
    }

    private fun meaningfulState(
        ts: Long = 1_700_000_000_000L,
        bridgeStatus: String? = null
    ) = RawCoreState(
        ts = ts,
        moodValence = 0.6,
        moodArousal = 0.7,
        lastUserConfidence = 0.9,
        trust = 0.8,
        bondDepth = 0.5,
        totalInteractions = 42L,
        secondsSinceLastContact = 30L,
        lastUserSignals = emptyMap(),
        presenceModeName = "ACTIVE",
        currentActionRaw = null,
        bridgeStatus = bridgeStatus,
        lastUtteranceText = "a reply",
        fabricationEvidence = false
    )

    @Test
    fun `tick publishes a mapped signal on the state flow`() {
        val binding = FakeBinding(state = meaningfulState())
        val integration = HumanCoreIntegration(binding)
        integration.tick()
        val s = integration.signal.value
        assertEquals(0.6, s.emotionVector.valence, 1e-9)
        // confidence = 0.25 + 0.35*|0.6| + 0.4*0.8 = 0.78 (documented heuristic)
        assertEquals(0.78, s.confidence, 1e-9)
        assertEquals("a reply", s.lastUtteranceText)
    }

    @Test
    fun `signal is not stale after a fresh meaningful tick`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(state = meaningfulState(ts = clock.now))
        val integration = HumanCoreIntegration(binding, nowMs = { clock.now })
        integration.tick()
        assertFalse(integration.stale)
    }

    @Test
    fun `empty read latches staleness (hc uninitialized)`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(state = RawCoreState.empty(ts = clock.now))
        val integration = HumanCoreIntegration(binding, nowMs = { clock.now })
        integration.tick()
        assertTrue("empty read must not count as fresh", integration.stale)
    }

    @Test
    fun `stale trips when no fresh signal arrives within the threshold`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(state = meaningfulState(ts = clock.now))
        val integration = HumanCoreIntegration(binding, nowMs = { clock.now })
        integration.tick()
        assertFalse(integration.stale)
        // No ticks for 3s — staleness is live-computed, so it trips without a tick.
        clock.advance(3_000L)
        assertTrue(integration.stale)
        // A fresh meaningful read re-arms it.
        integration.tick()
        assertFalse(integration.stale)
    }

    @Test
    fun `emotion layer falls back to neutral when the signal is stale`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(state = meaningfulState(ts = clock.now))
        val integration = HumanCoreIntegration(binding, nowMs = { clock.now })
        integration.tick()
        // HC goes down: reads become empty, staleness latches, next tick
        // propagates neutral to the emotion layer.
        binding.state = RawCoreState.empty(ts = clock.now)
        clock.advance(3_000L)
        integration.tick()
        assertEquals(0.0, integration.emotionLayer.current().valence, 1e-9)
    }

    @Test
    fun `conversation boundary events drive current action`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(
            state = meaningfulState(ts = clock.now, bridgeStatus = "sending (local)")
        )
        val integration = HumanCoreIntegration(binding, nowMs = { clock.now })
        integration.onUserSubmitted()
        integration.tick()
        assertEquals(CurrentAction.GENERATING, integration.signal.value.currentAction)
    }

    @Test
    fun `remote-supplied current action wins over derived`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(
            state = meaningfulState(ts = clock.now).copy(
                currentActionRaw = CurrentAction.DONE,
                bridgeStatus = null
            )
        )
        val integration = HumanCoreIntegration(binding, nowMs = { clock.now })
        integration.tick()
        assertEquals(CurrentAction.DONE, integration.signal.value.currentAction)
    }

    @Test
    fun `sole writer - signal flow is the immutable view`() {
        // Structural sole-writer assertions (audit R-I6): the flow is exposed
        // as a read-only StateFlow (the MutableStateFlow stays private inside
        // HumanCoreIntegration), and re-ticking deterministically replaces the
        // snapshot without any external mutation path.
        val binding = FakeBinding(state = meaningfulState())
        val integration = HumanCoreIntegration(binding)
        integration.tick()
        val first = integration.signal.value
        integration.tick()
        assertEquals(first, integration.signal.value)
        // The public contract is StateFlow; callers cannot set .value.
        val flow: kotlinx.coroutines.flow.StateFlow<com.jarvis.app.companioncore.contract.CompanionSignal> = integration.signal
        assertEquals(first, flow.value)
    }

    @Test
    fun `user presence event delivers through the additive facade`() {
        // Init HC against a temp dir so the facade has a graph to forward to.
        val dir = File(System.getProperty("java.io.tmpdir"), "cc-presence-test-${System.nanoTime()}")
        dir.mkdirs()
        HumanCore.init(storage = FileStorage(dir))

        val binding = FakeBinding(state = meaningfulState())
        val integration = HumanCoreIntegration(binding)

        val accepted = integration.sendUserPresenceEvent(
            UserPresenceEvent(
                eventType = UserPresenceEvent.EventType.DEVICE_BACKGROUNDED,
                timestamp = 1_700_000_000_000L,
                confidence = 0.9
            )
        )
        assertTrue(accepted)
    }
}
