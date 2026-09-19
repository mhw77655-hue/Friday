package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.PresenceState

/**
 * The Presence Manager (§12): JARVIS's notion of "am I here, am I paying
 * attention, and what actually happened while I wasn't."
 *
 * Its single most important rule is what it REFUSES to do: it never invents
 * background activity. [PresenceState.backgroundSummary] must come from the
 * orchestrator/autonomous-loop feed — an empty value means "nothing happened
 * while away", and the Expression layer (Consistency Guard) treats any claim
 * to the contrary as a fabrication (§12, §20). That is why "I've been
 * thinking about it all day" is blockable content, not just bad manners.
 *
 * Failure handling (§12): when no system-state signal exists, presence
 * reports a truthful "unknown" rather than guessing. It is honest about its
 * own uncertainty instead of masking it.
 */
class PresenceManager(
    private val bus: StateBus,
    private val clock: () -> Long
) {

    /** Time after which an active session is considered to have lapsed. */
    private val inactiveTimeoutMs = 30 * 60 * 1000L // 30 minutes

    @Volatile private var lastActiveEpochMs: Long? = null
    @Volatile private var backgroundSummary: String? = null
    @Volatile private var systemTier: PresenceState.SystemTier = PresenceState.SystemTier.UNKNOWN

    /**
     * Register user-visible activity (a message arrived / a session began).
     * Returns true when this activity transitioned JARVIS from AWAY to ACTIVE
     * — the Integration Pass uses that to finalize the previous session
     * boundary (§9b, §12).
     */
    fun noteActivity(now: Long = clock()): Boolean {
        // Away-ness is a TIME transition, not the computed mode: when the
        // system tier is DEGRADED/OFFLINE the mode is permanently DEGRADED,
        // so `current().mode != ACTIVE` would report "session began" on EVERY
        // message and re-trigger session start/end each time (HUMAN_CORE_AUDIT
        // m-4). Use the same idle rule current() uses for AWAY.
        val last = lastActiveEpochMs
        val wasAway = last == null || (now - last) > inactiveTimeoutMs
        lastActiveEpochMs = now
        if (wasAway) {
            bus.publish(HcEvent.PresenceChanged(ts = now, reason = "session began", mode = PresenceState.Mode.ACTIVE.name))
        }
        return wasAway
    }

    /** Called by the app when a session ends cleanly (optional). */
    fun endSession(now: Long = clock()) {
        lastActiveEpochMs = now
        bus.publish(HcEvent.PresenceChanged(ts = now, reason = "session ended", mode = current(now).mode.name))
    }

    /** Feed a legitimate background-activity summary from the orchestrator. */
    fun noteBackgroundActivity(summary: String) {
        backgroundSummary = summary
    }

    /** Feed a system kill-switch/availability tier, when one exists (§0.4). */
    fun updateSystemTier(tier: PresenceState.SystemTier) {
        systemTier = tier
    }

    fun current(now: Long = clock()): PresenceState {
        val last = lastActiveEpochMs
        val mode = when {
            systemTier == PresenceState.SystemTier.OFFLINE ||
                systemTier == PresenceState.SystemTier.DEGRADED -> PresenceState.Mode.DEGRADED
            last == null || (now - last) > inactiveTimeoutMs -> PresenceState.Mode.AWAY
            else -> PresenceState.Mode.ACTIVE
        }
        // Background activity is only claimed while away, and only if the
        // orchestrator actually supplied it.
        return PresenceState(
            mode = mode,
            lastActiveEpochMs = last,
            backgroundSummary = if (mode == PresenceState.Mode.AWAY) backgroundSummary else null,
            systemTier = systemTier
        )
    }
}
