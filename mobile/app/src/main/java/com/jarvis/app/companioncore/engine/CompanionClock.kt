package com.jarvis.app.companioncore.engine

/**
 * The single master clock for the Companion Core (§1.5, audit A-3).
 *
 * Every timing domain — Presence Engine tick, Animation Controller frame
 * clock, Speech Timing's audio `PlaybackTimestamp` — converts to this master
 * before any cross-domain comparison, so no subsystem keeps a second
 * free-running clock (spec Design Principle 4 / Hard Invariant 5).
 *
 * A monotonic source is anchored at construction and an epoch pair is
 * captured at the same moment. [elapsedNanos] is the master time; derived
 * timestamps convert back to epoch millis for persistence. [reset] re-anchors
 * both (used on process-death restore, plan §1.7).
 *
 * Frame pacing returns the delay until the next frame at a target fps so the
 * render loop never runs faster than its resource tier allows (audit R-P5/M-16).
 *
 * Pure-JVM friendly: monotonic/epoch sources are injectable for tests
 * (deterministic `ManualClock`); on device they default to
 * `System.nanoTime()` / `System.currentTimeMillis()`.
 */
class CompanionClock(
    private val monotonicNanos: () -> Long = { System.nanoTime() },
    private val epochMs: () -> Long = { System.currentTimeMillis() }
) {

    @Volatile private var anchorMonotonicNanos: Long = monotonicNanos()
    @Volatile private var anchorEpochMs: Long = epochMs()

    /** Re-anchor the clock to "now" on both sources (process-death restore). */
    fun reset() {
        anchorMonotonicNanos = monotonicNanos()
        anchorEpochMs = epochMs()
    }

    /** Master time: nanoseconds elapsed since the clock was anchored. */
    fun elapsedNanos(): Long = monotonicNanos() - anchorMonotonicNanos

    /** Master time expressed as epoch millis (for persistence/display). */
    fun nowEpochMs(): Long = anchorEpochMs + elapsedNanos() / NANOS_PER_MILLI

    /**
     * Convert a render-lane frame timestamp (Android's `Choreographer` /
     * Compose `withFrameNanos` timebase — `System.nanoTime()`-based, same
     * source as this clock's default monotonic) into master-elapsed
     * nanoseconds. Without this conversion a raw frame timestamp would be
     * interpreted as "elapsed since anchor" and produce a garbage epoch
     * (audit F4 — the render timestamp in `CompanionState`).
     */
    fun masterElapsedNanos(frameTimeNanos: Long): Long = frameTimeNanos - anchorMonotonicNanos

    /** Convert a master-elapsed-nanos timestamp back to epoch millis. */
    fun toEpochMs(masterElapsedNanos: Long): Long =
        anchorEpochMs + masterElapsedNanos / NANOS_PER_MILLI

    /** Convert an epoch-millis timestamp into master-elapsed-nanos. */
    fun toMasterElapsedNanos(epochMsTimestamp: Long): Long =
        (epochMsTimestamp - anchorEpochMs) * NANOS_PER_MILLI

    /**
     * Frame period for a target fps (0 when target is non-positive).
     * Consumed by the Animation Controller (Phase 2) to cap render rate to
     * the resource tier (audit R-P5/M-16).
     */
    fun framePeriodNanos(targetFps: Double): Long {
        if (targetFps <= 0.0) return 0L
        return (NANOS_PER_SECOND / targetFps).toLong()
    }

    /**
     * Delay until the next frame at [targetFps], given the master-elapsed
     * nanoseconds at which the last frame started. Never negative: a slow
     * frame schedules the next one immediately (frame-budget watchdog feeds
     * §2.31 in a later phase).
     */
    fun nextFrameDelayNanos(lastFrameStartedElapsedNanos: Long, targetFps: Double): Long {
        val period = framePeriodNanos(targetFps)
        if (period <= 0L) return 0L
        val next = lastFrameStartedElapsedNanos + period
        return (next - elapsedNanos()).coerceAtLeast(0L)
    }

    companion object {
        const val NANOS_PER_MILLI: Long = 1_000_000L
        const val NANOS_PER_SECOND: Long = 1_000_000_000L
        /** Default signal staleness threshold (spec §2.30: 2s). */
        const val DEFAULT_STALENESS_THRESHOLD_MS: Long = 2_000L
    }
}
