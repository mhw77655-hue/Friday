package com.jarvis.app.github

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DevelopmentEvidenceSnapshotTest {
    @Test
    fun `serializes aggregate plan outcomes including skipped and blocked work`() {
        val snapshot = DevelopmentEvidenceSnapshot(
            observedAtMs = 1234L,
            runtimeReadiness = "Ready",
            identityRecordPresent = true,
            experienceCount = 12,
            associationCount = 29,
            planStatus = "PARTIAL",
            planStepStatusCounts = mapOf(
                "COMPLETED" to 2,
                "SKIPPED" to 1,
                "BLOCKED" to 3
            ),
            capabilityStage = 2,
            availableCapabilityKeys = listOf("VOICE_OUTPUT", "LOCAL_MEMORY"),
            missingCapabilityKeys = listOf("SEMANTIC_VIDEO"),
            symbioticMode = "BALANCED",
            symbioticContextTokenBudget = 2048,
            symbioticMaxConcurrentExpensiveTasks = 1,
            symbioticMemoryReserveMb = 256L,
            selfStudy = null
        )

        val json = JSONObject(snapshot.toJsonString())

        assertEquals(12, json.getInt("experience_count"))
        assertEquals(29, json.getInt("association_count"))
        assertEquals("PARTIAL", json.getString("plan_status"))
        val outcomes = json.getJSONObject("plan_step_status_counts")
        assertEquals(1, outcomes.getInt("SKIPPED"))
        assertEquals(3, outcomes.getInt("BLOCKED"))
        assertEquals("aggregate_only_no_content", json.getString("privacy_scope"))
    }

    @Test
    fun `snapshot schema has no fields for private memory content or identity identifiers`() {
        val json = JSONObject(
            DevelopmentEvidenceSnapshot(
                observedAtMs = 1L,
                runtimeReadiness = "Ready",
                identityRecordPresent = true,
                experienceCount = 1,
                associationCount = 1,
                planStatus = null,
                planStepStatusCounts = emptyMap(),
                capabilityStage = null,
                availableCapabilityKeys = emptyList(),
                missingCapabilityKeys = emptyList(),
                symbioticMode = null,
                symbioticContextTokenBudget = null,
                symbioticMaxConcurrentExpensiveTasks = null,
                symbioticMemoryReserveMb = null,
                selfStudy = null
            ).toJsonString()
        )

        assertFalse(json.has("identity_id"))
        assertFalse(json.has("remembered_fact"))
        assertFalse(json.has("conversation_text"))
        assertFalse(json.has("feature_names"))
    }

    @Test
    fun `self study evidence exports only aggregate next focus count`() {
        val json = JSONObject(
            DevelopmentEvidenceSnapshot(
                observedAtMs = 1234L,
                runtimeReadiness = "Ready",
                identityRecordPresent = true,
                experienceCount = 1,
                associationCount = 2,
                planStatus = null,
                planStepStatusCounts = emptyMap(),
                capabilityStage = null,
                availableCapabilityKeys = emptyList(),
                missingCapabilityKeys = emptyList(),
                symbioticMode = null,
                symbioticContextTokenBudget = null,
                symbioticMaxConcurrentExpensiveTasks = null,
                symbioticMemoryReserveMb = null,
                selfStudy = DevelopmentEvidenceSnapshot.SelfStudyEvidence(
                    sourceRevision = "abc123",
                    sourceFileCount = 1,
                    sourceSymbolCount = 2,
                    sourcePackageCount = 1,
                    sourceDependencyEdgeCount = 0,
                    graphNodeCount = 1,
                    graphEdgeCount = 0,
                    reachableNodeCount = 1,
                    unreachableNodeCount = 0,
                    missingInvariantCount = 0,
                    declaredMissingCapabilityKeys = emptyList(),
                    knownLimitationKeys = emptyList(),
                    hostSamples = 1,
                    minimumAvailableMemoryMb = 100,
                    averageCpuLoadEmaPercent = 1.0,
                    thermalPressureSamples = 0,
                    lowMemorySamples = 0,
                    additionalMemoryReserveMb = 100,
                    maximumObservedProcessPssMb = 100,
                    minimumBatteryPercent = 80,
                    liquidGeneration = 1,
                    liquidPredictionError = 0.0,
                    organStrategyKeys = emptyList(),
                    organFailureCounts = emptyMap(),
                    nextFocusCount = 3
                )
            ).toJsonString()
        )
        val selfStudy = json.getJSONObject("self_study")

        assertEquals(3, selfStudy.getInt("next_focus_count"))
        assertFalse(selfStudy.has("next_focus"))
        assertFalse(json.toString().contains("internal-node-id"))
    }
}
