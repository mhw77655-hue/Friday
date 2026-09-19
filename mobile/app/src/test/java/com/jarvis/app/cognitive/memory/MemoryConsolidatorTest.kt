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

class MemoryConsolidatorTest {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val consolidator = MemoryConsolidator(scope, config = MemoryConsolidator.Config(autoEvaluate = false))

    @Test
    fun `submit experience and evaluate`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "exp_test_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(
                actionType = ActionType.COMMUNICATE,
                description = "User prefers dark mode"
            ),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        val submitResult = consolidator.submitExperience(experience)
        assertTrue(submitResult is MemoryConsolidator.SubmitResult.ACCEPTED)

        // Evaluate
        val evalResult = consolidator.evaluateCandidate(experience.experienceId)
        assertTrue(evalResult is MemoryConsolidator.EvaluationResult.PROMOTED)

        val promoted = evalResult as MemoryConsolidator.EvaluationResult.PROMOTED
        val memory = consolidator.getMemory(promoted.memoryId)
        assertNotNull(memory)
        assertEquals(MemoryType.PREFERENCE, memory?.memoryType)
        assertTrue(memory?.content?.contains("dark mode") == true)
    }

    @Test
    fun `reject low importance experience`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "exp_test_2",
            source = ExperienceSource.OBSERVATION,
            action = ExperienceAction(
                actionType = ActionType.OBSERVE,
                description = "Random background noise"
            ),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.3f,
            importance = 0.1f,
            candidateMemoryType = MemoryType.CONTEXT
        )

        val submitResult = consolidator.submitExperience(experience)
        assertTrue(submitResult is MemoryConsolidator.SubmitResult.ACCEPTED)

        val evalResult = consolidator.evaluateCandidate(experience.experienceId)
        // With very low importance/relevance, should be rejected
        assertTrue(evalResult is MemoryConsolidator.EvaluationResult.REJECTED ||
                   evalResult is MemoryConsolidator.EvaluationResult.DEFERRED)
    }

    @Test
    fun `get current truths`() = runBlocking {
        // Add some memories
        for (i in 1..3) {
            val exp = ExperienceRecord(
                experienceId = "exp_truth_$i",
                source = ExperienceSource.USER_INTERACTION,
                action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "Fact $i"),
                result = ExperienceResult(outcome = Outcome.SUCCESS),
                confidence = 0.9f,
                importance = 0.8f,
                candidateMemoryType = MemoryType.FACT
            )
            consolidator.submitExperience(exp)
            consolidator.evaluateCandidate(exp.experienceId)
        }

        val truths = consolidator.getCurrentTruths()
        assertEquals(3, truths.size)
        assertTrue(truths.all { it.isCurrentTruth() })
    }

    @Test
    fun `consolidator stats`() = runBlocking {
        val stats = consolidator.getStats()
        assertNotNull(stats)
        assertEquals(0, stats.totalSubmissions)
        assertEquals(0, stats.totalPromotions)
        assertEquals(0, stats.totalRejections)
    }

    @Test
    fun `reevaluate deferred`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "exp_deferred",
            source = ExperienceSource.OBSERVATION,
            action = ExperienceAction(actionType = ActionType.OBSERVE, description = "Medium importance"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.5f,
            importance = 0.4f,
            candidateMemoryType = MemoryType.EPISODIC
        )

        consolidator.submitExperience(experience)
        // Don't evaluate immediately - let it be deferred

        val count = consolidator.reevaluateDeferred()
        assertEquals(1, count)
    }
}