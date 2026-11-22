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
    val cpuLoadPercent: Double,
    val processPssMb: Long,
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
        // Reuse Friday's existing Nano Symbiotic Engine policy rather than
        // creating a competing host scheduler. Unknown CPU load prevents the
        // high-capacity profile from being selected.
        val measuredCpu = host.cpuLoadPercent.takeIf { it in 0.0..100.0 }
        val state = ai.friday.assistant.symbiosis.HostResourceState(
            availableMemoryMb = host.availableMemoryMb,
            cpuLoadPercent = measuredCpu ?: 0.0,
            thermalLevel = host.thermalStatus,
            batteryPercent = host.batteryPercent,
            isCharging = host.charging && measuredCpu != null && measuredCpu <= 75.0,
            availableStorageMb = host.availableStorageMb
        )
        val nanoPlan = ai.friday.assistant.symbiosis.NanoSymbioticPolicy().plan(state)
        val mode = when (nanoPlan.mode) {
            ai.friday.assistant.symbiosis.SymbioticMode.CRITICAL -> HostOperatingMode.PROTECTIVE
            ai.friday.assistant.symbiosis.SymbioticMode.ECO -> HostOperatingMode.EFFICIENT
            ai.friday.assistant.symbiosis.SymbioticMode.BALANCED -> HostOperatingMode.BALANCED
            ai.friday.assistant.symbiosis.SymbioticMode.PERFORMANCE -> HostOperatingMode.HIGH_CAPACITY
        }
        val localModelAllowed = host.installedGgufCount > 0 &&
            host.nativeInferenceLibraryPresent &&
            host.availableMemoryMb >= 900L &&
            host.thermalStatus < 2
        val modelBudget = when (mode) {
            HostOperatingMode.PROTECTIVE -> 0L
            HostOperatingMode.EFFICIENT -> 512L * 1024L * 1024L
            HostOperatingMode.BALANCED -> 2L * 1024L * 1024L * 1024L
            HostOperatingMode.HIGH_CAPACITY -> 4L * 1024L * 1024L * 1024L
        }
        return HostConfiguration(
            mode = mode,
            contextTokenBudget = nanoPlan.contextTokenBudget,
            maxConcurrentExpensiveTasks = nanoPlan.maxConcurrentExpensiveTasks,
            allowBackgroundIndexing = nanoPlan.allowBackgroundConsolidation,
            allowLocalModelLoad = localModelAllowed && mode != HostOperatingMode.PROTECTIVE,
            recommendedModelMaxBytes = modelBudget,
            reason = if (measuredCpu == null) {
                nanoPlan.reason + " CPU load was unavailable, so high-capacity admission was withheld."
            } else nanoPlan.reason
        )
    }
}


enum class ShizukuPrivilege { ROOT, ADB_SHELL, UNKNOWN, NOT_RUNNING }

data class ShizukuCapabilityState(
    val managerInstalled: Boolean,
    val binderReachable: Boolean,
    val fridayAuthorized: Boolean,
    val serverUid: Int?
) {
    val privilege: ShizukuPrivilege
        get() = when {
            !binderReachable -> ShizukuPrivilege.NOT_RUNNING
            serverUid == 0 -> ShizukuPrivilege.ROOT
            serverUid == 2000 -> ShizukuPrivilege.ADB_SHELL
            else -> ShizukuPrivilege.UNKNOWN
        }

    val usableByFriday: Boolean
        get() = managerInstalled && binderReachable && fridayAuthorized &&
            privilege in setOf(ShizukuPrivilege.ROOT, ShizukuPrivilege.ADB_SHELL)
}
