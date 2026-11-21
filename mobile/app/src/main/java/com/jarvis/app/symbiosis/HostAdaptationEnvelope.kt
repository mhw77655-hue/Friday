package com.jarvis.app.symbiosis

import org.json.JSONObject

/**
 * Privacy-bounded description of the Android host for Friday's adaptation
 * control plane. Contains device/runtime facts and aggregate measurements only:
 * never app inventory, user content, account IDs, serials, transcripts or secrets.
 */
data class HostAdaptationEnvelope(
    val schemaVersion: Int,
    val host: HostFingerprint,
    val resources: HostResourceState,
    val plan: SymbioticPlan,
    val hostStudySamples: Long,
    val processPssMb: Long,
    val generatedAtEpochMs: Long,
    val sourceRevision: String,
    val network: HostNetworkState
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION)
        require(host.androidApi >= 1)
        require(hostStudySamples >= 0L)
        require(processPssMb >= 0L)
        require(generatedAtEpochMs > 0L)
        require(sourceRevision.isNotBlank())
    }

    /** Stable JSON wire representation; fields are explicit and schema-versioned. */
    fun toJson(): JSONObject = JSONObject()
        .put("schema_version", schemaVersion)
        .put("generated_at_epoch_ms", generatedAtEpochMs)
        .put("source_revision", sourceRevision)
        .put("host", JSONObject()
            .put("manufacturer", host.manufacturer)
            .put("model", host.model)
            .put("device", host.device)
            .put("soc_model", host.socModel)
            .put("board", host.board)
            .put("android_api", host.androidApi)
            .put("total_memory_mb", host.totalMemoryMb)
            .put("app_memory_class_mb", host.appMemoryClassMb)
            .put("cpu_core_count", host.cpuCoreCount)
            .put("primary_abi", host.primaryAbi))
        .put("resources", JSONObject()
            .put("available_memory_mb", resources.availableMemoryMb)
            .put("cpu_load_percent", resources.cpuLoadPercent)
            .put("thermal_level", resources.thermalLevel)
            .put("battery_percent", resources.batteryPercent)
            .put("charging", resources.isCharging)
            .put("available_storage_mb", resources.availableStorageMb)
            .put("process_pss_mb", processPssMb))
        .put("network", JSONObject()
            .put("active_transport", network.activeTransport)
            .put("internet_capability", network.internetCapability)
            .put("validated", network.validated)
            .put("metered", network.metered))
        .put("local_adaptation", JSONObject()
            .put("mode", plan.mode.name)
            .put("context_token_budget", plan.contextTokenBudget)
            .put("max_concurrent_expensive_tasks", plan.maxConcurrentExpensiveTasks)
            .put("allow_background_consolidation", plan.allowBackgroundConsolidation)
            .put("memory_reserve_mb", plan.memoryReserveMb)
            .put("reason", plan.reason))
        .put("host_study_samples", hostStudySamples)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Aggregate network facts only; no SSID, BSSID, IP or location is collected. */
data class HostNetworkState(
    val activeTransport: String,
    val internetCapability: Boolean,
    val validated: Boolean,
    val metered: Boolean?
)

/**
 * A cloud response is a proposal, not executable authority. The eventual
 * signed response parser must reject unknown schema versions, expired proposals,
 * host-fingerprint mismatches, unsupported capabilities and attempts to alter
 * permissions, identity, signing, update trust roots or protected data.
 */
