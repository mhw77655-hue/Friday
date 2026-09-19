package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.engine.CompanionClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CompanionClock (§1.5, audit A-3): single master clock, monotonic + epoch
 * sources, frame pacing, drift-free conversion round-trips.
 */
class CompanionClockTest {

    @Test
    fun `elapsed nanos tracks monotonic source`() {
        val src = ManualCompanionClockSource()
        val clock = src.make()
        src.advanceNanos(5_000_000L)
        assertEquals(5_000_000L, clock.elapsedNanos())
    }

    @Test
    fun `now epoch millis advances with both sources`() {
        val src = ManualCompanionClockSource()
        val clock = src.make()
        val t0 = clock.nowEpochMs()
        src.advanceMillis(1_000L)
        assertEquals(t0 + 1_000L, clock.nowEpochMs())
    }

    @Test
    fun `epoch and master conversions round trip without drift`() {
        val src = ManualCompanionClockSource()
        val clock = src.make()
        src.advanceMillis(7_777L)
        val master = clock.elapsedNanos()
        val epoch = clock.toEpochMs(master)
        assertEquals(clock.nowEpochMs(), epoch)
        assertEquals(master, clock.toMasterElapsedNanos(epoch))
    }

    @Test
    fun `frame period for a target fps`() {
        val src = ManualCompanionClockSource()
        val clock = src.make()
        assertEquals(CompanionClock.NANOS_PER_SECOND / 60, clock.framePeriodNanos(60.0))
        assertEquals(CompanionClock.NANOS_PER_SECOND / 30, clock.framePeriodNanos(30.0))
    }

    @Test
    fun `master elapsed converts a frame timestamp on the same monotonic base`() {
        val src = ManualCompanionClockSource()
        val clock = src.make()
        src.advanceNanos(4_000_000L)
        // A render-lane frame timestamp (System.nanoTime base, i.e. an absolute
        // reading from the SAME monotonic source) maps to "elapsed since anchor".
        assertEquals(4_000_000L, clock.masterElapsedNanos(src.monotonicNanos))
        assertEquals(0L, clock.masterElapsedNanos(src.monotonicNanos - 4_000_000L))
    }

    @Test
    fun `frame period zero for non positive fps`() {
        val src = ManualCompanionClockSource()
        val clock = src.make()
        assertEquals(0L, clock.framePeriodNanos(0.0))
        assertEquals(0L, clock.framePeriodNanos(-5.0))
    }

    @Test
    fun `next frame delay never negative and meets target period`() {
        val src = ManualCompanionClockSource()
        val clock = src.make()
        val period = clock.framePeriodNanos(60.0)
        val lastStart = clock.elapsedNanos()
        src.advanceNanos(period)
        val delay = clock.nextFrameDelayNanos(lastStart, 60.0)
        assertTrue(delay in 0L..period)
    }

    @Test
    fun `reset re-anchors both sources`() {
        val src = ManualCompanionClockSource()
        val clock = src.make()
        src.advanceMillis(5_000L)
        val epochBefore = clock.nowEpochMs()
        clock.reset()
        assertTrue(clock.nowEpochMs() >= epochBefore)
        assertTrue(clock.elapsedNanos() <= 1_000_000L) // re-anchored near zero
    }
}
