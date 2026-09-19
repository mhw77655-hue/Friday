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

class MemoryUpdateEngineTest {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val consolidator = MemoryConsolidator(scope, config = MemoryConsolidator.Config(autoEvaluate = false))
    private val updateEngine = MemoryUpdateEngine(scope, consolidator)

    @Test
    fun `supersede memory creates new and marks old superseded`() = runBlocking {
        // First create a memory
        val exp = ExperienceRecord(
            experienceId = "exp_orig",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User prefers light mode"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        consolidator.submitExperience(exp)
        val evalResult = consolidator.evaluateCandidate(exp.experienceId)
        assertTrue(evalResult is MemoryConsolidator.EvaluationResult.PROMOTED)

        val promoted = evalResult as MemoryConsolidator.EvaluationResult.PROMOTED
        val oldMemory = consolidator.getMemory(promoted.memoryId)!!
        assertTrue(oldMemory.isCurrentTruth())

        // Now supersede it
        val result = updateEngine.supersedeMemory(
            oldMemoryId = oldMemory.memoryId,
            newContent = "User prefers dark mode",
            newConfidence = 0.95f,
            reason = "User explicitly changed preference",
            triggeringExperienceId = "exp_correction",
            source = "user_correction"
        )

        assertTrue(result is MemoryUpdateEngine.SupersessionResult.SUCCESS)

        val success = result as MemoryUpdateEngine.SupersessionResult.SUCCESS
        val superseded = success.superseded
        val superseding = success.superseding

        // Old memory should be superseded
        assertEquals(MemoryLifecycleState.SUPERSEDED, superseded.lifecycleState)
        assertEquals(superseding.memoryId, superseded.supersededBy)

        // New memory should be consolidated and reference old
        assertEquals(MemoryLifecycleState.CONSOLIDATED, superseding.lifecycleState)
        assertEquals(oldMemory.memoryId, superseding.supersedes)
        assertTrue(superseding.content.contains("dark mode"))

        // Both should exist in store
        assertNotNull(consolidator.getMemory(superseded.memoryId))
        assertNotNull(consolidator.getMemory(superseding.memoryId))

        // Current truths should only have the new one
        val truths = consolidator.getCurrentTruths().filter { it.content.contains("mode") }
        assertEquals(1, truths.size)
        assertTrue(truths.first().content.contains("dark mode"))
    }

    @Test
    fun `supersede non-existent memory fails`() = runBlocking {
        val result = updateEngine.supersedeMemory(
            oldMemoryId = "nonexistent",
            newContent = "New content",
            newConfidence = 0.9f,
            reason = "Test",
            triggeringExperienceId = "exp_test"
        )

        assertTrue(result is MemoryUpdateEngine.SupersessionResult.NOT_FOUND)
    }

    @Test
    fun `supersede non-current-truth fails`() = runBlocking {
        // Create and supersede once
        val exp = ExperienceRecord(
            experienceId = "exp_orig2",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Original"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp)
        val evalResult = consolidator.evaluateCandidate(exp.experienceId)
        val promoted = evalResult as MemoryConsolidator.EvaluationResult.PROMOTED
        val memory = consolidator.getMemory(promoted.memoryId)!!

        // Supersede it
        updateEngine.supersedeMemory(
            oldMemoryId = memory.memoryId,
            newContent = "Superseded once",
            newConfidence = 0.9f,
            reason = "First supersession",
            triggeringExperienceId = "exp_1"
        )

        // Try to supersede again (already superseded)
        val result = updateEngine.supersedeMemory(
            oldMemoryId = memory.memoryId,
            newContent = "Superseded twice",
            newConfidence = 0.9f,
            reason = "Second supersession",
            triggeringExperienceId = "exp_2"
        )

        assertTrue(result is MemoryUpdateEngine.SupersessionResult.NOT_CURRENT_TRUTH)
    }

    @Test
    fun `update memory content`() = runBlocking {
        val exp = ExperienceRecord(
            experienceId = "exp_update",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Original content"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.8f,
            importance = 1.0f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp)
        val evalResult = consolidator.evaluateCandidate(exp.experienceId)
        val promoted = evalResult as MemoryConsolidator.EvaluationResult.PROMOTED
        val memory = consolidator.getMemory(promoted.memoryId)!!

        // Update content
        val result = updateEngine.updateMemory(
            memoryId = memory.memoryId,
            newContent = "Updated content",
            reason = "Correction",
            triggeringExperienceId = "exp_correction"
        )

        assertTrue(result is MemoryUpdateEngine.UpdateResult.SUCCESS)

        val updated = (result as MemoryUpdateEngine.UpdateResult.SUCCESS).updated
        assertEquals("Updated content", updated.content)
        assertTrue(updated.metadata["supersessionReason"] == null) // Not a supersession
        assertEquals("Correction", updated.metadata["reason"])
    }

    @Test
    fun `reinforce memory increases confidence`() = runBlocking {
        val exp = ExperienceRecord(
            experienceId = "exp_reinforce",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Test fact"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.7f,
            importance = 1.0f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp)
        val evalResult = consolidator.evaluateCandidate(exp.experienceId)
        val promoted = evalResult as MemoryConsolidator.EvaluationResult.PROMOTED
        val memory = consolidator.getMemory(promoted.memoryId)!!

        val originalConfidence = memory.confidence

        // Reinforce
        val result = updateEngine.reinforceMemory(memory.memoryId, "exp_recurrence", 0.1f)

        assertTrue(result is MemoryUpdateEngine.ReinforceResult.SUCCESS)

        val reinforced = (result as MemoryUpdateEngine.ReinforceResult.SUCCESS).reinforced
        assertEquals(originalConfidence + 0.1f, reinforced.confidence, 0.001f)
        assertTrue(reinforced.provenance.derivationHistory.any { it.stepType == DerivationStepType.REINFORCEMENT })
    }

    @Test
    fun `decay memory reduces confidence with floor`() = runBlocking {
        val exp = ExperienceRecord(
            experienceId = "exp_decay",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Test fact"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.5f,
            importance = 1.0f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp)
        val evalResult = consolidator.evaluateCandidate(exp.experienceId)
        val promoted = evalResult as MemoryConsolidator.EvaluationResult.PROMOTED
        val memory = consolidator.getMemory(promoted.memoryId)!!

        // Decay
        val result = updateEngine.decayMemory(memory.memoryId, 0.1f)

        assertTrue(result is MemoryUpdateEngine.DecayResult.SUCCESS)

        val decayed = (result as MemoryUpdateEngine.DecayResult.SUCCESS).decayed
        assertEquals(0.4f, decayed.confidence, 0.001f) // 0.5 - 0.1
        assertTrue(decayed.provenance.derivationHistory.any { it.stepType == DerivationStepType.DECAY })

        // Decay below floor - should stop at 0.3 for current truths
        for (i in 1..5) {
            updateEngine.decayMemory(memory.memoryId, 0.1f)
        }

        val final = consolidator.getMemory(memory.memoryId)!!
        assertTrue(final.confidence >= 0.3f) // Floor for current truths
    }

    @Test
    fun `create temporal relationship`() = runBlocking {
        // Create two memories
        val exp1 = ExperienceRecord(
            experienceId = "exp_rel_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "First"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.FACT
        )

        val exp2 = ExperienceRecord(
            experienceId = "exp_rel_2",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Second"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp1)
        consolidator.submitExperience(exp2)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        val eval2 = consolidator.evaluateCandidate(exp2.experienceId)

        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!
        val mem2 = consolidator.getMemory((eval2 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        // Create BEFORE relationship
        val result = updateEngine.createTemporalRelationship(
            fromMemoryId = mem1.memoryId,
            toMemoryId = mem2.memoryId,
            relationshipType = TemporalRelationshipType.BEFORE,
            confidence = 0.95f,
            evidence = listOf("Sequential user statements")
        )

        assertTrue(result is MemoryUpdateEngine.TemporalRelationshipResult.SUCCESS)

        val rel = (result as MemoryUpdateEngine.TemporalRelationshipResult.SUCCESS).relationship
        assertEquals(TemporalRelationshipType.BEFORE, rel.relationshipType)

        // Both memories should have the relationship
        val updated1 = consolidator.getMemory(mem1.memoryId)!!
        val updated2 = consolidator.getMemory(mem2.memoryId)!!

        assertTrue(updated1.temporalRelationships.any { it.relationshipType == TemporalRelationshipType.BEFORE })
        assertTrue(updated2.temporalRelationships.any { it.relationshipType == TemporalRelationshipType.AFTER })
    }
}