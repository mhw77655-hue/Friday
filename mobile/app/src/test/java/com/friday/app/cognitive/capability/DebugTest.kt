package com.friday.app.cognitive.capability

import com.friday.app.cognitive.CognitiveEngine.CognitiveEvent
import com.friday.app.cognitive.DecisionOutcome
import com.friday.app.cognitive.Goal
import com.friday.app.cognitive.execution.ActionFailure
import com.friday.app.cognitive.execution.ActionRequest
import com.friday.app.cognitive.execution.ExecutionResult
import com.friday.app.cognitive.execution.ExecutionStatus
import com.friday.app.cognitive.execution.PlanDriver
import com.friday.app.cognitive.immune.ImmuneSystem
import com.friday.app.cognitive.planning.DecompositionStrategy
import com.friday.app.cognitive.planning.GoalPlanner
import com.friday.app.cognitive.planning.PlanGraph
import com.friday.app.cognitive.planning.PlanNode
import com.friday.app.cognitive.planning.StepSpec
import com.friday.app.cognitive.planning.SubgoalSpec
import com.friday.app.failure.BackoffPolicy
import com.friday.app.failure.CircuitBreaker
import com.friday.app.failure.DegradationLevel
import com.friday.app.failure.FailureSurface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTest {
    private object FullStrategy : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a"),
            SubgoalSpec("sg2", "b", dependencies = listOf("sg1")),
            SubgoalSpec("sg3", "c", dependencies = listOf("sg2"))
        )
        override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> = when (subgoal.id) {
            "sg1" -> listOf(StepSpec("s1", "do a", "compute_a", "done a"))
            "sg2" -> listOf(StepSpec("s1", "do b", "compute_b", "done b"))
            "sg3" -> listOf(StepSpec("s1", "do c", "compute_c", "done c"))
            else -> emptyList()
        }
        override fun proposeReplacement(failed: PlanNode, cause: String): List<StepSpec> =
            listOf(StepSpec("alt", "alternate for ${failed.id}", "compute_alt", failed.expectedOutcome))
    }

    private fun graphOf(strategy: DecompositionStrategy): PlanGraph =
        GoalPlanner(strategy).createPlan(Goal("g1", "ship", successCriteria = listOf("done"))).graph

    @Test
    fun debugNodeIds() = runBlocking {
        val graph = graphOf(FullStrategy)
        println("Nodes:")
        graph.nodes.forEach { println("  ${it.id} - ${it.action} - prereqs: ${it.prerequisites} - status: ${it.status}") }
        println("Is complete: ${graph.isComplete}")
        println("Status: ${graph.status}")
    }
}