package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.engine.FridayRuntimeSnapshot
import com.jarvis.app.companioncore.engine.SignalMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validates that the renderer never invents affect or motivation evidence. */
class SignalMapperTest {
    private fun snapshot(
        valence: Double? = null,
        arousal: Double? = null,
        confidence: Double? = null,
        presenceModeName: String? = null,
        action: CurrentAction? = null,
        lastText: String? = "hello",
        fabrication: Boolean = false
    ) = FridayRuntimeSnapshot(
        ts = 1_700_000_000_000L,
        moodValence = valence,
        moodArousal = arousal,
        lastUserConfidence = confidence,
        totalInteractions = 1L,
        presenceModeName = presenceModeName,
        currentActionRaw = action,
        lastUtteranceText = lastText,
        fabricationEvidence = fabrication
    )

    @Test fun `missing affect values use neutral render baseline with zero confidence`() {
        val signal = SignalMapper.map(snapshot(), CurrentAction.IDLE)
        assertEquals(0.0, signal.emotionVector.valence, 1e-9)
        assertEquals(0.5, signal.emotionVector.arousal, 1e-9)
        assertEquals(0.0, signal.emotionVector.confidence, 1e-9)
        assertEquals(0.0, signal.confidence, 1e-9)
        assertEquals(0.5, signal.energy, 1e-9)
        assertEquals(0.5, signal.motivation, 1e-9)
    }

    @Test fun `validated affect measurements are mapped without heuristic confidence`() {
        val signal = SignalMapper.map(
            snapshot(valence = 0.6, arousal = 0.7, confidence = 0.82),
            CurrentAction.IDLE
        )
        assertEquals(0.6, signal.emotionVector.valence, 1e-9)
        assertEquals(0.7, signal.emotionVector.arousal, 1e-9)
        assertEquals(0.82, signal.emotionVector.confidence, 1e-9)
        assertEquals(0.82, signal.confidence, 1e-9)
        assertEquals(0.7, signal.energy, 1e-9)
    }

    @Test fun `presence awayness does not fabricate motivation`() {
        val active = SignalMapper.map(snapshot(presenceModeName = "ACTIVE"), CurrentAction.IDLE)
        val away = SignalMapper.map(snapshot(presenceModeName = "AWAY"), CurrentAction.IDLE)
        assertEquals(0.5, active.motivation, 1e-9)
        assertEquals(active.motivation, away.motivation, 1e-9)
    }

    @Test fun `attention focus remains unknown until a validated attention source exists`() {
        assertEquals(null, SignalMapper.map(snapshot(), CurrentAction.IDLE).attentionFocus)
    }

    @Test fun `current action is supplied by Friday runtime discriminator or binding`() {
        val signal = SignalMapper.map(snapshot(action = CurrentAction.GENERATING), CurrentAction.GENERATING)
        assertEquals(CurrentAction.GENERATING, signal.currentAction)
    }

    @Test fun `last utterance is preserved and null becomes empty for renderers`() {
        assertEquals("a reply", SignalMapper.map(snapshot(lastText = "a reply"), CurrentAction.IDLE).lastUtteranceText)
        assertEquals("", SignalMapper.map(snapshot(lastText = null), CurrentAction.IDLE).lastUtteranceText)
    }

    @Test fun `fabrication evidence only passes through from its referee`() {
        assertFalse(SignalMapper.map(snapshot(fabrication = false), CurrentAction.IDLE).fabricationFlag)
        assertTrue(SignalMapper.map(snapshot(fabrication = true), CurrentAction.IDLE).fabricationFlag)
    }

    @Test fun `mapping is a pure function of inputs`() {
        val raw = snapshot(valence = 0.4, arousal = 0.2, confidence = 0.3)
        assertEquals(
            SignalMapper.map(raw, CurrentAction.IDLE),
            SignalMapper.map(raw, CurrentAction.IDLE)
        )
    }
}
