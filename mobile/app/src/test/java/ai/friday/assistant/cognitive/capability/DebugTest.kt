package ai.friday.assistant.cognitive.capability

import ai.friday.assistant.cognitive.CognitiveEngine.CognitiveEvent
import ai.friday.assistant.cognitive.DecisionOutcome
import ai.friday.assistant.cognitive.Goal
import ai.friday.assistant.cognitive.execution.ActionFailure
import ai.friday.assistant.cognitive.execution.ActionRequest
import ai.friday.assistant.cognitive.execution.ExecutionResult
import ai.friday.assistant.cognitive.execution.ExecutionStatus
import ai.friday.assistant.cognitive.execution.PlanDriver
import ai.friday.assistant.cognitive.immune.ImmuneSystem
import ai.friday.assistant.cognitive.planning.DecompositionStrategy
import ai.friday.assistant.cognitive.planning.GoalPlanner
import ai.friday.assistant.cognitive.planning.PlanGraph
import ai.friday.assistant.cognitive.planning.PlanNode
import ai.friday.assistant.cognitive.planning.StepSpec
import ai.friday.assistant.cognitive.planning.SubgoalSpec
import ai.friday.assistant.failure.BackoffPolicy
import ai.friday.assistant.failure.CircuitBreaker
import ai.friday.assistant.failure.DegradationLevel
import ai.friday.assistant.failure.FailureSurface
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