package com.jarvis.app.microsystem

import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.Genome
import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.ResourceSandbox
import com.jarvis.app.resource.ResourceRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * A micro-system that is callable directly: an organism whose capability is
 * a promoted [AlgorithmSpec]. Registered into the [MicroSystemRegistry] on
 * promotion, discovered by capability, invoked by the nervous system's
 * capability router. This is how a newly-created tool "becomes callable"
 * (§22).
 *
 * The spec executes inside the [ResourceSandbox] — time-bounded, crash-captured,
 * never silent. Failures surface through the canonical [FailureSurface].
 */
class SpecMicroSystem(
    override val id: String,
    override val genome: Genome,
    val spec: AlgorithmSpec,
    override val capabilities: Set<String> = setOf(spec.capability),
    override val requiredCapabilities: Set<String> = emptySet(),
    private val sandbox: ResourceSandbox = ResourceSandbox(),
    private val failureSurface: FailureSurface? = null,
    private val onResult: ((SyncMessage) -> Unit)? = null
) : MicroSystemContract, CallableMicroSystem {

    private val _status = MutableStateFlow(MicroSystemStatus.INITIALIZING)
    private val _health = MutableStateFlow(MicroSystemHealth(MicroSystemStatus.INITIALIZING))
    override val health: StateFlow<MicroSystemHealth> = _health.asStateFlow()

    override val resourceRequirements: ResourceRequest = ResourceRequest(
        expectedMemoryMb = genome.resourceBudget.maxMemoryMb,
        expectedCpuPercent = genome.resourceBudget.maxCpuPercent,
        expectedRuntimeMs = genome.latencyBudget.maxFullResponseMs,
        priority = genome.resourceBudget.priority,
        latencySensitive = genome.resourceBudget.isLatencySensitive
    )

    private val _currentResources = MutableStateFlow(CurrentResourceUsage())
    override val currentResources: StateFlow<CurrentResourceUsage> = _currentResources.asStateFlow()

    private val _incoming = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 8)
    override val incoming: SharedFlow<SyncMessage> = _incoming.asSharedFlow()

    private val _outgoing = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 8)
    override val outgoing: SharedFlow<SyncMessage> = _outgoing.asSharedFlow()

    private val _telemetry = MutableStateFlow<Map<String, Any>>(emptyMap())
    override val telemetry: StateFlow<Map<String, Any>> = _telemetry.asStateFlow()

    private val callCount = AtomicLong(0)
    private val lastLatencyMs = AtomicLong(0)

    val lastResult = MutableStateFlow<OperationResult?>(null)

    override suspend fun initialize() {
        _status.value = MicroSystemStatus.HEALTHY
        _health.value = MicroSystemHealth(status = MicroSystemStatus.HEALTHY)
    }

    override suspend fun onPromoted() {
        initialize()
    }

    override suspend fun shutdown() {
        _status.value = MicroSystemStatus.DISABLED
        _health.value = _health.value.copy(status = MicroSystemStatus.DISABLED)
        sandbox.shutdown()
    }

    override val isReady: Boolean get() = _status.value == MicroSystemStatus.HEALTHY

    override suspend fun checkHealth(): MicroSystemHealth = _health.value

    override suspend fun deliver(message: SyncMessage) {
        _incoming.tryEmit(message)
        val inputs = message.payload as? Map<*, *>
        val result = if (inputs != null) {
            // Coerce the untagged payload into the call contract: drop nulls,
            // stringify keys. A non-map payload is a loud call failure.
            val coerced = inputs.entries.mapNotNull { (k, v) -> v?.let { k.toString() to it } }.toMap()
            call(coerced)
        } else {
            OperationResult(success = false, error = "SpecMicroSystem expects a Map payload, got ${message.payload?.javaClass?.simpleName}")
        }
        lastResult.value = result
        if (!result.success) {
            reportFailure(
                FailureReport(
                    subsystem = "ORGANISM",
                    operation = "call:${spec.capability}",
                    severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                    category = com.jarvis.app.failure.FailureCategory.EVOLUTION,
                    message = result.error ?: "call failed",
                    source = id
                )
            )
        }
        onResult?.invoke(
            SyncMessage(from = id, to = message.from, topic = "${spec.capability}.result", payload = result)
        )
    }

    override fun emit(message: SyncMessage) {
        _outgoing.tryEmit(message)
    }

    override fun reportFailure(report: FailureReport) {
        failureSurface?.report(report)
    }

    /** Direct call path used by the capability router (cheapest valid route). */
    override suspend fun call(inputs: Map<String, Any>): OperationResult {
        val result = sandbox.run(spec, inputs)
        callCount.incrementAndGet()
        lastLatencyMs.set(result.latencyMs)
        _currentResources.value = CurrentResourceUsage(
            memoryMb = genome.resourceBudget.maxMemoryMb,
            cpuPercent = genome.resourceBudget.maxCpuPercent,
            latencyMs = result.latencyMs,
            lastActivityMs = System.currentTimeMillis()
        )
        _telemetry.value = mapOf(
            "calls" to callCount.get(),
            "lastLatencyMs" to lastLatencyMs.get(),
            "capability" to spec.capability
        )
        // §11: failure-first — a failed call is loud on the canonical surface,
        // never silent. The router and nervous system observe the same surface.
        if (!result.success) {
            reportFailure(
                FailureReport(
                    subsystem = "ORGANISM",
                    operation = "call:${spec.capability}",
                    severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                    category = com.jarvis.app.failure.FailureCategory.EVOLUTION,
                    message = result.error ?: "call failed",
                    source = id
                )
            )
        }
        return result
    }

    companion object {
        fun topic(capability: String): String = "capability.$capability"
    }
}

/** A micro-system that can be invoked synchronously with an input map. */
interface CallableMicroSystem {
    suspend fun call(inputs: Map<String, Any>): OperationResult
}
