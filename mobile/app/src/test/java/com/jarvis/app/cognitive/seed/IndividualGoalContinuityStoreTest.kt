package com.jarvis.app.cognitive.seed

import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.GoalPriority
import com.jarvis.app.cognitive.GoalStatus
import com.jarvis.app.cognitive.Subgoal
import com.jarvis.app.cognitive.SubgoalStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndividualGoalContinuityStoreTest {

    private fun tempDir(): File = File.createTempFile("friday-goal-continuity-", ".dir").apply {
        delete()
        mkdirs()
    }

    @Test
    fun `goal and subgoals survive a new store instance for the same individual`() {
        val directory = tempDir()
        val goal = Goal(
            id = "goal-build-continuity",
            description = "Preserve Friday's identity and unfinished commitments.",
            priority = GoalPriority.HIGH,
            createdAt = 1_000L,
            deadline = 9_000L,
            successCriteria = listOf("Identity remains stable.", "Active goals recover."),
            status = GoalStatus.ACTIVE
        )
        val subgoals = listOf(
            Subgoal(
                id = "subgoal-identity",
                goalId = goal.id,
                description = "Keep the persistent individual identity.",
                status = SubgoalStatus.COMPLETED,
                createdAt = 1_100L,
                estimatedEffort = 2.5f
            ),
            Subgoal(
                id = "subgoal-recovery",
                goalId = goal.id,
                description = "Restore unfinished goals after process death.",
                status = SubgoalStatus.IN_PROGRESS,
                createdAt = 1_200L,
                estimatedEffort = 3.0f,
                dependencies = listOf("subgoal-identity")
            )
        )

        IndividualGoalContinuityStore(directory) { 5_000L }
            .save("friday-individual-1", goal, subgoals)

        val restored = IndividualGoalContinuityStore(directory) { 8_000L }
            .load("friday-individual-1") as GoalContinuityLoad.Restored

        assertEquals(goal, restored.continuity.goal)
        assertEquals(subgoals, restored.continuity.subgoals)
        assertEquals(5_000L, restored.continuity.updatedAtMs)
        directory.deleteRecursively()
    }

    @Test
    fun `goal history is never attached to a different individual identity`() {
        val directory = tempDir()
        val goal = Goal(id = "g1", description = "Keep learning.", createdAt = 1L)
        IndividualGoalContinuityStore(directory).save("friday-a", goal, emptyList())

        val result = IndividualGoalContinuityStore(directory).load("friday-b")

        assertTrue(result is GoalContinuityLoad.IdentityMismatch)
        directory.deleteRecursively()
    }

    @Test
    fun `corrupt goal history is reported and preserved for diagnosis`() {
        val directory = tempDir()
        val file = File(directory, IndividualGoalContinuityStore.FILE_NAME)
        file.parentFile?.mkdirs()
        file.writeText("{not-json")
        
        val result = IndividualGoalContinuityStore(directory).load("friday-a")

        assertTrue(result is GoalContinuityLoad.Corrupt)
        assertEquals("{not-json", file.readText())
        directory.deleteRecursively()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subgoals cannot be persisted under a different parent goal`() {
        val directory = tempDir()
        val goal = Goal(id = "g1", description = "Keep learning.", createdAt = 1L)
        val subgoal = Subgoal(id = "s1", goalId = "g2", description = "Not this goal.")
        IndividualGoalContinuityStore(directory).save("friday-a", goal, listOf(subgoal))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `cyclic subgoal dependencies cannot be persisted`() {
        val directory = tempDir()
        val goal = Goal(id = "g1", description = "Build a coherent goal graph.", createdAt = 1L)
        IndividualGoalContinuityStore(directory).save(
            "friday-a",
            goal,
            listOf(
                Subgoal(id = "a", goalId = "g1", description = "A", createdAt = 1L, dependencies = listOf("b")),
                Subgoal(id = "b", goalId = "g1", description = "B", createdAt = 1L, dependencies = listOf("a"))
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown subgoal dependencies are rejected`() {
        val directory = tempDir()
        val goal = Goal(id = "g1", description = "Build a coherent goal graph.", createdAt = 1L)
        IndividualGoalContinuityStore(directory).save(
            "friday-a",
            goal,
            listOf(
                Subgoal(id = "a", goalId = "g1", description = "A", createdAt = 1L, dependencies = listOf("missing"))
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate subgoal identities cannot be persisted`() {
        val directory = tempDir()
        val goal = Goal(id = "g1", description = "Build a coherent goal graph.", createdAt = 1L)
        IndividualGoalContinuityStore(directory).save(
            "friday-a",
            goal,
            listOf(
                Subgoal(id = "same", goalId = "g1", description = "First", createdAt = 1L),
                Subgoal(id = "same", goalId = "g1", description = "Second", createdAt = 1L)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `success criteria are validated before writing so the store cannot reject its own snapshot`() {
        val directory = tempDir()
        val goal = Goal(
            id = "g1",
            description = "Build a coherent goal graph.",
            createdAt = 1L,
            successCriteria = listOf("x".repeat(501))
        )
        IndividualGoalContinuityStore(directory).save("friday-a", goal, emptyList())
    }

}
