package com.jarvis.app.vf

import com.jarvis.app.vf.motion.MotionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MotionEngine: breathing envelope, event-pulse envelope, timing bounds.
 */
class MotionEngineTest {

    @Test
    fun `breathing at peak is above baseline`() {
        // At t = period/4 (sine peak), brightness = 1 + amplitude
        val b = MotionEngine.breathingBrightness(
            elapsedMs = 3600L / 4,
            periodMs = 3600L,
            amplitude = 0.10f
        )
        assertTrue("breathing should be > 1.0 at peak", b > 1.0f)
        assertTrue("breathing should be ≈ 1.10", kotlin.math.abs(b - 1.10f) < 0.05f)
    }

    @Test
    fun `breathing at trough is below baseline`() {
        val b = MotionEngine.breathingBrightness(
            elapsedMs = 3600L * 3 / 4, // 3/4 period = sine trough
            periodMs = 3600L,
            amplitude = 0.10f
        )
        assertTrue("breathing should be < 1.0 at trough", b < 1.0f)
        assertTrue("breathing should be ≈ 0.90", kotlin.math.abs(b - 0.90f) < 0.05f)
    }

    @Test
    fun `breathing is periodic over full cycle`() {
        val b0 = MotionEngine.breathingBrightness(0L, 3600L, 0.10f)
        val b1 = MotionEngine.breathingBrightness(3600L, 3600L, 0.10f)
        assertEquals("breathing must repeat", b0, b1, 1e-6f)
    }

    @Test
    fun `event pulse ramps to 1 during attack`() {
        val p = MotionEngine.eventPulseBrightness(75L) // mid-attack
        assertTrue("pulse should be positive during attack", p > 0f)
        assertTrue("pulse should be < 1 during attack", p < 1f)
    }

    @Test
    fun `event pulse decays after attack + decay window`() {
        val p = MotionEngine.eventPulseBrightness(700L) // after 150+500
        assertEquals("pulse should be 0 after decay", 0f, p, 1e-6f)
    }

    @Test
    fun `event pulse attack is a fast ramp ending at full spike`() {
        val attackEnd = 150L
        assertEquals("attack must reach 1 exactly at peak", 1f, MotionEngine.eventPulseBrightness(attackEnd), 0.001f)
        assertTrue("attack should be rising", MotionEngine.eventPulseBrightness(75L) < MotionEngine.eventPulseBrightness(140L))
    }

    @Test
    fun `event pulse decay is sharp ease-out, not a linear triangle`() {
        // Mid-decay: linear decay would read 0.5; the §12.2 sharp ease-out
        // (cubic-bezier(0.9,0,1,1) sampled at remaining brightness) reads ~0.13.
        val midDecay = 150L + 250L
        val p = MotionEngine.eventPulseBrightness(midDecay)
        assertTrue("mid-decay must fall well below linear 0.5 (got $p)", p < 0.30f)
        // 10% into decay: must already be well off the peak (linear would be 0.90).
        val earlyDecay = MotionEngine.eventPulseBrightness(150L + 50L)
        assertTrue("decay must drop steeply off the peak (got $earlyDecay)", earlyDecay < 0.75f)
        // Monotone decay after the peak.
        for (t in 151L..649L step 50) {
            val now = MotionEngine.eventPulseBrightness(t)
            val later = MotionEngine.eventPulseBrightness(t + 1)
            assertTrue("decay must be non-increasing at $t", later <= now + 1e-6f)
        }
    }

    @Test
    fun `breathing never exceeds amplitude bounds`() {
        for (t in 0..7200L step 100) {
            val b = MotionEngine.breathingBrightness(t, 3600L, 0.12f)
            assertTrue("breathing at $t must be >= 0.88", b >= 0.88f)
            assertTrue("breathing at $t must be <= 1.12", b <= 1.12f)
        }
    }

    @Test
    fun `sharp pulse at 1Hz spikes once per second`() {
        assertTrue("must spike at cycle start", MotionEngine.sharpPulse(1L, 1f) > 0f)
        assertEquals("must rest silent after the spike", 0f, MotionEngine.sharpPulse(400L, 1f), 1e-6f)
        assertEquals("must rest silent just before the next cycle", 0f, MotionEngine.sharpPulse(999L, 1f), 1e-6f)
        assertTrue("must spike again at the next cycle", MotionEngine.sharpPulse(1001L, 1f) > 0f)
    }

    @Test
    fun `sharp pulse at 2Hz spikes twice per second`() {
        assertTrue("must spike at cycle start", MotionEngine.sharpPulse(1L, 2f) > 0f)
        assertEquals("must rest between spikes", 0f, MotionEngine.sharpPulse(400L, 2f), 1e-6f)
        assertTrue("must spike at the 2Hz cycle boundary", MotionEngine.sharpPulse(501L, 2f) > 0f)
        assertTrue("must spike at 1000ms (2 cycles)", MotionEngine.sharpPulse(1001L, 2f) > 0f)
    }

    @Test
    fun `sharp pulse frequencies are distinct and non-overlapping`() {
        // At 520ms: 1Hz is mid-cycle (silent, spike already passed), 2Hz is
        // inside its second spike — so a grayscale observer sees 1Hz vs 2Hz
        // as different flash rates (§5.7).
        assertEquals("1Hz must be silent at 520ms", 0f, MotionEngine.sharpPulse(520L, 1f), 1e-6f)
        assertTrue("2Hz must be spiking at 520ms", MotionEngine.sharpPulse(520L, 2f) > 0f)
    }

    @Test
    fun `sharp pulse is zero for non-alert frequency`() {
        assertEquals("hz <= 0 must produce no pulse", 0f, MotionEngine.sharpPulse(50L, 0f), 1e-6f)
    }

    @Test
    fun `sharp pulse spike shape is fast attack then sharp ease-out`() {
        val midAttack = MotionEngine.sharpPulse(18L, 1f) // 18/150 = 0.12 spike fraction
        val nearPeak = MotionEngine.sharpPulse(36L, 1f) // 36/150 = 0.24, just before join
        val justAfterJoin = MotionEngine.sharpPulse(50L, 1f) // 50/150 = 0.33, in decay
        assertTrue("attack must rise sharply", midAttack < nearPeak)
        assertTrue("must be at/near peak at the attack join", nearPeak >= 0.9f)
        assertTrue("decay must begin dropping", justAfterJoin < nearPeak)
        // Decay must be sharp ease-out (fall off fast), not linear: at spike
        // fraction 0.5 (t=75ms) the eased value is well below linear 0.5.
        val midDecay = MotionEngine.sharpPulse(75L, 1f)
        assertTrue("mid-spike decay must be well below linear 0.5 (got $midDecay)", midDecay < 0.35f)
    }
}
