package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.engine.RawCoreState
import com.jarvis.app.companioncore.engine.SignalMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapping unit tests (plan Phase 1 item 6): every `CompanionSignal` field is
 * produced from fixture raw state, including the documented GAP heuristics
 * (confidence/energy/motivation) and the pass-through fields
 * (emotion vector, current_action, utterance text, fabrication flag).
 */
class SignalMapperTest {

    private fun raw(
        valence: Double? = 0.6,
        arousal: Double? = 0.7,
        trust: Double? = 0.8,
        presenceModeName: String? = "ACTIVE",
        action: CurrentAction? = null,
        lastText: String? = "hello",
        fabrication: Boolean = false
    ) = RawCoreState(
        ts = 1_700_000_000_000L,
        moodValence = valence,
        moodArousal = arousal,
        lastUserConfidence = 0.9,
        trust = trust,
        bondDepth = 0.5,
        totalInteractions = 42L,
        secondsSinceLastContact = 30L,
        lastUserSignals = mapOf("frustration" to 0.1),
        presenceModeName = presenceModeName,
        currentActionRaw = action,
        bridgeStatus = null,
        lastUtteranceText = lastText,
        fabricationEvidence = fabrication
    )

    @Test
    fun `emotion vector mirrors valence and arousal`() {
        val s = SignalMapper.map(raw(valence = 0.6, arousal = 0.7), CurrentAction.IDLE)
        assertEquals(0.6, s.emotionVector.valence, 1e-9)
        assertEquals(0.7, s.emotionVector.arousal, 1e-9)
    }

    @Test
    fun `null emotion fields surface as neutral not default`() {
        val s = SignalMapper.map(raw(valence = null, arousal = null), CurrentAction.IDLE)
        // Mirrors HC §10: "unknown" maps to neutral (0/0.5), not a fabricated read.
        assertEquals(0.0, s.emotionVector.valence, 1e-9)
        assertEquals(0.5, s.emotionVector.arousal, 1e-9)
    }

    @Test
    fun `confidence heuristic is monotonic in valence and trust`() {
        val low = SignalMapper.map(raw(valence = 0.0, trust = 0.0), CurrentAction.IDLE)
        val high = SignalMapper.map(raw(valence = 1.0, trust = 1.0), CurrentAction.IDLE)
        assertTrue("confidence should rise with |valence| and trust", low.confidence < high.confidence)
    }

    @Test
    fun `confidence stays in unit range at extremes`() {
        val min = SignalMapper.map(raw(valence = -1.0, trust = 0.0), CurrentAction.IDLE)
        val max = SignalMapper.map(raw(valence = 1.0, trust = 1.0), CurrentAction.IDLE)
        assertTrue(min.confidence >= 0.0)
        assertTrue(max.confidence <= 1.0)
    }

    @Test
    fun `energy mirrors arousal`() {
        val s = SignalMapper.map(raw(arousal = 0.3), CurrentAction.IDLE)
        assertEquals(0.3, s.energy, 1e-9)
    }

    @Test
    fun `motivation is discounted when presence is away`() {
        val active = SignalMapper.map(raw(presenceModeName = "ACTIVE"), CurrentAction.IDLE)
        val away = SignalMapper.map(raw(presenceModeName = "AWAY"), CurrentAction.IDLE)
        assertTrue("AWAY presence should lower motivation", away.motivation < active.motivation)
    }

    @Test
    fun `attention focus is deferred until the attention engine exists`() {
        val s = SignalMapper.map(raw(), CurrentAction.IDLE)
        assertEquals(null, s.attentionFocus)
    }

    @Test
    fun `current action is passed through`() {
        val s = SignalMapper.map(raw(action = CurrentAction.GENERATING), CurrentAction.GENERATING)
        assertEquals(CurrentAction.GENERATING, s.currentAction)
    }

    @Test
    fun `last utterance text is passed through`() {
        val s = SignalMapper.map(raw(lastText = "a reply"), CurrentAction.IDLE)
        assertEquals("a reply", s.lastUtteranceText)
    }

    @Test
    fun `null utterance text becomes empty string`() {
        val s = SignalMapper.map(raw(lastText = null), CurrentAction.IDLE)
        assertEquals("", s.lastUtteranceText)
    }

    @Test
    fun `fabrication flag passes through unchanged`() {
        assertFalse(SignalMapper.map(raw(fabrication = false), CurrentAction.IDLE).fabricationFlag)
        assertTrue(SignalMapper.map(raw(fabrication = true), CurrentAction.IDLE).fabricationFlag)
    }

    @Test
    fun `mapping is a pure function of inputs`() {
        val r = raw()
        val a = SignalMapper.map(r, CurrentAction.IDLE)
        val b = SignalMapper.map(r, CurrentAction.IDLE)
        assertEquals(a, b)
    }
}
