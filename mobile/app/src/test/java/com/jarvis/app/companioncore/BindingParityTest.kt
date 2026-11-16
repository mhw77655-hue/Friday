package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.engine.FridayLocalRuntimeBinding
import com.jarvis.app.companioncore.engine.FridayPresenceEvidenceStore
import com.jarvis.app.companioncore.engine.FridayRuntimeIntegration
import com.jarvis.app.companioncore.engine.FridayRuntimeSnapshot
import com.jarvis.app.companioncore.engine.RemoteHumanCoreBinding
import com.jarvis.app.companioncore.engine.SignalMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local and optional remote runtime adapters map equivalent validated evidence. */
class BindingParityTest {
    @Test
    fun `equivalent runtime observations map to identical CompanionSignal`() {
        val equivalent = FridayRuntimeSnapshot(
            ts = 1_700_000_000_000L,
            totalInteractions = 42L,
            secondsSinceLastContact = 30L,
            presenceModeName = "ACTIVE",
            bridgeStatus = "sending (local)",
            lastUtteranceText = "a reply"
        )
        val local = SignalMapper.map(equivalent, CurrentAction.GENERATING)
        val remote = SignalMapper.map(
            equivalent.copy(currentActionRaw = CurrentAction.GENERATING),
            CurrentAction.GENERATING
        )
        assertEquals(local, remote)
        assertEquals(0.0, local.confidence, 1e-9)
        assertEquals(0.5, local.motivation, 1e-9)
    }

    @Test
    fun `local binding reads only Friday runtime evidence`() {
        val clock = ManualEpochClock()
        val presence = FridayPresenceEvidenceStore.Snapshot(
            userPresent = true,
            appForeground = true,
            lastInteractionAtMs = clock.now - 30_000L,
            interactionCount = 42L
        )
        val binding = FridayLocalRuntimeBinding(
            readStatus = { "sending (local)" },
            readLastReply = { "a reply" },
            readPresence = { presence },
            nowMs = { clock.now }
        )
        val raw = binding.read()
        assertNull(raw.moodValence)
        assertNull(raw.moodArousal)
        assertNull(raw.trust)
        assertEquals(42L, raw.totalInteractions)
        assertEquals(30L, raw.secondsSinceLastContact)
        assertEquals("a reply", raw.lastUtteranceText)
        assertEquals("ACTIVE", raw.presenceModeName)
    }

    @Test
    fun `optional remote runtime adapter parses documented fields`() {
        val binding = RemoteHumanCoreBinding(
            endpointUrl = "test://status",
            httpGet = { jsonStatusPayload },
            refreshIntervalMs = 60_000L
        )
        binding.refreshNow()
        assertTrue(binding.isHealthy())
        val raw = binding.read()
        assertNull(raw.moodValence)
        assertNull(raw.trust)
        assertEquals(42L, raw.totalInteractions)
        assertEquals(30L, raw.secondsSinceLastContact)
        assertEquals("a reply", raw.lastUtteranceText)
        assertEquals(CurrentAction.GENERATING, raw.currentActionRaw)
    }

    @Test
    fun `local and remote runtime bindings produce the same signal end to end`() {
        val clock = ManualEpochClock()
        val presence = FridayPresenceEvidenceStore.Snapshot(
            userPresent = true,
            appForeground = true,
            lastInteractionAtMs = clock.now - 30_000L,
            interactionCount = 42L
        )
        val localBinding = FridayLocalRuntimeBinding(
            readStatus = { "sending (local)" },
            readLastReply = { "a reply" },
            readPresence = { presence },
            nowMs = { clock.now }
        )
        val local = FridayRuntimeIntegration(localBinding, nowMs = { clock.now })
        local.onUserSubmitted()
        local.tick()
        val localSignal: CompanionSignal = local.signal.value

        val remoteBinding = RemoteHumanCoreBinding(
            endpointUrl = "test://status",
            httpGet = { jsonStatusPayload },
            refreshIntervalMs = 60_000L
        )
        remoteBinding.refreshNow()
        val remote = FridayRuntimeIntegration(remoteBinding, nowMs = { clock.now })
        remote.tick()

        assertEquals(CurrentAction.GENERATING, localSignal.currentAction)
        assertEquals(localSignal, remote.signal.value)
    }

    @Test
    fun `fabrication referee evidence passes through unchanged`() {
        val evidence = FridayRuntimeSnapshot.empty(1_700_000_000_000L)
            .copy(fabricationEvidence = true)
        val signal = SignalMapper.map(evidence, CurrentAction.IDLE)
        assertTrue(signal.fabricationFlag)
    }

    companion object {
        private val jsonStatusPayload = """
            {
              "ts": 1700000000000,
              "total_interactions": 42,
              "seconds_since_last_contact": 30,
              "presence_mode": "ACTIVE",
              "current_action": "GENERATING",
              "bridge_status": "sending (local)",
              "last_utterance_text": "a reply",
              "fabrication_flag": false
            }
        """.trimIndent()
    }
}
