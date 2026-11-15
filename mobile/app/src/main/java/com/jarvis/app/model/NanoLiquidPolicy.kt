package com.jarvis.app.model

data class NanoHostState(
    val availableMemoryMb: Long,
    val totalMemoryMb: Long,
    val freeStorageMb: Long,
    val thermalLevel: Int,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val cpuCores: Int,
    val lowMemory: Boolean = false,
    val appPssMb: Long = 0L
)

data class NanoLiquidProfile(
    val threads: Int,
    val contextTokens: Int,
    val batchTokens: Int,
    val mode: String,
    val reason: String
)

data class NanoModelAdmission(
    val allowed: Boolean,
    val requiredFreeMemoryMb: Long,
    val availableFreeMemoryMb: Long,
    val suggestedContextTokens: Int?,
    val reason: String
)

/**
 * Deterministic NAE policy. It protects the host first, then uses learned
 * per-device measurements to choose the least costly thread count.
 */
object NanoLiquidPolicy {
    /**
     * Conservative preflight: estimate quantized weights + KV cache + native
     * buffers while preserving a system reserve. This is a guard, not a
     * promise that every model will load; native allocation can still fail.
     */
    fun admitModel(
        host: NanoHostState,
        modelBytes: Long,
        contextTokens: Int,
        systemReserveMb: Long = 900L
    ): NanoModelAdmission {
        val modelMb = (modelBytes.coerceAtLeast(0L) + 1_048_575L) / 1_048_576L
        val weightWorkingSet = (modelMb * 1.12).toLong()
        val nativeBuffers = 256L
        fun required(context: Int): Long =
            weightWorkingSet + (context.coerceAtLeast(0) * 0.06).toLong() + nativeBuffers + systemReserveMb

        val blockedByPressure = host.lowMemory || host.thermalLevel >= 2
        val requestedRequired = required(contextTokens)
        val contextFallback = listOf(4096, 2048).firstOrNull { it < contextTokens && required(it) <= host.availableMemoryMb }
        val allowed = !blockedByPressure &&
            (host.availableMemoryMb >= requestedRequired || contextFallback != null)
        val selectedContext = when {
            !allowed -> null
            host.availableMemoryMb >= requestedRequired -> contextTokens
            else -> contextFallback
        }
        val requiredSelected = selectedContext?.let(::required) ?: requestedRequired
        val reason = when {
            host.lowMemory -> "Android reports low memory; model load was blocked to protect the phone."
            host.thermalLevel >= 2 -> "Thermal pressure is high; model load was deferred."
            !allowed -> "Model needs about $requestedRequired MB free including context and system reserve; only ${host.availableMemoryMb} MB is available."
            selectedContext != contextTokens -> "Model admitted with context reduced from $contextTokens to $selectedContext tokens to protect phone memory."
            else -> "Model fits the conservative free-memory budget."
        }
        return NanoModelAdmission(allowed, requiredSelected, host.availableMemoryMb, selectedContext, reason)
    }

    fun recommend(
        host: NanoHostState,
        throughputTokensPerSecond: Map<Int, Float> = emptyMap(),
        tokensPerBatteryPercent: Map<Int, Float> = emptyMap()
    ): NanoLiquidProfile {
        val batteryCritical = host.batteryPercent in 0..14 && !host.isCharging
        val memoryCritical = host.lowMemory || host.availableMemoryMb < 640
        val thermalCritical = host.thermalLevel >= 2

        if (batteryCritical || memoryCritical || thermalCritical) {
            val reason = when {
                thermalCritical -> "Thermal pressure: reduce inference heat and let the device cool."
                memoryCritical -> "Memory pressure: use the smallest safe working set."
                else -> "Battery reserve is low: conserve energy."
            }
            return NanoLiquidProfile(1, 2048, 64, "conserve", reason)
        }

        val safeThreadCeiling = when {
            host.availableMemoryMb < 1024 || host.thermalLevel == 1 -> 2
            host.availableMemoryMb < 2048 -> 3
            else -> 4
        }.coerceAtMost(host.cpuCores.coerceAtLeast(1))

        val context = when {
            host.availableMemoryMb < 900 -> 2048
            host.availableMemoryMb < 1800 -> 4096
            else -> 8192
        }
        val batch = when {
            host.availableMemoryMb < 900 -> 64
            host.availableMemoryMb < 1800 -> 128
            else -> 256
        }

        val efficiency = tokensPerBatteryPercent.filterKeys { it in 1..safeThreadCeiling }
        val throughput = throughputTokensPerSecond.filterKeys { it in 1..safeThreadCeiling }
        val measurements = if (efficiency.isNotEmpty()) efficiency else throughput

        val selectedThreads = if (measurements.isEmpty()) {
            safeThreadCeiling.coerceAtMost(2).coerceAtLeast(1)
        } else {
            val best = measurements.values.maxOrNull() ?: 0f
            measurements.entries
                .filter { it.value >= best * 0.90f }
                .minByOrNull { it.key }
                ?.key ?: safeThreadCeiling.coerceAtMost(2).coerceAtLeast(1)
        }

        val mode = when {
            host.thermalLevel == 1 -> "balanced-cool"
            host.availableMemoryMb < 1800 -> "balanced"
            else -> "headroom"
        }
        val reason = if (efficiency.isNotEmpty()) {
            "Selected from measured tokens per battery percentage on this phone."
        } else if (throughput.isNotEmpty()) {
            "Selected from measured generation speed; energy data is not available yet."
        } else {
            "Safe initial settings; NAE will refine them from real inference sessions."
        }
        return NanoLiquidProfile(selectedThreads, context, batch, mode, reason)
    }
}
