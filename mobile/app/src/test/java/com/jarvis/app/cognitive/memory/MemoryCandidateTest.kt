package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class MemoryCandidateTest {

    @Test
    fun `promotion factors compute score correctly`() = runBlocking {
        val factors = PromotionFactors(
            recurrence = 1.0f,
            importance = 1.0f,
            userRelevance = 1.0f,
            goalRelevance = 1.0f,
            futureUtility = 1.0f,
            novelty = 1.0f,
            reliability = 1.0f,
            explicitInstruction = 1.0f,
            failureLearningValue = 1.0f
        )

        val score = factors.computeScore()
        assertEquals(1.0f, score, 0.001f)
    }

    @Test
    fun `promotion factors compute zero score when all zero`() = runBlocking {
        val factors = PromotionFactors()
        val score = factors.computeScore()
        assertEquals(0.0f, score, 0.001f)
    }

    @Test
    fun `promotion factors weight distribution sums to 1`() = runBlocking {
        // Test that weights are properly distributed
        val factors = PromotionFactors(
            recurrence = 1.0f,
            importance = 0.0f,
            userRelevance = 0.0f,
            goalRelevance = 0.0f,
            futureUtility = 0.0f,
            novelty = 0.0f,
            reliability = 0.0f,
            explicitInstruction = 0.0f,
            failureLearningValue = 0.0f
        )

        // Recurrence weight is 0.15
        assertEquals(0.15f, factors.computeScore(), 0.001f)

        val factors2 = PromotionFactors(
            recurrence = 0.0f,
            importance = 1.0f,
            userRelevance = 0.0f,
            goalRelevance = 0.0f,
            futureUtility = 0.0f,
            novelty = 0.0f,
            reliability = 0.0f,
            explicitInstruction = 0.0f,
            failureLearningValue = 0.0f
        )
        // Importance weight is 0.20
        assertEquals(0.20f, factors2.computeScore(), 0.001f)
    }

    @Test
    fun `candidate promotion decision`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "exp_1",
            candidateMemoryType = MemoryType.FACT
        )

        val candidate = MemoryCandidate(
            experience = experience,
            promotionFactors = PromotionFactors(
                importance = 0.8f,
                userRelevance = 0.9f,
                reliability = 0.9f
            ),
            promotionScore = 0.75f,
            evaluated = true
        )

        val decision = candidate.getPromotionDecision(0.6f, 0.3f)
        assertEquals(PromotionDecision.PROMOTE, decision)
    }

    @Test
    fun `candidate rejection decision`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "exp_2",
            candidateMemoryType = MemoryType.CONTEXT
        )

        val candidate = MemoryCandidate(
            experience = experience,
            promotionFactors = PromotionFactors(
                importance = 0.1f,
                userRelevance = 0.1f,
                reliability = 0.2f
            ),
            promotionScore = 0.2f,
            evaluated = true
        )

        val decision = candidate.getPromotionDecision(0.6f, 0.3f)
        assertEquals(PromotionDecision.REJECT, decision)
    }

    @Test
    fun `candidate deferred decision`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "exp_3",
            candidateMemoryType = MemoryType.EPISODIC
        )

        val candidate = MemoryCandidate(
            experience = experience,
            promotionFactors = PromotionFactors(
                importance = 0.5f,
                userRelevance = 0.4f,
                reliability = 0.5f
            ),
            promotionScore = 0.45f,
            evaluated = true
        )

        val decision = candidate.getPromotionDecision(0.6f, 0.3f)
        assertEquals(PromotionDecision.DEFERRED, decision)
    }

    @Test
    fun `unevaluated candidate is deferred`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "exp_4",
            candidateMemoryType = MemoryType.FACT
        )

        val candidate = MemoryCandidate(
            experience = experience,
            promotionScore = 0.8f,
            evaluated = false
        )

        val decision = candidate.getPromotionDecision(0.6f, 0.3f)
        assertEquals(PromotionDecision.DEFERRED, decision)
    }
}