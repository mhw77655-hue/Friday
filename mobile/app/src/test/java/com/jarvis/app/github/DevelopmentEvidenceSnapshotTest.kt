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

        assertTrue(json.contains("\"experience_count\":12"))
        assertTrue(json.contains("\"association_count\":29"))
        assertTrue(json.contains("\"PARTIAL\""))
        assertTrue(json.contains("\"SKIPPED\":1"))
        assertTrue(json.contains("\"BLOCKED\":3"))
        assertTrue(json.contains("\"privacy_scope\":\"aggregate_only_no_content\""))
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
}
