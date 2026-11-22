package ai.friday.assistant.bootstrap.host

/**
 * Deterministic host-to-workload policy. Inputs are measured locally; outputs
 * are recommendations only and never change Android settings or permissions.
 */
data class HostCapacity(
    val apiLevel: Int,
    val availableMemoryMb: Long,
    val memoryClassMb: Int,
    val availableStorageMb: Long,
    val cpuCores: Int,
    val primaryAbi: String,
    val thermalStatus: Int,
    val batteryPercent: Int,
    val charging: Boolean,
    val powerSave: Boolean,
    val installedGgufCount: Int,
    val nativeInferenceLibraryPresent: Boolean
)

enum class HostOperatingMode { PROTECTIVE, EFFICIENT, BALANCED, HIGH_CAPACITY }

data class HostConfiguration(
    val mode: HostOperatingMode,
    val contextTokenBudget: Int,
    val maxConcurrentExpensiveTasks: Int,
    val allowBackgroundIndexing: Boolean,
    val allowLocalModelLoad: Boolean,
    val recommendedModelMaxBytes: Long,
    val reason: String
)

object HostBootstrapPolicy {
    fun configure(host: HostCapacity): HostConfiguration {
        val critical = host.thermalStatus >= 4 ||
            host.availableMemoryMb < 650L ||
            host.availableStorageMb in 0L..255L ||
            (!host.charging && host.batteryPercent in 0..8)
        val constrained = critical ||
            host.thermalStatus >= 3 ||
            host.availableMemoryMb < 1400L ||
            host.powerSave ||
            (!host.charging && host.batteryPercent in 0..20)

        val mode = when {
            critical -> HostOperatingMode.PROTECTIVE
            constrained -> HostOperatingMode.EFFICIENT
            host.charging && host.availableMemoryMb >= 3000L &&
                host.availableStorageMb >= 4096L && host.thermalStatus <= 1 ->
                HostOperatingMode.HIGH_CAPACITY
            else -> HostOperatingMode.BALANCED
        }

        val context = when (mode) {
            HostOperatingMode.PROTECTIVE -> 512
            HostOperatingMode.EFFICIENT -> 1024
            HostOperatingMode.BALANCED -> 2048
            HostOperatingMode.HIGH_CAPACITY -> 4096
        }
        val localModelAllowed = host.installedGgufCount > 0 &&
            host.nativeInferenceLibraryPresent &&
            host.availableMemoryMb >= 900L &&
            host.thermalStatus < 4
        val modelBudget = when (mode) {
            HostOperatingMode.PROTECTIVE -> 0L
            HostOperatingMode.EFFICIENT -> 512L * 1024L * 1024L
            HostOperatingMode.BALANCED -> 2L * 1024L * 1024L * 1024L
            HostOperatingMode.HIGH_CAPACITY -> 4L * 1024L * 1024L * 1024L
        }

        val reason = when (mode) {
            HostOperatingMode.PROTECTIVE -> "Host pressure is high; preserve responsiveness and defer expensive work."
            HostOperatingMode.EFFICIENT -> "Constrained host; use small contexts and serialize expensive work."
            HostOperatingMode.BALANCED -> "Use balanced local workloads with one expensive task at a time."
            HostOperatingMode.HIGH_CAPACITY -> "Measured headroom currently supports deeper local workloads."
        }
        return HostConfiguration(
            mode = mode,
            contextTokenBudget = context,
            maxConcurrentExpensiveTasks = if (mode == HostOperatingMode.HIGH_CAPACITY) 2 else 1,
            allowBackgroundIndexing = mode == HostOperatingMode.HIGH_CAPACITY &&
                host.availableStorageMb >= 2048L && host.batteryPercent !in 0..19,
            allowLocalModelLoad = localModelAllowed,
            recommendedModelMaxBytes = modelBudget,
            reason = reason
        )
    }
}
