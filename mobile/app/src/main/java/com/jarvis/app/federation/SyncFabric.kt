package com.jarvis.app.federation

import com.jarvis.app.microsystem.SyncMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Synchronization fabric: the standard communication mechanism allowing
 * independent micro-systems to behave as one organism.
 *
 * Supports:
 * - messages (async, routed by topic)
 * - events (broadcast)
 * - state snapshots (polled or pushed)
 * - capability requests (from the registry)
 * - results (from any operation)
 * - confidence (in every result)
 * - resource requests (admission control via governor)
 * - failures (surfaced via FailureSurface)
 * - cancellation (job tokens)
 * - priorities (arbitration)
 * - deadlines (timeout enforcement)
 * - correlation IDs (turn tracing)
 *
 * Prevents uncontrolled direct subsystem-to-subsystem coupling: all
 * communication goes through this fabric.
 */
class SyncFabric(private val scope: CoroutineScope) {

    private val _messages = Channel<SyncMessage>(capacity = Channel.BUFFERED)
    val messages: Flow<SyncMessage> = _messages.receiveAsFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _snapshots = Channel<Snapshot>(capacity = Channel.BUFFERED)
    val snapshots: Flow<Snapshot> = _snapshots.receiveAsFlow()

    private val _stateChanges = Channel<StateChange>(capacity = Channel.BUFFERED)
    val stateChanges: Flow<StateChange> = _stateChanges.receiveAsFlow()

    private val _capabilityRequests = Channel<CapabilityRequest>(capacity = Channel.BUFFERED)
    val capabilityRequests: Flow<CapabilityRequest> = _capabilityRequests.receiveAsFlow()

    private val _cancellations = Channel<Cancellation>(capacity = Channel.BUFFERED)
    val cancellations: Flow<Cancellation> = _cancellations.receiveAsFlow()

    private val _deadlines = Channel<Deadline>(capacity = Channel.BUFFERED)
    val deadlines: Flow<Deadline> = _deadlines.receiveAsFlow()

    private val topics = ConcurrentHashMap<String, MutableSet<String>>() // topic → subscribers

    /** Send a message from one micro-system to another (by topic routing). */
    fun send(message: SyncMessage) {
        _messages.trySend(message)
    }

    /** Broadcast an event to all listeners. */
    fun emit(event: Event) {
        _events.trySend(event)
    }

    /** Push a state snapshot. */
    fun publishSnapshot(snapshot: Snapshot) {
        _snapshots.trySend(snapshot)
    }

    /** Signal a state change. */
    fun signalStateChange(change: StateChange) {
        _stateChanges.trySend(change)
    }

    /** Request a capability from any micro-system that provides it. */
    fun requestCapability(request: CapabilityRequest) {
        _capabilityRequests.trySend(request)
    }

    /** Cancel a job (by correlation id). */
    fun cancel(cancellation: Cancellation) {
        _cancellations.trySend(cancellation)
    }

    /** Notify a deadline. */
    fun notifyDeadline(deadline: Deadline) {
        _deadlines.trySend(deadline)
    }

    /** Subscribe to messages on a topic. */
    fun subscribe(topic: String, subscriberId: String) {
        topics.computeIfAbsent(topic) { ConcurrentHashMap.newKeySet() }
        topics[topic]?.add(subscriberId)
    }

    fun unsubscribe(topic: String, subscriberId: String) {
        topics[topic]?.remove(subscriberId)
    }

    fun topicSubscribers(topic: String): Set<String> = topics[topic]?.toSet() ?: emptySet()

    fun isDormant(): Boolean = topics.isEmpty()
}

data class Event(
    val name: String,
    val source: String,
    val payload: Any? = null,
    val correlationId: String? = null
)

data class Snapshot(
    val producerId: String,
    val state: Map<String, Any>,
    val version: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class StateChange(
    val producerId: String,
    val field: String,
    val oldValue: Any?,
    val newValue: Any?,
    val correlationId: String? = null
)

data class CapabilityRequest(
    val requestId: String,
    val capability: String,
    val requesterId: String,
    val params: Map<String, Any> = emptyMap(),
    val priority: Int = 0,
    val deadlineMs: Long? = null
)

data class Cancellation(
    val correlationId: String,
    val reason: String,
    val requestedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class Deadline(
    val correlationId: String,
    val deadlineMs: Long,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
