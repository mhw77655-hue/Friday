package ai.friday.assistant.companioncore.engine

import ai.friday.assistant.companioncore.contract.UserPresenceEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local evidence cache for actual Android user-presence and lifecycle
 * events. This is observation, not Friday's existential presence state.
 */
object FridayPresenceEvidenceStore {
    data class Snapshot(
        val userPresent: Boolean? = null,
        val appForeground: Boolean? = null,
        val lastInteractionAtMs: Long? = null,
        val interactionCount: Long = 0L,
        val lastEventType: String? = null,
        val lastEventAtMs: Long? = null
    )

    private val mutable = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = mutable.asStateFlow()

    @Synchronized
    fun record(event: UserPresenceEvent) {
        val previous = mutable.value
        val next = when (event.eventType) {
            UserPresenceEvent.EventType.USER_PRESENT ->
                previous.copy(userPresent = true)
            UserPresenceEvent.EventType.USER_ABSENT ->
                previous.copy(userPresent = false)
            UserPresenceEvent.EventType.DEVICE_BACKGROUNDED ->
                previous.copy(appForeground = false)
            UserPresenceEvent.EventType.DEVICE_FOREGROUNDED ->
                previous.copy(appForeground = true)
            UserPresenceEvent.EventType.USER_TAPPED ->
                previous.copy(
                    lastInteractionAtMs = event.timestamp,
                    interactionCount = previous.interactionCount + 1
                )
            UserPresenceEvent.EventType.USER_IDLE_THRESHOLD ->
                previous.copy(userPresent = false)
            UserPresenceEvent.EventType.USER_LOOKED_AWAY -> previous
        }.copy(
            lastEventType = event.eventType.name,
            lastEventAtMs = event.timestamp
        )
        mutable.value = next
    }

    @Synchronized
    fun recordUserInteraction(timestamp: Long) {
        val previous = mutable.value
        mutable.value = previous.copy(
            lastInteractionAtMs = timestamp,
            interactionCount = previous.interactionCount + 1,
            lastEventType = "USER_INTERACTION",
            lastEventAtMs = timestamp
        )
    }

    @Synchronized
    fun resetForTests() {
        mutable.value = Snapshot()
    }
}
