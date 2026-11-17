package com.jarvis.app.github

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDevelopmentClientTest {
    private val now = Instant.parse("2026-11-17T13:00:00Z")

    @Test
    fun `model-free self-study evidence is withheld without separate aggregate consent`() {
        val reflection = com.jarvis.app.selfstudy.ApplicationSelfStudy.Reflection(
            createdAtMs = 1L,
            sourceRevision = "revision-7",
            summary = "One structural gap observed.",
            findings = listOf("Unreachable component: body.voice"),
            nextActions = listOf("Inspect production wiring."),
            developmentRequest = "Investigate the unreachable body.voice component.",
            constraints = listOf("Inspect source; do not infer root cause."),
            evidenceGaps = listOf("Packaged metadata is not source comprehension.")
        )

        assertTrue(modelFreeReflectionEvidence(reflection, aggregateEvidenceEnabled = false) == null)
    }

    @Test
    fun `consented model-free reflection exports only its declared bounded fields`() {
        val reflection = com.jarvis.app.selfstudy.ApplicationSelfStudy.Reflection(
            createdAtMs = 1L,
            sourceRevision = "revision-7",
            summary = "One structural gap observed.",
            findings = listOf("Unreachable component: body.voice"),
            nextActions = listOf("Inspect production wiring."),
            developmentRequest = "Investigate the unreachable body.voice component.",
            constraints = listOf("Inspect source; do not infer root cause."),
            evidenceGaps = listOf("Packaged metadata is not source comprehension.")
        )

        val json = modelFreeReflectionEvidence(reflection, aggregateEvidenceEnabled = true)!!

        assertTrue(json.getString("development_request").contains("body.voice"))
        assertTrue(json.getString("privacy_scope").contains("structural_self_study"))
        assertFalse(json.has("hostFingerprint"))
        assertFalse(json.has("conversation_text"))
        assertFalse(json.has("remembered_fact"))
    }

    @Test
    fun `open proposal blocks first cycle without local history`() {
        assertFalse(shouldDispatchAutonomousCycle(
            hasOpenProposal = true,
            previousRequestAt = null,
            now = now
        ))
    }

    @Test
    fun `recent request remains inside cooldown`() {
        assertFalse(shouldDispatchAutonomousCycle(
            hasOpenProposal = false,
            previousRequestAt = now.minusSeconds(60 * 60),
            now = now
        ))
    }

    @Test
    fun `expired cooldown allows a new cycle`() {
        assertTrue(shouldDispatchAutonomousCycle(
            hasOpenProposal = false,
            previousRequestAt = now.minusSeconds(13 * 60 * 60),
            now = now
        ))
    }

    @Test
    fun `no history and no proposal allows first cycle`() {
        assertTrue(shouldDispatchAutonomousCycle(
            hasOpenProposal = false,
            previousRequestAt = null,
            now = now
        ))
    }
}
