package com.jarvis.app.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        val json = snapshot.toJsonString()

        assertTrue(json.contains("experience_count" + ":12"))
        assertTrue(json.contains("association_count" + ":29"))
        assertTrue(json.contains("PARTIAL"))
        assertTrue(json.contains("SKIPPED" + ":1"))
        assertTrue(json.contains("BLOCKED" + ":3"))
        assertTrue(json.contains("aggregate_only_no_content"))
    }

    @Test
    fun `snapshot schema has no fields for private memory content or identity identifiers`() {
        val snapshot = DevelopmentEvidenceSnapshot(
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
        )

        val json = snapshot.toJsonString()

        assertFalse(json.contains("identity_id"))
        assertFalse(json.contains("remembered_fact"))
        assertFalse(json.contains("conversation_text"))
        assertFalse(json.contains("feature_names"))
    }

    @Test
    fun `self study evidence exports only aggregate next focus count`() {
        val snapshot = DevelopmentEvidenceSnapshot(
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
        )

        val json = snapshot.toJsonString()

        assertTrue(json.contains("next_focus_count" + ":3"))
        // The legacy free-text field must not be exported; next_focus_count is allowed.
        assertFalse(json.contains("next_focus" + ":"))
        assertFalse(json.contains("internal-node-id"))
    }
}
