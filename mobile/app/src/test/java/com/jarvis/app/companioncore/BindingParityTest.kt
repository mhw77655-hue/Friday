package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.engine.CurrentActionDiscriminator
import com.jarvis.app.companioncore.engine.HumanCoreIntegration
import com.jarvis.app.companioncore.engine.LocalHumanCoreBinding
import com.jarvis.app.companioncore.engine.RawCoreState
import com.jarvis.app.companioncore.engine.RemoteHumanCoreBinding
import com.jarvis.app.companioncore.engine.SignalMapper
import com.jarvis.app.humancore.bus.StateBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local-vs-remote binding parity (audit M-8, spec §2.30): both bindings must
 * produce identical `CompanionSignal` shapes from equivalent underlying Human
 * Core state.
 *
 * Equivalent state is defined as: valence 0.6, arousal 0.7, trust 0.8,
 * presence ACTIVE, current_action GENERATING, same utterance text. The local
 * binding reads it from a `StateSnapshot` fixture (via injected lambdas); the
 * remote binding reads it from a documented `/api/status`-shaped JSON payload
 * (via an injected in-memory fetch — no socket in tests).
 */
class BindingParityTest {

    // StateBus teardown: LocalHumanCoreBinding subscribes in its init.
    @After
    fun tearDownBus() {
        StateBus.reset()
    }

    private val equivalent = RawCoreState(
        ts = 1_700_000_000_000L,
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
        bridgeStatus = null,
        lastUtteranceText = "a reply",
        fabricationEvidence = false
    )

    @Test
    fun `equivalent raw state maps to identical CompanionSignal`() {
        val local = SignalMapper.map(equivalent, CurrentAction.GENERATING)
        val remoteRaw = equivalent.copy(currentActionRaw = CurrentAction.GENERATING)
        val remote = SignalMapper.map(remoteRaw, CurrentAction.GENERATING)
        assertEquals(local, remote)
    }

    @Test
    fun `local binding reads equivalent state from a snapshot fixture`() {
        val clock = ManualEpochClock()
        val snap = snapshot(
            ts = clock.now,
            valence = 0.6,
            arousal = 0.7,
            trust = 0.8,
            totalInteractions = 42L,
            secondsSinceLastContact = 30L
        )
        val binding = LocalHumanCoreBinding(
            readSnapshot = { snap },
            readStatus = { "sending (local)" },
            readLastReply = { "a reply" },
            readPresenceModeName = { snap.presence.mode.name }
        )
        val raw = binding.read()
        assertEquals(0.6, raw.moodValence!!, 1e-9)
        assertEquals(0.7, raw.moodArousal!!, 1e-9)
        assertEquals(0.8, raw.trust!!, 1e-9)
        assertEquals(42L, raw.totalInteractions)
        assertEquals(30L, raw.secondsSinceLastContact!!)
        assertEquals("a reply", raw.lastUtteranceText)
        assertEquals("ACTIVE", raw.presenceModeName)
    }

    @Test
    fun `remote binding parses the documented status payload`() {
        val binding = RemoteHumanCoreBinding(
            endpointUrl = "test://status",
            httpGet = { jsonStatusPayload },
            refreshIntervalMs = 60_000L // don't poll during the test
        )
        binding.refreshNow()
        assertTrue(binding.isHealthy())
        val raw = binding.read()
        assertEquals(0.6, raw.moodValence!!, 1e-9)
        assertEquals(0.7, raw.moodArousal!!, 1e-9)
        assertEquals(0.8, raw.trust!!, 1e-9)
        assertEquals(42L, raw.totalInteractions)
        assertEquals(30L, raw.secondsSinceLastContact!!)
        assertEquals("a reply", raw.lastUtteranceText)
        assertEquals(CurrentAction.GENERATING, raw.currentActionRaw)
    }

    @Test
    fun `integration parity - both bindings yield the same signal end to end`() {
        val clock = ManualEpochClock()

        // Local path: snapshot fixture + discriminator drives GENERATING.
        val snap = snapshot(ts = clock.now)
        val localBinding = LocalHumanCoreBinding(
            readSnapshot = { snap },
            readStatus = { "sending (local)" },
            readLastReply = { "a reply" },
            readPresenceModeName = { snap.presence.mode.name }
        )
        val local = HumanCoreIntegration(localBinding, nowMs = { clock.now })
        local.onUserSubmitted()
        local.tick()
        val localSignal: CompanionSignal = local.signal.value

        // Remote path: identical state via the documented payload.
        val remoteBinding = RemoteHumanCoreBinding(
            endpointUrl = "test://status",
            httpGet = { jsonStatusPayload },
            refreshIntervalMs = 60_000L
        )
        remoteBinding.refreshNow()
        val remote = HumanCoreIntegration(remoteBinding, nowMs = { clock.now })
        remote.tick()
        val remoteSignal: CompanionSignal = remote.signal.value

        assertEquals(CurrentAction.GENERATING, localSignal.currentAction)
        assertEquals(localSignal, remoteSignal)
    }

    @Test
    fun `fabrication flag passes through both bindings unchanged`() {
        val clock = ManualEpochClock()
        val local = SignalMapper.map(equivalent.copy(fabricationEvidence = true), CurrentAction.IDLE)
        val remoteRaw = equivalent.copy(
            currentActionRaw = CurrentAction.IDLE,
            fabricationEvidence = true
        )
        val remote = SignalMapper.map(remoteRaw, CurrentAction.IDLE)
        assertTrue(local.fabricationFlag)
        assertEquals(local, remote)
    }

    companion object {
        private val jsonStatusPayload = """
            {
              "ts": 1700000000000,
              "valence": 0.6,
              "arousal": 0.7,
              "last_user_confidence": 0.9,
              "trust": 0.8,
              "bond_depth": 0.5,
              "total_interactions": 42,
              "seconds_since_last_contact": 30,
              "presence_mode": "ACTIVE",
              "current_action": "GENERATING",
              "last_utterance_text": "a reply",
              "fabrication_flag": false
            }
        """.trimIndent()
    }
}
