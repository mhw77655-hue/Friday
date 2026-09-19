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

class ExperienceRecordTest {

    @Test
    fun `experience record creation with all fields`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "test_exp_1",
            timestamp = 1000L,
            source = ExperienceSource.USER_INTERACTION,
            context = ExperienceContext(turnIndex = 5, currentIntent = "question"),
            relatedEntities = listOf(
                RelatedEntity("ent_1", "User", EntityType.PERSON, EntityRole.ACTOR)
            ),
            relatedGoalId = "goal_1",
            action = ExperienceAction(
                actionType = ActionType.SPEAK,
                description = "User asked about weather",
                parameters = mapOf("topic" to "weather")
            ),
            result = ExperienceResult(
                outcome = Outcome.SUCCESS,
                output = "Weather is sunny"
            ),
            observations = listOf("User seemed happy"),
            failures = emptyList(),
            evidence = listOf(
                Evidence(EvidenceType.OBSERVATION, "Direct user interaction", "user", 1.0f)
            ),
            confidence = 0.9f,
            importance = 0.7f,
            tags = listOf("weather", "question"),
            candidateMemoryType = MemoryType.EPISODIC
        )

        assertEquals("test_exp_1", experience.experienceId)
        assertEquals(ExperienceSource.USER_INTERACTION, experience.source)
        assertEquals(1, experience.relatedEntities.size)
        assertEquals("goal_1", experience.relatedGoalId)
        assertEquals(ActionType.SPEAK, experience.action.actionType)
        assertEquals(Outcome.SUCCESS, experience.result.outcome)
        assertEquals(1, experience.observations.size)
        assertEquals(0, experience.failures.size)
        assertEquals(1, experience.evidence.size)
        assertEquals(0.9f, experience.confidence)
        assertEquals(0.7f, experience.importance)
        assertEquals(2, experience.tags.size)
        assertEquals(MemoryType.EPISODIC, experience.candidateMemoryType)
    }

    @Test
    fun `experience record summary`() = runBlocking {
        val experience = ExperienceRecord(
            experienceId = "test_exp_2",
            source = ExperienceSource.COGNITIVE_CYCLE,
            action = ExperienceAction(actionType = ActionType.THINK, description = "Reasoning"),
            result = ExperienceResult(outcome = Outcome.PARTIAL),
            candidateMemoryType = MemoryType.FACT
        )

        val summary = experience.summary()
        assertTrue(summary.contains("test_exp_2"))
        assertTrue(summary.contains("FACT"))
        assertTrue(summary.contains("THINK"))
        assertTrue(summary.contains("PARTIAL"))
    }
}