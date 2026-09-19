package com.jarvis.app.companioncore.contract

/**
 * Advisory, fire-and-forget message from the Companion Core to the Human Core
 * (§1, spec §0.3/§2.3).
 *
 * Delivered outward via the additive `HumanCore.adviseUserPresence(...)`
 * facade (audit A-7/V-2) — never by direct CC→`PresenceManager` calls — so
 * the frozen-HC read-only contract holds. Emitted on state changes only (not
 * continuous polling) to avoid flooding the HC loop.
 *
 * @property eventType what happened, from the Companion Core's vantage.
 * @property timestamp epoch millis when the event occurred.
 * @property confidence in [0,1] how sure the Companion Core is of the read.
 */
data class UserPresenceEvent(
    val eventType: EventType,
    val timestamp: Long,
    val confidence: Double
) {
    enum class EventType {
        USER_PRESENT,
        USER_ABSENT,
        USER_LOOKED_AWAY,
        USER_TAPPED,
        USER_IDLE_THRESHOLD,
        DEVICE_BACKGROUNDED
    }
}
