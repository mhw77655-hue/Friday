package com.jarvis.app.symbiosis

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A point-in-time view of the host, deliberately independent of Android APIs. */
data class HostResourceState(
    val availableMemoryMb: Long,
    val cpuLoadPercent: Double,
    val thermalLevel: Int,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val availableStorageMb: Long = -1L
)

enum class SymbioticMode { CRITICAL, ECO, BALANCED, PERFORMANCE }

/** The current workload envelope NAE recommends for Friday's own organs. */
data class SymbioticPlan(
    val mode: SymbioticMode,
    val contextTokenBudget: Int,
    val maxConcurrentExpensiveTasks: Int,
    val allowBackgroundConsolidation: Boolean,
    val memoryReserveMb: Long,
    val reason: String
)

data class WorkloadEvidence(
    val samples: Int = 0,
    val successes: Int = 0,
    val failureStreak: Int = 0,
    val latencyEmaMs: Double = 0.0,
    val peakMemoryMb: Long = 0L
) {
    val successRate: Double
        get() = if (samples == 0) 1.0 else successes.toDouble() / samples
}

data class ModelAdmission(
    val allowed: Boolean,
    val contextTokens: Int,
    val requiredMemoryMb: Long,
    val reason: String,
    val plan: SymbioticPlan
)

/**
 * NAE's first adaptive policy.
 *
 * It learns from measured workload outcomes, not from arbitrary self-modifying
 * code. The policy can change its own operating envelope; it cannot change
 * Android settings, kill other apps, or rewrite Friday. Those powers require
 * separate explicit capabilities and approval.
 */
class NanoSymbioticPolicy {
    private val evidence = ConcurrentHashMap<String, WorkloadEvidence>()

    fun recordOutcome(
        organ: String,
        succeeded: Boolean,
        latencyMs: Long,
        peakMemoryMb: Long = 0L
    ) {
        require(organ.isNotBlank())
        val key = organ.trim().lowercase()
        evidence.compute(key) { _, old ->
            val prior = old ?: WorkloadEvidence()
            val nextSamples = prior.samples + 1
            val latency = if (prior.samples == 0) latencyMs.toDouble()
                else prior.latencyEmaMs * 0.8 + latencyMs.coerceAtLeast(0).toDouble() * 0.2
            WorkloadEvidence(
                samples = nextSamples,
                successes = prior.successes + if (succeeded) 1 else 0,
                failureStreak = if (succeeded) 0 else prior.failureStreak + 1,
                latencyEmaMs = latency,
                peakMemoryMb = max(prior.peakMemoryMb, peakMemoryMb)
            )
        }
    }

    fun evidenceFor(organ: String): WorkloadEvidence =
        evidence[organ.trim().lowercase()] ?: WorkloadEvidence()

    fun allEvidence(): Map<String, WorkloadEvidence> = evidence.toMap()

    /** Stable, small persistence representation; no conversation or user content. */
    fun restore(records: Map<String, WorkloadEvidence>) {
        evidence.clear()
        records.forEach { (key, value) ->
            if (key.isNotBlank()) evidence[key.lowercase()] = value
        }
    }

    /**
     * Admit an actual GGUF load against current host measurements and NAE's
     * learned operating envelope. Weight size is not treated as runtime RAM.
     */
    fun admitModel(
        host: HostResourceState,
        modelBytes: Long,
        requestedContextTokens: Int
    ): ModelAdmission {
        val currentPlan = plan(host)
        val blockedReason = when {
            modelBytes <= 0L -> "Model file size is invalid."
            host.thermalLevel >= 2 -> "Thermal pressure is high; wait for the phone to cool."
            host.availableMemoryMb < 650L -> "Available RAM is critically low."
            host.cpuLoadPercent >= 92.0 -> "The phone is already under heavy CPU load."
            host.availableStorageMb in 0L..255L -> "Free storage is too low to preserve a safe reserve."
            else -> null
        }
        val baseContext = min(requestedContextTokens.coerceAtLeast(512), currentPlan.contextTokenBudget)
        val contexts = listOf(baseContext, min(baseContext, 2048), min(baseContext, 1024), min(baseContext, 768), 512)
            .distinct().filter { it > 0 }.sortedDescending()
        val weightWorkingSetMb = ceil(modelBytes.toDouble() / (1024.0 * 1024.0) * 1.12).toLong()
        val reserveMb = max(900L, currentPlan.memoryReserveMb)
        fun required(context: Int): Long =
            weightWorkingSetMb + ceil(context * 0.06).toLong() + 256L + reserveMb

        val selectedContext = if (blockedReason == null) {
            contexts.firstOrNull { host.availableMemoryMb >= required(it) }
        } else null
        val allowed = selectedContext != null
        val requiredMb = if (selectedContext != null) required(selectedContext)
            else contexts.lastOrNull()?.let { required(it) } ?: Long.MAX_VALUE
        val reason = when {
            blockedReason != null -> blockedReason
            selectedContext != null && selectedContext < requestedContextTokens ->
                "Admitted with a reduced context of $selectedContext tokens to protect phone headroom."
            allowed -> "Admitted within NAE's current ${currentPlan.mode} resource envelope."
            else -> "The model working set plus Android reserve exceeds available RAM."
        }
        return ModelAdmission(
            allowed = allowed,
            contextTokens = selectedContext ?: 0,
            requiredMemoryMb = requiredMb,
            reason = reason,
            plan = currentPlan
        )
    }

    fun plan(host: HostResourceState): SymbioticPlan {
        val worstFailureStreak = evidence.values.maxOfOrNull { it.failureStreak } ?: 0
        val learnedPeak = evidence.values.maxOfOrNull { it.peakMemoryMb } ?: 0L
        val reserve = (192L + learnedPeak / 8L + worstFailureStreak * 64L)
            .coerceIn(192L, 768L)

        val mode = when {
            host.thermalLevel >= 2 || host.availableMemoryMb < 650L -> SymbioticMode.CRITICAL
            (!host.isCharging && host.batteryPercent in 0..19) ||
                host.cpuLoadPercent >= 85.0 -> SymbioticMode.ECO
            host.isCharging && host.batteryPercent >= 60 &&
                host.availableMemoryMb >= 3000L && host.thermalLevel == 0 ->
                SymbioticMode.PERFORMANCE
            else -> SymbioticMode.BALANCED
        }

        // Repeated failures teach NAE to back off. Sustained success permits
        // a slightly larger working set only when the host independently has headroom.
        val reliabilityPenalty = if (worstFailureStreak >= 2) 512 else 0
        val context = when (mode) {
            SymbioticMode.CRITICAL -> 768
            SymbioticMode.ECO -> 1024
            SymbioticMode.BALANCED -> 2048
            SymbioticMode.PERFORMANCE -> 4096
        }.minus(reliabilityPenalty).coerceAtLeast(512)

        val backgroundAllowed = mode == SymbioticMode.PERFORMANCE &&
            host.availableStorageMb >= 512L && worstFailureStreak == 0

        val reason = when (mode) {
            SymbioticMode.CRITICAL -> "Protecting responsiveness: memory or thermal pressure is high."
            SymbioticMode.ECO -> "Reducing work to protect battery or CPU headroom."
            SymbioticMode.BALANCED -> "Balancing response quality with available phone resources."
            SymbioticMode.PERFORMANCE -> "The phone currently has headroom for deeper work."
        } + if (reliabilityPenalty > 0) " Recent workload failures reduced the working context." else ""

        return SymbioticPlan(
            mode = mode,
            contextTokenBudget = context,
            maxConcurrentExpensiveTasks = if (mode == SymbioticMode.PERFORMANCE) 2 else 1,
            allowBackgroundConsolidation = backgroundAllowed,
            memoryReserveMb = reserve,
            reason = reason
        )
    }
}
