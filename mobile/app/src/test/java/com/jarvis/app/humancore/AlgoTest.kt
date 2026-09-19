package com.jarvis.app.humancore

import com.jarvis.app.humancore.algo.Accumulate
import com.jarvis.app.humancore.algo.Clamp
import com.jarvis.app.humancore.algo.Decay
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hand-computed checks against the three shared algorithms (§0.9).
 * These are the foundations every store's math rests on; if any of these
 * fail, the whole subsystem's behavior is untrustworthy.
 */
class AlgoTest {

    @Test
    fun `decay halves each half-life unit`() {
        // decayFactor 0.5 == half-life per unit
        assertEquals(0.5, Decay.value(1.0, 0.0, 0.5, 1.0), 1e-9)
        assertEquals(0.25, Decay.value(1.0, 0.0, 0.5, 2.0), 1e-9)
        assertEquals(0.125, Decay.value(1.0, 0.0, 0.5, 3.0), 1e-9)
    }

    @Test
    fun `decay with zero elapsed changes nothing`() {
        assertEquals(1.0, Decay.value(1.0, 0.5, 0.5, 0.0), 1e-9)
    }

    @Test
    fun `decay never drifts off baseline`() {
        // At any baseline == current, decay is a fixed point forever.
        assertEquals(0.3, Decay.value(0.3, 0.3, 0.5, 9999.0), 1e-9)
        // Long elapsed pulls fully to baseline.
        assertEquals(0.5, Decay.value(1.0, 0.5, 0.5, 1000.0), 1e-9)
    }

    @Test
    fun `accumulate moves proportionally by confidence`() {
        // current 0.5, observe 1.0, rate 0.2, confidence 1.0 -> +0.1
        assertEquals(0.6, Accumulate.step(0.5, 1.0, 0.2, 1.0), 1e-9)
        // confidence 0.5 halves the step -> +0.05
        assertEquals(0.55, Accumulate.step(0.5, 1.0, 0.2, 0.5), 1e-9)
        // zero confidence -> no movement
        assertEquals(0.5, Accumulate.step(0.5, 1.0, 0.2, 0.0), 1e-9)
        // observation equal to current -> no movement
        assertEquals(0.5, Accumulate.step(0.5, 0.5, 0.2, 1.0), 1e-9)
    }

    @Test
    fun `learning rate shrinks toward floor`() {
        // factor is a per-step survival fraction: rate * factor, floored.
        assertEquals(0.18, Accumulate.withShrinkingRate(0.2, 0.9, 0.01), 1e-9)
        // floors below the minimum
        assertEquals(0.01, Accumulate.withShrinkingRate(0.005, 0.9, 0.01), 1e-9)
        // never exceeds 1.0
        assertEquals(1.0, Accumulate.withShrinkingRate(1.2, 0.9, 0.01), 1e-9)
    }

    @Test
    fun `clamp honors ranges`() {
        assertEquals(0.0, Clamp.unit(-0.5), 0.0)
        assertEquals(1.0, Clamp.unit(1.5), 0.0)
        assertEquals(0.5, Clamp.unit(0.5), 0.0)
        assertEquals(-1.0, Clamp.unitSymmetric(-1.5), 0.0)
        assertEquals(1.0, Clamp.unitSymmetric(1.5), 0.0)
        assertEquals(0.4, Clamp.unitSymmetric(0.4), 0.0)
    }
}
