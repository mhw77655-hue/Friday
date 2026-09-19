package com.jarvis.app.humancore.protocol

/**
 * JARVIS's notion of availability/attention state (§12 Presence Manager).
 *
 * Presence prevents JARVIS from implying continuity it doesn't have — e.g.
 * "I've been thinking about this all day" when no background processing
 * occurred. The summary of background activity is sourced from the
 * orchestrator/autonomous loop, never invented by the Human Core.
 */
data class PresenceState(
    val mode: Mode,
    /** Epoch millis of the last active session start. */
    val lastActiveEpochMs: Long?,
    /**
     * Factual summary of any legitimate background activity while away
     * (e.g. an autonomous loop tick touched something relevant). Must be
     * sourced from the orchestrator; empty means "nothing happened while
     * away" — never filled in speculatively (§12, §20 authenticity).
     */
    val backgroundSummary: String?,
    /** Last known system availability tier, if the kill-switch reports it. */
    val systemTier: SystemTier
) {
    enum class Mode { ACTIVE, AWAY, DEGRADED }

    enum class SystemTier { FULL, DEGRADED, OFFLINE, UNKNOWN }

    companion object {
        /** Default when no system-state signal is available (§12 Failure handling). */
        val UNKNOWN = PresenceState(
            mode = Mode.AWAY,
            lastActiveEpochMs = null,
            backgroundSummary = null,
            systemTier = SystemTier.UNKNOWN
        )
    }
}
