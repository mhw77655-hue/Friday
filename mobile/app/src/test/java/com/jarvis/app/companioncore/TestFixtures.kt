package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.engine.CompanionClock

/** Controllable epoch clock for Friday runtime tests. */
class ManualEpochClock(var now: Long = 1_700_000_000_000L) {
    fun advance(ms: Long) {
        now += ms
    }
}

/** Controllable monotonic + epoch clock pair driving a CompanionClock. */
class ManualCompanionClockSource {
    var monotonicNanos: Long = 0L
    var epochMs: Long = 1_700_000_000_000L

    fun advanceNanos(ns: Long) {
        monotonicNanos += ns
    }

    fun advanceMillis(ms: Long) {
        monotonicNanos += ms * CompanionClock.NANOS_PER_MILLI
        epochMs += ms
    }

    fun make(): CompanionClock = CompanionClock(
        monotonicNanos = { monotonicNanos },
        epochMs = { epochMs }
    )
}
