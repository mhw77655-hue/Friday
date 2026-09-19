package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.RenderIntent
import com.jarvis.app.companioncore.contract.RenderPriority
import com.jarvis.app.companioncore.contract.VisualState
import com.jarvis.app.companioncore.render.AnimationController
import com.jarvis.app.companioncore.render.OrbClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.21 — Animation Controller tests.
 *
 * Intent arbitration (priority gate, most-recent-on-tie), frame pacing derived
 * from the master clock, and the frame-budget watchdog that feeds §2.31.
 */
class AnimationControllerTest {

    private val clockSource = ManualCompanionClockSource()
    private val controller = AnimationController(clockSource.make())

    private fun intent(visual: VisualState, priority: RenderPriority = RenderPriority.NORMAL) =
        RenderIntent(visualState = visual, priority = priority)

    // ------------------------------------------------------------------ arbitration

    @Test
    fun `first intent is accepted and resolved to a clip`() {
        assertTrue(controller.submit(intent(VisualState.IDLE_BREATHE)))
        assertEquals(VisualState.IDLE_BREATHE, controller.currentIntent?.visualState)
        assertEquals(OrbClip.IDLE_BREATHE, controller.currentClip)
    }

    @Test
    fun `higher priority intent preempts the current one`() {
        controller.submit(intent(VisualState.IDLE_BREATHE, RenderPriority.NORMAL))
        assertTrue(controller.submit(intent(VisualState.WAKE_BLOOM, RenderPriority.URGENT)))
        assertEquals(VisualState.WAKE_BLOOM, controller.currentIntent?.visualState)
        assertTrue(controller.lastSubmitPreempted)
    }

    @Test
    fun `lower priority intent is rejected`() {
        controller.submit(intent(VisualState.SPEAKING_PULSE, RenderPriority.HIGH))
        assertFalse(controller.submit(intent(VisualState.IDLE_BREATHE, RenderPriority.LOW)))
        assertEquals(VisualState.SPEAKING_PULSE, controller.currentIntent?.visualState)
        assertFalse(controller.lastSubmitPreempted)
    }

    @Test
    fun `equal priority - most recent wins`() {
        controller.submit(intent(VisualState.THINKING_SWIRL))
        assertTrue(controller.submit(intent(VisualState.LISTENING_RIPPLE)))
        assertEquals(VisualState.LISTENING_RIPPLE, controller.currentIntent?.visualState)
    }

    @Test
    fun `no intent before first submit`() {
        assertNull(controller.currentIntent)
        assertNull(controller.currentClip)
    }

    @Test
    fun `state submit always applies even when priority drops`() {
        // An alert (URGENT) clearing back to NORMAL must not leave the stale
        // alert intent in place — the per-tick state is authoritative.
        controller.submit(intent(VisualState.WAKE_BLOOM, RenderPriority.URGENT))
        controller.submitState(intent(VisualState.IDLE_BREATHE, RenderPriority.NORMAL))
        assertEquals(VisualState.IDLE_BREATHE, controller.currentIntent?.visualState)
        assertFalse(controller.lastSubmitPreempted)
    }

    // ------------------------------------------------------------------ pacing

    @Test
    fun `frame period matches the requested fps`() {
        assertEquals(16_666_666L, controller.framePeriodNanos(60.0))
        assertEquals(33_333_333L, controller.framePeriodNanos(30.0))
        assertEquals(0L, controller.framePeriodNanos(0.0))
    }

    @Test
    fun `next frame delay never negative`() {
        val period = controller.framePeriodNanos(30.0)
        // 0.1ms into a 33.3ms period → next frame is still ~33.2ms away.
        clockSource.monotonicNanos = 100_000L
        val delay = controller.nextFrameDelayNanos(0L, 30.0)
        assertTrue(delay in 1..period)
        // A frame that overshoots its budget schedules the next immediately.
        clockSource.monotonicNanos = 40_000_000L
        assertEquals(0L, controller.nextFrameDelayNanos(0L, 30.0))
    }

    // ------------------------------------------------------------------ watchdog

    @Test
    fun `frame misses accumulate on sustained over-budget frames`() {
        val period = controller.framePeriodNanos(60.0)
        controller.recordFrame(1_000L, 20_000_000L, period) // 20ms > 16.6ms → miss
        controller.recordFrame(30_000_000L, 20_000_000L, period) // miss
        assertEquals(2, controller.consecutiveFrameMisses)
    }

    @Test
    fun `within-budget frame resets the miss streak`() {
        val period = controller.framePeriodNanos(60.0)
        controller.recordFrame(1_000L, 20_000_000L, period) // miss
        controller.recordFrame(30_000_000L, 5_000_000L, period) // within → reset
        assertEquals(0, controller.consecutiveFrameMisses)
    }

    @Test
    fun `watchdog ignores a zero tier-cap`() {
        controller.recordFrame(1_000L, 900_000_000L, 0L)
        assertEquals(0, controller.consecutiveFrameMisses)
    }

    @Test
    fun `last render timestamp converts through the master clock`() {
        clockSource.advanceMillis(5_000)
        controller.recordFrame(clockSource.monotonicNanos, 8_000_000L, controller.framePeriodNanos(60.0))
        assertEquals(clockSource.epochMs, controller.lastRenderEpochMs)
    }
}
