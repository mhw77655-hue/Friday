package com.jarvis.app.cognitive.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class MemoryProvenanceTest {

    @Test
    fun `provenance creation and derivation chain`() = runBlocking {
        val provenance = MemoryProvenance(
            originatingExperienceId = "exp_1",
            experienceSource = "USER_INTERACTION",
            experienceTimestamp = 1000L,
            experienceConfidence = 0.9f,
            currentConfidence = 0.9f,
            sourceReliability = 0.95f
        )

        // Add derivation steps
        val withStep1 = provenance.withDerivation(DerivationStep(
            stepType = DerivationStepType.INITIAL_CONSOLIDATION,
            description = "Initial consolidation",
            confidenceBefore = 0.9f,
            confidenceAfter = 0.9f,
            source = "consolidator"
        ))

        val withStep2 = withStep1.withDerivation(DerivationStep(
            stepType = DerivationStepType.REINFORCEMENT,
            description = "Reinforced by recurrence",
            confidenceBefore = 0.9f,
            confidenceAfter = 0.95f,
            source = "recurrence"
        ))

        val verified = withStep2.withVerification("user_confirmation")

        assertEquals(2, verified.derivationHistory.size)
        assertEquals(DerivationStepType.INITIAL_CONSOLIDATION, verified.derivationHistory[0].stepType)
        assertEquals(DerivationStepType.REINFORCEMENT, verified.derivationHistory[1].stepType)
        assertTrue(verified.verified)
        assertNotNull(verified.verificationMethod)
        assertEquals("user_confirmation", verified.verificationMethod)
    }

    @Test
    fun `provenance confidence bounds`() = runBlocking {
        val provenance = MemoryProvenance(
            originatingExperienceId = "exp_1",
            experienceSource = "USER_INTERACTION",
            experienceTimestamp = 1000L,
            experienceConfidence = 0.9f,
            currentConfidence = 0.9f
        )

        val high = provenance.withConfidence(1.5f)
        assertEquals(1.0f, high.currentConfidence)

        val low = provenance.withConfidence(-0.5f)
        assertEquals(0.0f, low.currentConfidence)
    }

    @Test
    fun `provenance string representation`() = runBlocking {
        val provenance = MemoryProvenance(
            originatingExperienceId = "exp_1",
            experienceSource = "USER_INTERACTION",
            experienceTimestamp = 1000L,
            experienceConfidence = 0.9f,
            currentConfidence = 0.85f,
            sourceReliability = 0.9f,
            verified = true,
            verifiedAt = 2000L,
            verificationMethod = "user_confirmation"
        ).withDerivation(DerivationStep(
            stepType = DerivationStepType.INITIAL_CONSOLIDATION,
            description = "Initial consolidation",
            confidenceBefore = 0.9f,
            confidenceAfter = 0.85f
        ))

        val str = provenance.toProvenanceString()
        assertTrue(str.contains("exp_1"))
        assertTrue(str.contains("USER_INTERACTION"))
        assertTrue(str.contains("0.90"))
        assertTrue(str.contains("0.85"))
        assertTrue(str.contains("INITIAL_CONSOLIDATION"))
        assertTrue(str.contains("Verified"))
    }
}

class TemporalRelationshipTest {

    @Test
    fun `temporal relationship creation`() = runBlocking {
        val rel = TemporalRelationship(
            fromMemoryId = "mem_A",
            toMemoryId = "mem_B",
            relationshipType = TemporalRelationshipType.SUPERSEDES,
            confidence = 0.9f,
            evidence = listOf("User corrected preference"),
            triggeringExperienceId = "exp_123"
        )

        assertEquals("mem_A", rel.fromMemoryId)
        assertEquals("mem_B", rel.toMemoryId)
        assertEquals(TemporalRelationshipType.SUPERSEDES, rel.relationshipType)
        assertEquals(0.9f, rel.confidence)
        assertEquals(1, rel.evidence.size)
        assertEquals("exp_123", rel.triggeringExperienceId)
    }

    @Test
    fun `temporal relationship inverse`() = runBlocking {
        val rel = TemporalRelationship(
            fromMemoryId = "mem_A",
            toMemoryId = "mem_B",
            relationshipType = TemporalRelationshipType.BEFORE
        )

        val inverse = rel.inverse()

        assertEquals("mem_B", inverse.fromMemoryId)
        assertEquals("mem_A", inverse.toMemoryId)
        assertEquals(TemporalRelationshipType.AFTER, inverse.relationshipType)
        assertEquals(rel.confidence, inverse.confidence)
    }

    @Test
    fun `all inverse pairs correct`() = runBlocking {
        val pairs = mapOf(
            TemporalRelationshipType.BEFORE to TemporalRelationshipType.AFTER,
            TemporalRelationshipType.AFTER to TemporalRelationshipType.BEFORE,
            TemporalRelationshipType.DURING to TemporalRelationshipType.DURING,
            // CHANGED_TO has no distinct inverse type in the current taxonomy —
            // kept as a self-inverse so the mapping stays a bijection.
            TemporalRelationshipType.CHANGED_TO to TemporalRelationshipType.CHANGED_TO,
            TemporalRelationshipType.SUPERSEDED_BY to TemporalRelationshipType.SUPERSEDES,
            TemporalRelationshipType.SUPERSEDES to TemporalRelationshipType.SUPERSEDED_BY,
            TemporalRelationshipType.DERIVED_FROM to TemporalRelationshipType.SOURCE_OF,
            TemporalRelationshipType.SOURCE_OF to TemporalRelationshipType.DERIVED_FROM,
            TemporalRelationshipType.CONCURRENT to TemporalRelationshipType.CONCURRENT,
            TemporalRelationshipType.CAUSED to TemporalRelationshipType.CAUSED_BY,
            TemporalRelationshipType.CAUSED_BY to TemporalRelationshipType.CAUSED,
            TemporalRelationshipType.CONTRADICTS to TemporalRelationshipType.CONTRADICTED_BY,
            TemporalRelationshipType.CONTRADICTED_BY to TemporalRelationshipType.CONTRADICTS
        )

        for ((original, expectedInverse) in pairs) {
            val rel = TemporalRelationship(
                fromMemoryId = "A",
                toMemoryId = "B",
                relationshipType = original
            )
            val inverse = rel.inverse()
            assertEquals("Failed for $original", expectedInverse, inverse.relationshipType)
        }
    }
}