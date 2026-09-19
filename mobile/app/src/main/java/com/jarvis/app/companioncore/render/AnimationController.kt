package com.jarvis.app.companioncore.render

import com.jarvis.app.companioncore.contract.RenderIntent
import com.jarvis.app.companioncore.engine.CompanionClock

/**
 * §2.21 — Animation Controller.
 *
 * The shared low-level playback engine beneath every visual subsystem: it
 * arbitrates [RenderIntent]s (only a higher- or equal-priority intent
 * replaces the current one — calls, wake-word, urgent notifications preempt),
 * owns frame pacing derived from the single master clock (audit R-P5/M-16),
 * and runs the frame-budget watchdog that feeds §2.31 (audit R-P2).
 *
 * The orb drawing path ([PresenceOrb]) is a *consumer*: it reads the resolved
 * [currentIntent], measures frames via `withFrameNanos`, and reports each
 * frame duration back through [recordFrame]. This class stays pure and
 * renderer-free so the arbitration and watchdog are unit-testable.
 */
class AnimationController(
    private val clock: CompanionClock = CompanionClock()
) {

    /** The intent currently being rendered, or null before the first submit. */
    var currentIntent: RenderIntent? = null
        private set

    /** The clip resolved from the current intent (null before first submit). */
    val currentClip: OrbClip? get() = currentIntent?.let { OrbClip.fromVisualState(it.visualState) }

    /** True when a newer, higher-priority intent replaced the previous one. */
    var lastSubmitPreempted: Boolean = false
        private set

    /**
     * Submit an interrupt-style intent (calls, wake word, urgent
     * notifications). Accepted only when no intent is active or
     * [intent.priority] is >= the active one (equal priority = most-recent
     * wins, per §2.1). Returns whether it was accepted.
     */
    fun submit(intent: RenderIntent): Boolean {
        val active = currentIntent
        lastSubmitPreempted = active != null && intent.priority >= active.priority
        val accepted = active == null || intent.priority >= active.priority
        if (accepted) currentIntent = intent
        return accepted
    }

    /**
     * Submit the authoritative per-tick baseline state (the Presence Engine's
     * resolved intent). Unlike an interrupt, a state change is ALWAYS applied —
     * even when its [RenderPriority] is lower than the previous state's (e.g.
     * an alert clearing from URGENT back to NORMAL must not leave the stale
     * alert intent in place).
     */
    fun submitState(intent: RenderIntent) {
        lastSubmitPreempted = false
        currentIntent = intent
    }

    /** Frame period for a target fps (0 when non-positive), via the master clock. */
    fun framePeriodNanos(targetFps: Double): Long = clock.framePeriodNanos(targetFps)

    /** Delay until the next frame at [targetFps] given the last frame start. */
    fun nextFrameDelayNanos(lastFrameStartElapsedNanos: Long, targetFps: Double): Long =
        clock.nextFrameDelayNanos(lastFrameStartElapsedNanos, targetFps)

    // ------------------------------------------------------------------ watchdog

    /**
     * Consecutive frames that exceeded the expected period (0 = within budget).
     * Written from the render lane, read from the Engine Tick Lane → volatile.
     */
    @Volatile
    var consecutiveFrameMisses: Int = 0
        private set

    /** Master-elapsed-nanos of the last recorded frame start (null before any).
     *  Written on the render lane (`recordFrame`), read on the Engine Tick Lane
     *  (`lastRenderEpochMs` each tick) → volatile (audit F4). */
    @Volatile
    var lastFrameStartElapsedNanos: Long? = null
        private set

    /** Epoch ms of the last recorded frame — feeds `CompanionState.lastRenderTimestamp`. */
    val lastRenderEpochMs: Long?
        get() = lastFrameStartElapsedNanos?.let { clock.toEpochMs(it) }

    /**
     * Record one rendered frame. A frame whose duration exceeds
     * [expectedPeriodNanos] (the tier's frame budget) increments the miss
     * streak; a within-budget frame resets it. [expectedPeriodNanos] ≤ 0
     * (no tier cap yet) is treated as "always within budget".
     */
    fun recordFrame(frameStartElapsedNanos: Long, frameDurationNanos: Long, expectedPeriodNanos: Long) {
        if (expectedPeriodNanos > 0L && frameDurationNanos > expectedPeriodNanos) {
            consecutiveFrameMisses++
        } else {
            consecutiveFrameMisses = 0
        }
        lastFrameStartElapsedNanos = frameStartElapsedNanos
    }

    /** Clear the watchdog (e.g. after the resource governor responds). */
    fun resetWatchdog() {
        consecutiveFrameMisses = 0
    }
}
