package com.jarvis.app.microsystem

import com.jarvis.app.failure.FailureReport
import com.jarvis.app.genome.Genome
import com.jarvis.app.resource.ResourceRequest
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The contract every JARVIS micro-system must implement.
 *
 * A micro-system is an independently replaceable capability that can be
 * created, tested, evolved, and torn down without affecting other
 * micro-systems or the body.
 *
 * The nervous system does NOT contain internal implementation logic.
 * It only communicates with micro-systems through this contract.
 */
interface MicroSystemContract {

    // ── Identity ──

    /** Unique id for this micro-system. */
    val id: String

    /** The genome that produced this micro-system. */
    val genome: Genome

    /** Current version (incremented on hot-reload). */
    val version: Int get() = genome.version

    // ── Lifecycle ──

    /** Construct and initialize the micro-system inside its isolated environment. */
    suspend fun initialize()

    /** Called when the micro-system is promoted to production. */
    suspend fun onPromoted()

    /** Called when the micro-system is being torn down. */
    suspend fun shutdown()

    /** Whether this micro-system is ready to handle requests. */
    val isReady: Boolean

    // ── Capabilities ──

    /** The set of capabilities this micro-system provides. */
    val capabilities: Set<String>

    /** Required capabilities from other micro-systems. */
    val requiredCapabilities: Set<String>

    // ── Health ──

    /** Current health snapshot. */
    val health: StateFlow<MicroSystemHealth>

    /** Health check — returns an updated [MicroSystemHealth]. */
    suspend fun checkHealth(): MicroSystemHealth

    // ── Resource ──

    /** Resource requirements (admission control uses this). */
    val resourceRequirements: ResourceRequest

    /** Live resource consumption (polled by governor). */
    val currentResources: StateFlow<CurrentResourceUsage>

    // ── Synchronization ──

    /** Incoming messages from other micro-systems (via [SyncFabric]). */
    val incoming: SharedFlow<SyncMessage>

    /** Outgoing messages to other micro-systems (routed by [SyncFabric]). */
    val outgoing: SharedFlow<SyncMessage>

    /** Deliver a message from another micro-system to this one. */
    suspend fun deliver(message: SyncMessage)

    /** Emit a message to other micro-systems (queued, non-blocking). */
    fun emit(message: SyncMessage)

    // ── Failure ──

    /** Report a failure to the existing [FailureSurface]. */
    fun reportFailure(report: FailureReport)

    // ── Telemetry ──

    /** Telemetry counters, snapshots, etc. */
    val telemetry: StateFlow<Map<String, Any>>
}

// ── Supporting data classes ──

data class MicroSystemHealth(
    val status: MicroSystemStatus,
    val uptimeMs: Long = 0,
    val lastError: String? = null,
    val consecutiveFailures: Int = 0
)

enum class MicroSystemStatus { UNKNOWN, INITIALIZING, HEALTHY, DEGRADED, FAILING, DISABLED }

data class CurrentResourceUsage(
    val memoryMb: Long = 0,
    val cpuPercent: Double = 0.0,
    val latencyMs: Long = 0,
    val lastActivityMs: Long = 0
)

/** A message exchanged between micro-systems via [SyncFabric]. */
data class SyncMessage(
    val from: String,
    val to: String,
    val topic: String,
    val payload: Any,
    val priority: Int = 0,
    val deadlineMs: Long? = null,
    val correlationId: String? = null
)

/** Result of an operation performed by a micro-system. */
data class OperationResult(
    val success: Boolean,
    val data: Any? = null,
    val confidence: Double = 0.0,
    val latencyMs: Long = 0,
    val error: String? = null
)
