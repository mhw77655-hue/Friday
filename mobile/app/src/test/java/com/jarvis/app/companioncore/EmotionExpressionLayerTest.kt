package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.contract.EmotionSnapshot
import com.jarvis.app.companioncore.engine.EmotionExpressionLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Emotion Expression Layer (§2.9): one snapshot per tick, immutable for the
 * tick, stale → neutral fallback, and all consumers see an identical snapshot.
 */
class EmotionExpressionLayerTest {

    private fun signal(valence: Double = 0.6, arousal: Double = 0.7) = CompanionSignal(
        emotionVector = com.jarvis.app.companioncore.contract.EmotionVector(valence, arousal, 0.8),
        confidence = 0.8,
        energy = arousal,
        motivation = 0.5,
        attentionFocus = null,
        currentAction = CurrentAction.IDLE,
        lastUtteranceText = "",
        fabricationFlag = false
    )

    @Test
    fun `fresh signal produces a mirroring snapshot`() {
        val clock = ManualEpochClock()
        val layer = EmotionExpressionLayer(nowMs = { clock.now })
        layer.update(signal(valence = 0.6, arousal = 0.7), stale = false)
        val snap = layer.current()
        assertEquals(0.6, snap.valence, 1e-9)
        assertEquals(0.7, snap.arousal, 1e-9)
        assertEquals(0.8, snap.confidence, 1e-9)
    }

    @Test
    fun `stale signal falls back to neutral not calm`() {
        val clock = ManualEpochClock()
        val layer = EmotionExpressionLayer(nowMs = { clock.now })
        layer.update(signal(valence = 0.9, arousal = 0.9), stale = true)
        val snap = layer.current()
        // Neutral (no reliable data), NOT a calm read of a negative valence.
        assertEquals(0.0, snap.valence, 1e-9)
        assertEquals(0.5, snap.arousal, 1e-9)
        assertEquals(0.0, snap.confidence, 1e-9)
    }

    @Test
    fun `fresh signal resets out of neutral`() {
        val clock = ManualEpochClock()
        val layer = EmotionExpressionLayer(nowMs = { clock.now })
        layer.update(signal(valence = 0.9), stale = true)
        layer.update(signal(valence = 0.9), stale = false)
        assertEquals(0.9, layer.current().valence, 1e-9)
    }

    @Test
    fun `every consumer sees the identical snapshot for one tick`() {
        val clock = ManualEpochClock()
        val layer = EmotionExpressionLayer(nowMs = { clock.now })
        layer.update(signal(valence = -0.3, arousal = 0.4), stale = false)
        // Two independent collects across a tick see the SAME immutable value.
        val a: EmotionSnapshot = layer.snapshots.value
        val b: EmotionSnapshot = layer.snapshots.value
        assertEquals(a, b)
        assertEquals(-0.3, a.valence, 1e-9)
    }

    @Test
    fun `each tick produces a new snapshot not a mutable drift`() {
        val clock = ManualEpochClock()
        val layer = EmotionExpressionLayer(nowMs = { clock.now })
        layer.update(signal(valence = 0.2), stale = false)
        val first = layer.current()
        clock.advance(1_000L)
        layer.update(signal(valence = 0.8), stale = false)
        assertNotEquals(first, layer.current())
        // The earlier snapshot is untouched (immutability).
        assertEquals(0.2, first.valence, 1e-9)
    }
}
