package com.jarvis.app.github

import com.jarvis.app.JarvisEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * A content-free view of Friday's own installed/runtime condition.
 *
 * This is deliberately limited to aggregate counts, status names, capability
 * keys and resource trends. It never serializes identity ids, remembered facts,
 * experience feature names, prompts, transcripts, files or device identifiers.
 */
internal data class DevelopmentEvidenceSnapshot(
    val observedAtMs: Long,
    val runtimeReadiness: String,
    val identityRecordPresent: Boolean,
    val experienceCount: Int?,
    val associationCount: Int?,
    val planStatus: String?,
    val planStepStatusCounts: Map<String, Int>,
    val capabilityStage: Int?,
    val availableCapabilityKeys: List<String>,
    val missingCapabilityKeys: List<String>,
    val symbioticMode: String?,
    val symbioticContextTokenBudget: Int?,
    val symbioticMaxConcurrentExpensiveTasks: Int?,
    val symbioticMemoryReserveMb: Long?,
    val selfStudy: SelfStudyEvidence?
) {
    data class SelfStudyEvidence(
        val sourceRevision: String,
        val sourceFileCount: Int,
        val sourceSymbolCount: Int,
        val sourcePackageCount: Int,
        val sourceDependencyEdgeCount: Int,
        val graphNodeCount: Int,
        val graphEdgeCount: Int,
        val reachableNodeCount: Int,
        val unreachableNodeCount: Int,
        val missingInvariantCount: Int,
        val declaredMissingCapabilityKeys: List<String>,
        val knownLimitationKeys: List<String>,
        val hostSamples: Long,
        val minimumAvailableMemoryMb: Long,
        val averageCpuLoadEmaPercent: Double,
        val thermalPressureSamples: Long,
        val lowMemorySamples: Long,
        val additionalMemoryReserveMb: Long,
        val maximumObservedProcessPssMb: Long,
        val minimumBatteryPercent: Int,
        val liquidGeneration: Long,
        val liquidPredictionError: Double,
        val organStrategyKeys: List<String>,
        val organFailureCounts: Map<String, Long>,
        val nextFocus: List<String>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("source_revision", sourceRevision.take(80))
            .put("source_file_count", sourceFileCount)
            .put("source_symbol_count", sourceSymbolCount)
            .put("source_package_count", sourcePackageCount)
            .put("source_dependency_edge_count", sourceDependencyEdgeCount)
            .put("graph_node_count", graphNodeCount)
            .put("graph_edge_count", graphEdgeCount)
            .put("reachable_node_count", reachableNodeCount)
            .put("unreachable_node_count", unreachableNodeCount)
            .put("missing_invariant_count", missingInvariantCount)
            .put("declared_missing_capability_keys", stringArray(declaredMissingCapabilityKeys))
            .put("known_limitation_keys", stringArray(knownLimitationKeys))
            .put("host_samples", hostSamples)
            .put("minimum_available_memory_mb", minimumAvailableMemoryMb)
            .put("average_cpu_load_ema_percent", averageCpuLoadEmaPercent)
            .put("thermal_pressure_samples", thermalPressureSamples)
            .put("low_memory_samples", lowMemorySamples)
            .put("additional_memory_reserve_mb", additionalMemoryReserveMb)
            .put("maximum_observed_process_pss_mb", maximumObservedProcessPssMb)
            .put("minimum_battery_percent", minimumBatteryPercent)
            .put("liquid_generation", liquidGeneration)
            .put("liquid_prediction_error", liquidPredictionError)
            .put("organ_strategy_keys", stringArray(organStrategyKeys))
            .put("organ_failure_counts", longMap(organFailureCounts))
            .put("next_focus", stringArray(nextFocus.map { it.take(160) }))
    }

    fun toJson(): JSONObject = JSONObject()
        .put("schema_version", 1)
        .put("observed_at_ms", observedAtMs)
        .put("privacy_scope", "aggregate_only_no_content")
        .put("runtime_readiness", runtimeReadiness.take(48))
        .put("identity_record_present", identityRecordPresent)
        .put("experience_count", experienceCount)
        .put("association_count", associationCount)
        .put("plan_status", planStatus)
        .put("plan_step_status_counts", intMap(planStepStatusCounts))
        .put("capability_stage", capabilityStage)
        .put("available_capability_keys", stringArray(availableCapabilityKeys))
        .put("missing_capability_keys", stringArray(missingCapabilityKeys))
        .put("symbiotic_mode", symbioticMode)
        .put("symbiotic_context_token_budget", symbioticContextTokenBudget)
        .put("symbiotic_max_concurrent_expensive_tasks", symbioticMaxConcurrentExpensiveTasks)
        .put("symbiotic_memory_reserve_mb", symbioticMemoryReserveMb)
        .put("self_study", selfStudy?.toJson())

    fun toJsonString(): String = toJson().toString()
}

private fun stringArray(values: List<String>) = JSONArray().apply { values.forEach { put(it.take(160)) } }
private fun intMap(values: Map<String, Int>) = JSONObject().apply {
    values.toSortedMap().forEach { (key, value) -> put(key.take(48), value) }
}
private fun longMap(values: Map<String, Long>) = JSONObject().apply {
    values.toSortedMap().forEach { (key, value) -> put(key.take(48), value) }
}

/** Reads only safe aggregates from Friday's already-wired product composition. */
internal object DevelopmentEvidenceCollector {
    fun collect(nowMs: Long = System.currentTimeMillis()): DevelopmentEvidenceSnapshot {
        val individual = runCatching { JarvisEngine.fridayIndividual }.getOrNull()
        val cognitive = runCatching { JarvisEngine.cognitiveEngine }.getOrNull()
        val plan = runCatching { cognitive?.getCurrentPlanGraph() }.getOrNull()
        val capabilities = runCatching { JarvisEngine.capabilities }.getOrNull()
        val symbioticPlan = runCatching { JarvisEngine.nanoSymbiosis?.plan?.value }.getOrNull()
        val report = runCatching {
            val selfStudy = JarvisEngine.applicationSelfStudy
            selfStudy?.latestReport() ?: selfStudy?.study(nowMs)
        }.getOrNull()

        val selfStudy = report?.let {
            DevelopmentEvidenceSnapshot.SelfStudyEvidence(
                sourceRevision = it.sourceRevision,
                sourceFileCount = it.sourceFileCount,
                sourceSymbolCount = it.sourceSymbolCount,
                sourcePackageCount = it.sourcePackageCount,
                sourceDependencyEdgeCount = it.sourceDependencyEdgeCount,
                graphNodeCount = it.nodeCount,
                graphEdgeCount = it.edgeCount,
                reachableNodeCount = it.reachableCount,
                unreachableNodeCount = it.unreachableNodeIds.size,
                missingInvariantCount = it.missingInvariantIds.size,
                declaredMissingCapabilityKeys = it.declaredMissingCapabilities.take(20),
                knownLimitationKeys = it.knownLimitationIds.take(20),
                hostSamples = it.hostSamples,
                minimumAvailableMemoryMb = it.minimumAvailableMemoryMb,
                averageCpuLoadEmaPercent = it.averageCpuLoadEmaPercent,
                thermalPressureSamples = it.thermalPressureSamples,
                lowMemorySamples = it.lowMemorySamples,
                additionalMemoryReserveMb = it.additionalMemoryReserveMb,
                maximumObservedProcessPssMb = it.maximumObservedProcessPssMb,
                minimumBatteryPercent = it.minimumBatteryPercent,
                liquidGeneration = it.liquidGeneration,
                liquidPredictionError = it.liquidPredictionError,
                organStrategyKeys = it.organStrategies.keys.sorted().take(24),
                organFailureCounts = it.organFailureCounts.toSortedMap().entries.take(24).associate { entry ->
                    entry.key.take(48) to entry.value
                },
                nextFocus = it.nextFocus.take(8).map { focus -> focus.take(160) }
            )
        }

        val planCounts = plan?.nodes?.groupingBy { node -> node.status.name }?.eachCount().orEmpty()
        return DevelopmentEvidenceSnapshot(
            observedAtMs = nowMs,
            runtimeReadiness = runCatching { JarvisEngine.currentReadiness.javaClass.simpleName }
                .getOrDefault("unknown"),
            identityRecordPresent = individual != null,
            experienceCount = runCatching { individual?.experience?.experienceCount() }.getOrNull(),
            associationCount = runCatching { individual?.experience?.associationCount() }.getOrNull(),
            planStatus = plan?.status?.name,
            planStepStatusCounts = planCounts,
            capabilityStage = capabilities?.stage,
            availableCapabilityKeys = capabilities?.capabilities?.filterValues { it }?.keys?.sorted()?.take(32).orEmpty(),
            missingCapabilityKeys = capabilities?.capabilities?.filterValues { !it }?.keys?.sorted()?.take(32).orEmpty(),
            symbioticMode = symbioticPlan?.mode?.name,
            symbioticContextTokenBudget = symbioticPlan?.contextTokenBudget,
            symbioticMaxConcurrentExpensiveTasks = symbioticPlan?.maxConcurrentExpensiveTasks,
            symbioticMemoryReserveMb = symbioticPlan?.memoryReserveMb,
            selfStudy = selfStudy
        )
    }
}
