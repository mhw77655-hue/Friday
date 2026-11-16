package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.contract.UserPresenceEvent
import com.jarvis.app.companioncore.engine.FridayPresenceEvidenceStore
import com.jarvis.app.companioncore.engine.FridayRuntimeIntegration
import com.jarvis.app.companioncore.engine.FridayRuntimeSnapshot
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Friday runtime signal freshness, sole-writer and local presence-event tests. */
class FridayRuntimeIntegrationTest {
    @Before
    fun clearEvidenceBeforeEachTest() {
        FridayPresenceEvidenceStore.resetForTests()
    }

    @After
    fun resetEvidence() {
        FridayPresenceEvidenceStore.resetForTests()
    }

    private fun meaningfulState(
        ts: Long = 1_700_000_000_000L,
        bridgeStatus: String? = "ready"
    ) = FridayRuntimeSnapshot(
        ts = ts,
        // No validated affect/trust/bond evidence in this fixture.
        totalInteractions = 42L,
        secondsSinceLastContact = 30L,
        presenceModeName = "ACTIVE",
        bridgeStatus = bridgeStatus,
        lastUtteranceText = "a reply"
    )

    @Test
    fun `tick publishes a runtime signal without inventing affect`() {
        val binding = FakeBinding(state = meaningfulState())
        val integration = FridayRuntimeIntegration(binding)
        integration.tick()
        val signal = integration.signal.value
        assertEquals(0.0, signal.emotionVector.valence, 1e-9)
        assertEquals(0.5, signal.emotionVector.arousal, 1e-9)
        assertEquals(0.0, signal.confidence, 1e-9)
        assertEquals(0.5, signal.motivation, 1e-9)
        assertEquals("a reply", signal.lastUtteranceText)
    }

    @Test
    fun `signal is not stale after a fresh meaningful runtime tick`() {
        val clock = ManualEpochClock()
        val integration = FridayRuntimeIntegration(FakeBinding(meaningfulState(ts = clock.now)), nowMs = { clock.now })
        integration.tick()
        assertFalse(integration.stale)
    }

    @Test
    fun `empty runtime read latches staleness`() {
        val clock = ManualEpochClock()
        val integration = FridayRuntimeIntegration(
            FakeBinding(FridayRuntimeSnapshot.empty(clock.now)),
            nowMs = { clock.now }
        )
        integration.tick()
        assertTrue("empty read must not count as fresh", integration.stale)
    }

    @Test
    fun `stale trips without a fresh runtime observation and recovers`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(meaningfulState(ts = clock.now))
        val integration = FridayRuntimeIntegration(binding, nowMs = { clock.now })
        integration.tick()
        assertFalse(integration.stale)
        clock.advance(3_000L)
        assertTrue(integration.stale)
        binding.state = meaningfulState(ts = clock.now)
        integration.tick()
        assertFalse(integration.stale)
    }

    @Test
    fun `emotion layer emits neutral when Friday affect evidence is unavailable`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(meaningfulState(ts = clock.now))
        val integration = FridayRuntimeIntegration(binding, nowMs = { clock.now })
        integration.tick()
        assertEquals(0.0, integration.emotionLayer.current().valence, 1e-9)
        assertEquals(0.0, integration.emotionLayer.current().confidence, 1e-9)
    }

    @Test
    fun `conversation boundary events drive current action`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(meaningfulState(ts = clock.now, bridgeStatus = "sending (local)"))
        val integration = FridayRuntimeIntegration(binding, nowMs = { clock.now })
        integration.onUserSubmitted()
        integration.tick()
        assertEquals(CurrentAction.GENERATING, integration.signal.value.currentAction)
        assertEquals(1L, FridayPresenceEvidenceStore.state.value.interactionCount)
    }

    @Test
    fun `remote supplied current action takes precedence over local derivation`() {
        val clock = ManualEpochClock()
        val binding = FakeBinding(
            meaningfulState(ts = clock.now).copy(currentActionRaw = CurrentAction.DONE, bridgeStatus = null)
        )
        val integration = FridayRuntimeIntegration(binding, nowMs = { clock.now })
        integration.tick()
        assertEquals(CurrentAction.DONE, integration.signal.value.currentAction)
    }

    @Test
    fun `signal flow is a read only sole writer view`() {
        val integration = FridayRuntimeIntegration(FakeBinding(meaningfulState()))
        integration.tick()
        val first = integration.signal.value
        integration.tick()
        assertEquals(first, integration.signal.value)
        val flow: kotlinx.coroutines.flow.StateFlow<com.jarvis.app.companioncore.contract.CompanionSignal> =
            integration.signal
        assertEquals(first, flow.value)
    }

    @Test
    fun `user presence events are retained locally without an external core`() {
        val integration = FridayRuntimeIntegration(FakeBinding(meaningfulState()))
        val accepted = integration.sendUserPresenceEvent(
            UserPresenceEvent(
                eventType = UserPresenceEvent.EventType.DEVICE_BACKGROUNDED,
                timestamp = 1_700_000_000_000L,
                confidence = 1.0
            )
        )
        assertTrue(accepted)
        assertEquals(false, FridayPresenceEvidenceStore.state.value.appForeground)
        assertEquals("DEVICE_BACKGROUNDED", FridayPresenceEvidenceStore.state.value.lastEventType)
    }
}
