package ai.friday.assistant.companioncore.contract

/**
 * Advisory, fire-and-forget message from the Android body to Friday's local runtime
 * (§1, spec §0.3/§2.3).
 *
 * Recorded locally by FridayRuntimeIntegration as observable user-presence evidence.
 * It is not sent to a legacy Human Core or remote service. Emitted on state
 * changes only (not continuous polling).
 *
 * @property eventType what happened, from Friday's Android body vantage.
 * @property timestamp epoch millis when the event occurred.
 * @property confidence in [0,1] how sure the local observer is of the read.
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
        DEVICE_BACKGROUNDED,
        DEVICE_FOREGROUNDED
    }
}
