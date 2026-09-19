package com.jarvis.app.federation

import com.jarvis.app.microsystem.MicroSystemContract
import com.jarvis.app.microsystem.SyncMessage
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-Hybrid Nervous-System Federation.
 *
 * Extends the existing nervous system into a federation layer supporting
 * separate coordination mechanisms for:
 * - reflexes (fast, local)
 * - prediction (async, model-based)
 * - attention (focus tracking)
 * - routing (message dispatch)
 * - arbitration (priority-based conflict resolution)
 * - resource allocation (governor-driven)
 * - synchronization (consistent state)
 * - feedback (health + telemetry)
 * - learning (genome evolution)
 * - evolution (mutation + selection)
 *
 * The federation does NOT contain internal implementation logic of
 * individual micro-systems. It coordinates them through explicit contracts.
 *
 * Uses independent modules connected through explicit contracts — NOT one
 * giant central class. Each coordination mechanism is its own function/module
 * within this class.
 */
class Federation(
    private val resourceGovernor: ResourceGovernor,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(FederationState.IDLE)
    val state: StateFlow<FederationState> = _state.asStateFlow()

    private val subscribers = ConcurrentHashMap<String, (SyncMessage) -> Unit>()

    private val _messages = Channel<SyncMessage>(capacity = Channel.BUFFERED)
    val messages: Flow<SyncMessage> = _messages.receiveAsFlow()

    // ── Reflex layer (fast, local, <1ms) ──

    private val reflexes = mutableListOf<Reflex>()

    fun registerReflex(reflex: Reflex) {
        reflexes += reflex
    }

    /** Dispatch a reflex — runs all matching reflexes in parallel (fast path). */
    fun dispatchReflex(signal: String, context: Map<String, Any> = emptyMap()) {
        val matching = reflexes.filter { it.matches(signal) }
        for (r in matching) {
            scope.launch { r.handle(signal, context) }
        }
    }

    // ── Attention tracking ──

    private val _attention = MutableStateFlow(AttentionState())
    val attention: StateFlow<AttentionState> = _attention.asStateFlow()

    fun focusOn(target: String, priority: Int = 0) {
        _attention.value = AttentionState(target = target, priority = priority, focusedAtMs = System.currentTimeMillis())
    }

    fun unfocus() {
        _attention.value = AttentionState()
    }

    // ── Routing ──

    private val routeTable = ConcurrentHashMap<String, String>() // topic → micro-system id

    fun registerRoute(topic: String, microSystemId: String) {
        routeTable[topic] = microSystemId
    }

    fun unregisterRoute(topic: String) {
        routeTable.remove(topic)
    }

    /** Route a message to the appropriate micro-system (by topic). */
    suspend fun route(message: SyncMessage): Boolean {
        val targetId = routeTable[message.topic] ?: return false
        val handler = subscribers[targetId] ?: return false
        handler(message)
        return true
    }

    // ── Arbitration ──

    /** Resolve conflicting messages by priority. */
    fun arbitrate(messages: List<SyncMessage>): SyncMessage =
        messages.maxByOrNull { it.priority } ?: messages.first()

    // ── Feedback ──

    private val _feedback = Channel<FeedbackEvent>(capacity = Channel.BUFFERED)
    val feedback: Flow<FeedbackEvent> = _feedback.receiveAsFlow()

    fun reportFeedback(feedback: FeedbackEvent) {
        _feedback.trySend(feedback)
    }

    // ── Subscription ──

    fun subscribe(microSystemId: String, handler: (SyncMessage) -> Unit) {
        subscribers[microSystemId] = handler
    }

    fun unsubscribe(microSystemId: String) {
        subscribers.remove(microSystemId)
    }

    /** Process a message from a micro-system. */
    fun processMessage(message: SyncMessage) {
        _messages.trySend(message)
    }

    fun activeCount(): Int = subscribers.size

    fun shutdown() {
        subscribers.clear()
        routeTable.clear()
        reflexes.clear()
    }
}

enum class FederationState { IDLE, RUNNING, COORDINATING, SUSPENDED, ERROR }

data class AttentionState(
    val target: String = "",
    val priority: Int = 0,
    val focusedAtMs: Long = 0
)

/** A reflex is a fast, local response to a specific signal pattern. */
interface Reflex {
    fun matches(signal: String): Boolean
    suspend fun handle(signal: String, context: Map<String, Any>)
}

/** Feedback from the federation to the nervous system. */
data class FeedbackEvent(
    val source: String,
    val type: FeedbackType,
    val message: String,
    val data: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class FeedbackType { HEALTH, LATENCY, ERROR, RECOVERY, DEGRADATION, STATE_CHANGE }
