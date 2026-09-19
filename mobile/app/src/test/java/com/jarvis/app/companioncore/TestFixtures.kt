package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.engine.CompanionClock
import com.jarvis.app.humancore.protocol.PresenceState
import com.jarvis.app.humancore.protocol.StateSnapshot

/** Controllable epoch clock for Companion Core tests. */
class ManualEpochClock(var now: Long = 1_700_000_000_000L) {
    fun advance(ms: Long) {
        now += ms
    }
}

/** Controllable monotonic + epoch clock pair driving a [CompanionClock]. */
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

/** A deterministic Human Core snapshot for binding/mapping tests. */
fun snapshot(
    ts: Long,
    valence: Double? = 0.6,
    arousal: Double? = 0.7,
    trust: Double? = 0.8,
    bondDepth: Double? = 0.5,
    totalInteractions: Long = 42L,
    secondsSinceLastContact: Long? = 30L,
    presenceMode: PresenceState.Mode = PresenceState.Mode.ACTIVE,
    lastUserConfidence: Double? = 0.9
): StateSnapshot = StateSnapshot(
    ts = ts,
    identityName = "JARVIS",
    identityVersion = 1,
    traits = emptyMap(),
    moodValence = valence,
    moodArousal = arousal,
    trust = trust,
    bondDepth = bondDepth,
    significantEventCount = 3,
    totalInteractions = totalInteractions,
    secondsSinceLastContact = secondsSinceLastContact,
    presence = PresenceState(
        mode = presenceMode,
        lastActiveEpochMs = ts,
        backgroundSummary = null,
        systemTier = PresenceState.SystemTier.FULL
    ),
    lastUserValence = valence,
    lastUserArousal = arousal,
    lastUserConfidence = lastUserConfidence
)
