package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class MemoryConflictResolverTest {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val consolidator = MemoryConsolidator(scope, config = MemoryConsolidator.Config(autoEvaluate = false))
    private val updateEngine = MemoryUpdateEngine(scope, consolidator)
    private val conflictResolver = MemoryConflictResolver(updateEngine)

    @Test
    fun `resolve conflict by highest confidence`() = runBlocking {
        // Create two conflicting memories with different confidence
        val exp1 = ExperienceRecord(
            experienceId = "exp_conf_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User name is Alice"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.FACT
        )

        val exp2 = ExperienceRecord(
            experienceId = "exp_conf_2",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User name is Bob"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.6f, // Lower confidence
            importance = 1.0f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp1)
        consolidator.submitExperience(exp2)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        val eval2 = consolidator.evaluateCandidate(exp2.experienceId)

        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!
        val mem2 = consolidator.getMemory((eval2 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        // Create conflict manually
        val conflict = MemoryConflict(
            memoryAId = mem1.memoryId,
            memoryBId = mem2.memoryId,
            memoryAContent = mem1.content,
            memoryBContent = mem2.content,
            conflictType = ConflictType.FACT_CONTRADICTION,
            evidenceA = listOf("Direct statement during interaction"),
            evidenceB = listOf("Observed in conversation"),
            timestampA = mem1.createdAt,
            timestampB = mem2.createdAt,
            confidenceA = mem1.confidence,
            confidenceB = mem2.confidence,
            sourceReliabilityA = 0.9f,
            sourceReliabilityB = 0.6f
        )

        val result = conflictResolver.resolve(conflict)

        assertTrue(result is MemoryConflictResolver.ConflictResolutionResult.RESOLVED)
        val resolved = result as MemoryConflictResolver.ConflictResolutionResult.RESOLVED
        assertEquals(ResolutionMethod.HIGHEST_CONFIDENCE, resolved.resolution.resolutionMethod)
        assertEquals(mem1.memoryId, resolved.resolution.chosenMemoryId)
        assertEquals(mem2.memoryId, resolved.resolution.rejectedMemoryId)
    }

    @Test
    fun `resolve conflict by recency`() = runBlocking {
        // Create memories with similar confidence but different timestamps
        val exp1 = ExperienceRecord(
            experienceId = "exp_conf_rec_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Old preference"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.8f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        // createdAt/memoryId are stamped at CONSOLIDATION time
        // (promoteToConsolidated), NOT at submit time, so the strictly-increasing
        // timestamps the recency resolution depends on must be guaranteed between
        // the EVALUATIONS — a delay between the two submits collapses to the same
        // millisecond under load (equal createdAt = arbitrary winner).
        val exp2 = ExperienceRecord(
            experienceId = "exp_conf_rec_2",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "New preference"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.8f, // Same confidence
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        consolidator.submitExperience(exp1)
        consolidator.submitExperience(exp2)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        Thread.sleep(20)
        val eval2 = consolidator.evaluateCandidate(exp2.experienceId)

        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!
        val mem2 = consolidator.getMemory((eval2 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        val conflict = MemoryConflict(
            memoryAId = mem1.memoryId,
            memoryBId = mem2.memoryId,
            memoryAContent = mem1.content,
            memoryBContent = mem2.content,
            conflictType = ConflictType.CONTENT_CONTRADICTION,
            evidenceA = listOf("Older statement"),
            evidenceB = listOf("Newer statement"),
            timestampA = mem1.createdAt,
            timestampB = mem2.createdAt,
            confidenceA = mem1.confidence,
            confidenceB = mem2.confidence,
            sourceReliabilityA = 0.8f,
            sourceReliabilityB = 0.8f
        )

        val result = conflictResolver.resolve(conflict)

        assertTrue(result is MemoryConflictResolver.ConflictResolutionResult.RESOLVED)
        val resolved = result as MemoryConflictResolver.ConflictResolutionResult.RESOLVED
        // Should use LAST_WRITER_WINS for content contradiction with same confidence
        assertEquals(ResolutionMethod.LAST_WRITER_WINS, resolved.resolution.resolutionMethod)
        assertEquals(mem2.memoryId, resolved.resolution.chosenMemoryId) // Newer wins
    }

    @Test
    fun `resolve conflict by user instruction`() = runBlocking {
        val exp1 = ExperienceRecord(
            experienceId = "exp_conf_user_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User says X"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.7f,
            importance = 1.0f,
            candidateMemoryType = MemoryType.FACT
        )

        val exp2 = ExperienceRecord(
            experienceId = "exp_conf_user_2",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User says Y"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.7f,
            importance = 1.0f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp1)
        consolidator.submitExperience(exp2)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        val eval2 = consolidator.evaluateCandidate(exp2.experienceId)

        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!
        val mem2 = consolidator.getMemory((eval2 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        val conflict = MemoryConflict(
            memoryAId = mem1.memoryId,
            memoryBId = mem2.memoryId,
            memoryAContent = mem1.content,
            memoryBContent = mem2.content,
            conflictType = ConflictType.FACT_CONTRADICTION,
            evidenceA = listOf("User explicitly said X"),
            evidenceB = listOf("Observed"),
            timestampA = mem1.createdAt,
            timestampB = mem2.createdAt,
            confidenceA = mem1.confidence,
            confidenceB = mem2.confidence,
            sourceReliabilityA = 0.9f,
            sourceReliabilityB = 0.7f
        )

        val result = conflictResolver.resolve(conflict)

        assertTrue(result is MemoryConflictResolver.ConflictResolutionResult.RESOLVED)
        val resolved = result as MemoryConflictResolver.ConflictResolutionResult.RESOLVED
        assertEquals(ResolutionMethod.USER_INSTRUCTION, resolved.resolution.resolutionMethod)
        assertEquals(mem1.memoryId, resolved.resolution.chosenMemoryId)
    }

    @Test
    fun `defer preference conflict`() = runBlocking {
        val exp1 = ExperienceRecord(
            experienceId = "exp_conf_pref_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Prefers A"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.8f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        val exp2 = ExperienceRecord(
            experienceId = "exp_conf_pref_2",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Prefers B"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.8f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        consolidator.submitExperience(exp1)
        consolidator.submitExperience(exp2)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        val eval2 = consolidator.evaluateCandidate(exp2.experienceId)

        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!
        val mem2 = consolidator.getMemory((eval2 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        val conflict = MemoryConflict(
            memoryAId = mem1.memoryId,
            memoryBId = mem2.memoryId,
            memoryAContent = mem1.content,
            memoryBContent = mem2.content,
            conflictType = ConflictType.PREFERENCE_CONTRADICTION,
            evidenceA = listOf("Said prefers A"),
            evidenceB = listOf("Said prefers B"),
            timestampA = mem1.createdAt,
            timestampB = mem2.createdAt,
            confidenceA = mem1.confidence,
            confidenceB = mem2.confidence,
            sourceReliabilityA = 0.8f,
            sourceReliabilityB = 0.8f
        )

        val result = conflictResolver.resolve(conflict)

        // Should be deferred for preferences
        assertTrue(result is MemoryConflictResolver.ConflictResolutionResult.DEFERRED)
    }

    @Test
    fun `already resolved conflict returns existing resolution`() = runBlocking {
        val exp1 = ExperienceRecord(
            experienceId = "exp_conf_resolved_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Test"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.FACT
        )

        val exp2 = ExperienceRecord(
            experienceId = "exp_conf_resolved_2",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Test observed fact"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.8f,
            importance = 1.0f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp1)
        consolidator.submitExperience(exp2)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        val eval2 = consolidator.evaluateCandidate(exp2.experienceId)

        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!
        val mem2 = consolidator.getMemory((eval2 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        val conflict = MemoryConflict(
            memoryAId = mem1.memoryId,
            memoryBId = mem2.memoryId,
            memoryAContent = mem1.content,
            memoryBContent = mem2.content,
            conflictType = ConflictType.FACT_CONTRADICTION,
            evidenceA = listOf("Direct"),
            evidenceB = listOf("Observed"),
            timestampA = mem1.createdAt,
            timestampB = mem2.createdAt,
            confidenceA = mem1.confidence,
            confidenceB = mem2.confidence,
            sourceReliabilityA = 0.9f,
            sourceReliabilityB = 0.5f,
            resolutionState = ConflictResolutionState.RESOLVED,
            resolution = ConflictResolution(
                resolutionMethod = ResolutionMethod.HIGHEST_CONFIDENCE,
                chosenMemoryId = mem1.memoryId,
                rejectedMemoryId = mem2.memoryId,
                reasoning = "Already resolved",
                resolutionConfidence = 0.9f,
                resolvedBy = "test"
            )
        )

        val result = conflictResolver.resolve(conflict)

        assertTrue(result is MemoryConflictResolver.ConflictResolutionResult.ALREADY_RESOLVED)
    }
}