package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class ContinuityManagerTest {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val consolidator = MemoryConsolidator(scope, config = MemoryConsolidator.Config(autoEvaluate = false))
    private val updateEngine = MemoryUpdateEngine(scope, consolidator)
    private val conflictResolver = MemoryConflictResolver(updateEngine)
    private val continuityManager = ContinuityManager(scope, consolidator, updateEngine, conflictResolver)

    @Test
    fun `reconstruct current state with memories`() = runBlocking {
        // Create several memories
        val facts = listOf(
            "User name is Alice",
            "User prefers dark mode",
            "User works as developer",
            "User lives in Berlin"
        )

        for ((i, fact) in facts.withIndex()) {
            val exp = ExperienceRecord(
                experienceId = "exp_continuity_$i",
                source = ExperienceSource.USER_INTERACTION,
                action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = fact),
                result = ExperienceResult(outcome = Outcome.SUCCESS),
                confidence = 0.9f,
                importance = 0.8f,
                candidateMemoryType = MemoryType.FACT,
                tags = listOf("user", "profile")
            )
            consolidator.submitExperience(exp)
            consolidator.evaluateCandidate(exp.experienceId)
        }

        // Reconstruct
        val state = continuityManager.reconstructCurrentState(
            query = "user profile",
            currentGoal = "Understand user preferences",
            activeEntities = listOf("user", "Alice")
        )

        assertEquals(4, state.activeTruths.size)
        assertEquals("user profile", state.query)
        assertEquals("Understand user preferences", state.currentGoal)
        assertEquals(2, state.activeEntities.size)
        assertTrue(state.overallConfidence > 0.8f)
        assertTrue(state.memoryCount >= 4)
    }

    @Test
    fun `reconstruct includes historical context after supersession`() = runBlocking {
        // Create original memory
        val exp1 = ExperienceRecord(
            experienceId = "exp_hist_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User prefers light theme"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        consolidator.submitExperience(exp1)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        // Supersede
        updateEngine.supersedeMemory(
            oldMemoryId = mem1.memoryId,
            newContent = "User prefers dark theme",
            newConfidence = 0.95f,
            reason = "User changed preference",
            triggeringExperienceId = "exp_change",
            source = "user_correction"
        )

        // Reconstruct
        val state = continuityManager.reconstructCurrentState(
            query = "theme preference",
            activeEntities = listOf("user", "theme")
        )

        // Should have 1 current truth (dark) and 1 historical (light)
        assertEquals(1, state.activeTruths.size)
        assertTrue(state.activeTruths.first().content.contains("dark"))

        assertEquals(1, state.historicalContext.size)
        assertTrue(state.historicalContext.first().content.contains("light"))
        assertNotNull(state.historicalContext.first().supersededBy)
    }

    @Test
    fun `reconstruct detects conflicts`() = runBlocking {
        // Create two conflicting memories
        val exp1 = ExperienceRecord(
            experienceId = "exp_conf_rec_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User name is Alice"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.8f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.FACT
        )

        val exp2 = ExperienceRecord(
            experienceId = "exp_conf_rec_2",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User name is Bob"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.8f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp1)
        consolidator.submitExperience(exp2)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        val eval2 = consolidator.evaluateCandidate(exp2.experienceId)

        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!
        val mem2 = consolidator.getMemory((eval2 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        // Manually add conflict to mem1
        val conflicted = mem1.copy(conflictIds = listOf(mem2.memoryId))
        consolidator.consolidatedMemories[mem1.memoryId] = conflicted

        // Reconstruct
        val state = continuityManager.reconstructCurrentState(
            query = "user name",
            activeEntities = listOf("user", "name")
        )

        assertTrue(state.unresolvedConflicts.isNotEmpty())
        assertEquals(1, state.unresolvedConflicts.first().conflictCount)
    }

    @Test
    fun `reconstruct detects unknown gaps`() = runBlocking {
        // Create memory but no entity memory
        val exp = ExperienceRecord(
            experienceId = "exp_gap_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User likes pizza"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        consolidator.submitExperience(exp)
        consolidator.evaluateCandidate(exp.experienceId)

        // Reconstruct with entity that has no memory
        val state = continuityManager.reconstructCurrentState(
            query = "food preferences",
            activeEntities = listOf("user", "sushi") // sushi not in memories
        )

        val missingEntityGaps = state.unknowns.filter { it.gapType == UnknownType.MISSING_ENTITY }
        assertTrue(missingEntityGaps.isNotEmpty())
        assertTrue(missingEntityGaps.first().description.contains("sushi"))
    }

    @Test
    fun `get continuity summary`() = runBlocking {
        val exp = ExperienceRecord(
            experienceId = "exp_summary_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User prefers coffee"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        consolidator.submitExperience(exp)
        consolidator.evaluateCandidate(exp.experienceId)

        val summary = continuityManager.getContinuitySummary("coffee")

        assertEquals("coffee", summary.topic)
        assertEquals(1, summary.currentTruthCount)
        assertEquals(0, summary.historicalCount)
        assertFalse(summary.hasUnresolvedConflicts)
        assertNotNull(summary.latestTruth)
        assertTrue(summary.latestTruth!!.contains("coffee"))
    }

    @Test
    fun `get temporal chain`() = runBlocking {
        // Create original
        val exp1 = ExperienceRecord(
            experienceId = "exp_chain_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User prefers tea"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE
        )

        consolidator.submitExperience(exp1)
        val eval1 = consolidator.evaluateCandidate(exp1.experienceId)
        val mem1 = consolidator.getMemory((eval1 as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        // Supersede
        updateEngine.supersedeMemory(
            oldMemoryId = mem1.memoryId,
            newContent = "User prefers coffee",
            newConfidence = 0.95f,
            reason = "Changed preference",
            triggeringExperienceId = "exp_change_chain",
            source = "user_correction"
        )

        // Get temporal chain
        val chain = continuityManager.getTemporalChain("prefer")

        assertEquals(2, chain.size)
        assertEquals(ChainState.SUPERSEDED, chain[0].state)
        assertEquals(ChainState.CURRENT, chain[1].state)
        assertTrue(chain[0].content.contains("tea"))
        assertTrue(chain[1].content.contains("coffee"))
        assertEquals(chain[1].memoryId, chain[0].supersededBy)
    }

    @Test
    fun `explain memory`() = runBlocking {
        val exp = ExperienceRecord(
            experienceId = "exp_explain_1",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User name is Carol"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.FACT
        )

        consolidator.submitExperience(exp)
        val eval = consolidator.evaluateCandidate(exp.experienceId)
        val mem = consolidator.getMemory((eval as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        val explanation = continuityManager.explainMemory(mem.memoryId)

        assertNotNull(explanation)
        assertEquals(mem.memoryId, explanation!!.memory.memoryId)
        assertTrue(explanation.provenance.contains("exp_explain_1"))
        assertTrue(explanation.provenance.contains("INITIAL_CONSOLIDATION"))
    }
}