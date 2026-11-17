package com.jarvis.app.symbiosis

/**
 * Hardware identity and accumulated observations for the Android host Friday
 * is currently running on. This is technical device telemetry only: no user
 * content, app inventory, account identifiers, or unique hardware serials.
 */
data class HostFingerprint(
    val manufacturer: String,
    val model: String,
    val device: String,
    val socModel: String,
    val board: String,
    val androidApi: Int,
    val totalMemoryMb: Long,
    val appMemoryClassMb: Int,
    val cpuCoreCount: Int,
    val primaryAbi: String
) {
    val stableKey: String
        get() = listOf(
            manufacturer, model, device, socModel, board, androidApi.toString(),
            totalMemoryMb.toString(), appMemoryClassMb.toString(),
            cpuCoreCount.toString(), primaryAbi
        ).joinToString("|").lowercase()
}

data class HostStudyRecord(
    val fingerprint: HostFingerprint,
    val sampleCount: Long = 0L,
    val minimumAvailableMemoryMb: Long = Long.MAX_VALUE,
    val maximumCpuLoadPercent: Double = 0.0,
    val averageCpuLoadEmaPercent: Double = 0.0,
    val thermalPressureSamples: Long = 0L,
    val lowMemorySamples: Long = 0L,
    val memoryPressureEma: Double = 0.0,
    val thermalPressureEma: Double = 0.0,
    val minimumBatteryPercent: Int = 100,
    val maximumObservedProcessPssMb: Long = 0L,
    val lastObservedAtMs: Long = 0L
) {
    val hasSamples: Boolean get() = sampleCount > 0L

    /** Additional headroom learned from recurring pressure on this host. */
    val additionalMemoryReserveMb: Long
        get() = when {
            memoryPressureEma >= 0.20 && thermalPressureEma >= 0.20 -> 512L
            memoryPressureEma >= 0.20 -> 384L
            thermalPressureEma >= 0.20 -> 256L
            else -> 0L
        }
}

/**
 * A bounded, content-free study of the host. It learns observed constraints;
 * it does not infer capabilities from a chipset name or claim that a synthetic
 * benchmark predicts inference speed.
 */
class HostStudy(fingerprint: HostFingerprint, restored: HostStudyRecord? = null) {
    @Volatile
    private var current = restored?.takeIf { it.fingerprint == fingerprint }
        ?: HostStudyRecord(fingerprint = fingerprint)

    @Synchronized
    fun observe(
        host: HostResourceState,
        processPssMb: Long,
        observedAtMs: Long
    ): HostStudyRecord {
        val previous = current
        val nextSamples = previous.sampleCount + 1L
        val cpu = host.cpuLoadPercent.coerceIn(0.0, 100.0)
        current = previous.copy(
            sampleCount = nextSamples,
            minimumAvailableMemoryMb = minOf(previous.minimumAvailableMemoryMb, host.availableMemoryMb),
            maximumCpuLoadPercent = maxOf(previous.maximumCpuLoadPercent, cpu),
            averageCpuLoadEmaPercent = if (previous.sampleCount == 0L) cpu
                else previous.averageCpuLoadEmaPercent * 0.8 + cpu * 0.2,
            thermalPressureSamples = previous.thermalPressureSamples +
                if (host.thermalLevel >= 1) 1L else 0L,
            lowMemorySamples = previous.lowMemorySamples +
                if (host.availableMemoryMb < 1400L) 1L else 0L,
            memoryPressureEma = previous.memoryPressureEma * 0.9 +
                (if (host.availableMemoryMb < 1400L) 1.0 else 0.0) * 0.1,
            thermalPressureEma = previous.thermalPressureEma * 0.9 +
                (if (host.thermalLevel >= 1) 1.0 else 0.0) * 0.1,
            minimumBatteryPercent = if (host.batteryPercent in 0..100)
                minOf(previous.minimumBatteryPercent, host.batteryPercent)
                else previous.minimumBatteryPercent,
            maximumObservedProcessPssMb = maxOf(
                previous.maximumObservedProcessPssMb,
                processPssMb.coerceAtLeast(0L)
            ),
            lastObservedAtMs = maxOf(previous.lastObservedAtMs, observedAtMs)
        )
        return current
    }

    fun snapshot(): HostStudyRecord = current
}
