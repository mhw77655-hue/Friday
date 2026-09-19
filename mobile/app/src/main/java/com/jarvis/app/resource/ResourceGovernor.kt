package com.jarvis.app.resource

import com.jarvis.app.failure.FailureReport
import com.jarvis.app.genome.Genome
import com.jarvis.app.microsystem.CurrentResourceUsage
import com.jarvis.app.microsystem.MicroSystemContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Resource-aware execution governor.
 *
 * Tracks RAM, CPU, GPU/NPU, thermal, battery, model residency, and
 * concurrent workloads. Implements admission control so a generated
 * micro-system can declare what it needs, and the governor decides
 * whether it may execute.
 *
 * Dormant when unused; event-driven; no continuous background processing.
 */
class ResourceGovernor(private val nowMs: () -> Long = { System.currentTimeMillis() }) {

    // ── Admission control ──

    private val activeResources = ConcurrentHashMap<String, CurrentResourceUsage>()

    private val _admissionPolicy = MutableStateFlow(AdmissionPolicy())
    val admissionPolicy: StateFlow<AdmissionPolicy> = _admissionPolicy.asStateFlow()

    private val _resourceState = MutableStateFlow(DeviceResourceState())
    val resourceState: StateFlow<DeviceResourceState> = _resourceState.asStateFlow()

    /** Request admission for a micro-system. Returns granted + reasoning. */
    fun requestAdmission(genome: Genome, caller: String = genome.id): AdmissionResult {
        val budget = genome.resourceBudget
        val policy = _admissionPolicy.value

        val usedMemoryMb = activeResources.values.sumOf { it.memoryMb }
        val usedCpu = activeResources.values.sumOf { it.cpuPercent }

        val remainingMemory = policy.totalMemoryMb - usedMemoryMb
        val remainingCpu = policy.totalCpuPercent - usedCpu

        val reasons = mutableListOf<String>()
        var admitted = true

        // Memory check
        if (budget.maxMemoryMb > remainingMemory) {
            reasons += "Insufficient memory: need ${budget.maxMemoryMb}MB, have ${remainingMemory}MB"
            admitted = false
        }
        if (budget.maxMemoryMb > policy.maxPerSystemMemoryMb) {
            reasons += "Exceeds per-system limit: ${budget.maxMemoryMb}MB > ${policy.maxPerSystemMemoryMb}MB"
            admitted = false
        }

        // CPU check
        if (budget.maxCpuPercent > remainingCpu) {
            reasons += "Insufficient CPU: need ${budget.maxCpuPercent}%, have ${remainingCpu}%"
            admitted = false
        }

        // Thermal check
        if (_resourceState.value.thermalLevel > 1 && budget.isLatencySensitive) {
            reasons += "Device is thermally throttled; latency-sensitive system rejected"
            admitted = false
        }

        // Battery check — reject when below minimum, unless charging is allowed
        // AND the device is actually charging.
        if (_resourceState.value.batteryPercent < policy.minBatteryPercent &&
            !(_resourceState.value.isCharging && policy.allowWhileCharging)
        ) {
            reasons += "Battery ${_resourceState.value.batteryPercent}% below minimum ${policy.minBatteryPercent}%"
            admitted = false
        }

        // Concurrent workload check
        if (activeResources.size >= policy.maxConcurrentSystems) {
            reasons += "Concurrent system limit reached: ${activeResources.size}/${policy.maxConcurrentSystems}"
            admitted = false
        }

        // Compatibility check
        genome.safetyConstraints.forEach { constraint ->
            when (constraint) {
                com.jarvis.app.genome.SafetyConstraint.NO_NETWORK -> {
                    if (_resourceState.value.networkAvailable) {
                        reasons += "Safety constraint: network must be unavailable"
                    }
                }
                else -> {} // Other constraints checked at build time
            }
        }

        if (admitted) {
            activeResources[caller] = CurrentResourceUsage(
                memoryMb = budget.maxMemoryMb,
                cpuPercent = budget.maxCpuPercent,
                latencyMs = 0,
                lastActivityMs = nowMs()
            )
        }

        return AdmissionResult(
            admitted = admitted,
            reasons = reasons,
            remainingMemoryMb = remainingMemory,
            remainingCpuPercent = remainingCpu,
            concurrentSystems = activeResources.size
        )
    }

    /**
     * Lightweight admission check (no registration side effects).
     * True when a system needing [memoryMb] RAM and [cpuPercent] CPU could be
     * admitted under the current policy and device state.
     */
    fun canAdmit(memoryMb: Long, cpuPercent: Double): Boolean {
        val policy = _admissionPolicy.value
        val usedMemoryMb = activeResources.values.sumOf { it.memoryMb }
        val usedCpu = activeResources.values.sumOf { it.cpuPercent }
        val state = _resourceState.value

        return memoryMb <= (policy.totalMemoryMb - usedMemoryMb) &&
            memoryMb <= policy.maxPerSystemMemoryMb &&
            cpuPercent <= (policy.totalCpuPercent - usedCpu) &&
            activeResources.size < policy.maxConcurrentSystems &&
            (state.thermalLevel <= 1 ||
                state.batteryPercent >= policy.minBatteryPercent ||
                (state.isCharging && policy.allowWhileCharging))
    }

    /** Release resources held by a micro-system. */
    fun release(caller: String) {
        activeResources.remove(caller)
    }

    /** Report actual resource usage from a running micro-system. */
    fun reportUsage(caller: String, usage: CurrentResourceUsage) {
        activeResources[caller] = usage
    }

    /** Update the device resource state (call when system signals change). */
    fun updateDeviceState(state: DeviceResourceState) {
        _resourceState.value = state
    }

    /** Update admission policy. */
    fun updatePolicy(policy: AdmissionPolicy) {
        _admissionPolicy.value = policy
    }

    fun activeCount(): Int = activeResources.size

    fun activeSystems(): Map<String, CurrentResourceUsage> = activeResources.toMap()

    fun totalMemoryMb(): Long = activeResources.values.sumOf { it.memoryMb }
    fun totalCpuPercent(): Double = activeResources.values.sumOf { it.cpuPercent }
}

data class AdmissionPolicy(
    val totalMemoryMb: Long = 6000,        // 8GB device, reserve ~2GB for OS
    val totalCpuPercent: Double = 80.0,     // leave headroom for OS
    val maxPerSystemMemoryMb: Long = 1024,
    val maxConcurrentSystems: Int = 3,
    val minBatteryPercent: Int = 5,
    val allowWhileCharging: Boolean = true
)

data class AdmissionResult(
    val admitted: Boolean,
    val reasons: List<String>,
    val remainingMemoryMb: Long,
    val remainingCpuPercent: Double,
    val concurrentSystems: Int
)

/**
 * A micro-system's resource declaration, used for admission control.
 * A generated micro-system declares what it expects; the governor decides
 * whether it may execute.
 */
data class ResourceRequest(
    val expectedMemoryMb: Long,
    val expectedCpuPercent: Double,
    val expectedRuntimeMs: Long,
    val requiredAccelerator: String? = null,
    val priority: Int = 0,
    val latencySensitive: Boolean = false
)

data class DeviceResourceState(
    val totalMemoryMb: Long = 6000,
    val availableMemoryMb: Long = 4000,
    val thermalLevel: Int = 0,           // 0=normal, 1=throttled, 2=critical
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val networkAvailable: Boolean = false,
    val gpuAvailable: Boolean = false,
    val executionLatencyMs: Long = 0
)
