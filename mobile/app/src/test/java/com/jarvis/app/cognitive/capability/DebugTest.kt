package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.DecisionOutcome
import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult
import com.jarvis.app.cognitive.execution.ExecutionStatus
import com.jarvis.app.cognitive.execution.PlanDriver
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.cognitive.planning.DecompositionStrategy
import com.jarvis.app.cognitive.planning.GoalPlanner
import com.jarvis.app.cognitive.planning.PlanGraph
import com.jarvis.app.cognitive.planning.PlanNode
import com.jarvis.app.cognitive.planning.StepSpec
import com.jarvis.app.cognitive.planning.SubgoalSpec
import com.jarvis.app.failure.BackoffPolicy
import com.jarvis.app.failure.CircuitBreaker
import com.jarvis.app.failure.DegradationLevel
import com.jarvis.app.failure.FailureSurface
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