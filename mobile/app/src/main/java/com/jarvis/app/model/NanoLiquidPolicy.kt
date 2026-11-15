package com.jarvis.app.model

data class NanoHostState(
    val availableMemoryMb: Long,
    val totalMemoryMb: Long,
    val freeStorageMb: Long,
    val thermalLevel: Int,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val cpuCores: Int,
    val lowMemory: Boolean = false
)

data class NanoLiquidProfile(
    val threads: Int,
    val contextTokens: Int,
    val batchTokens: Int,
    val mode: String,
    val reason: String
)

/**
 * Deterministic NAE policy. It protects the host first, then uses learned
 * per-device measurements to choose the least costly thread count.
 */
object NanoLiquidPolicy {
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
