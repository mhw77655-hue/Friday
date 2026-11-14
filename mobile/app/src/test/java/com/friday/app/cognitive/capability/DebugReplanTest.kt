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

class DebugReplanTest {
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

    private class FakeDriver(private val planner: GoalPlanner, initialGraph: PlanGraph) : PlanDriver {
        var graph: PlanGraph = initialGraph
        val events = mutableListOf<CognitiveEvent>()
        val recordedOutcomes = mutableListOf<DecisionOutcome>()

        override fun getPlanGraph(): PlanGraph? = graph
        override fun completePlanStep(nodeId: String, outcome: String?): PlanGraph? {
            graph = planner.completeNode(graph, nodeId, outcome); return graph
        }
        override fun failPlanStep(nodeId: String, cause: String): PlanGraph? {
            println("FAIL PLAN STEP: nodeId=$nodeId, cause=$cause")
            graph = planner.failNode(graph, nodeId, cause); return graph
        }
        override fun replanPlan(failedNodeId: String, cause: String): PlanGraph? {
            println("REPLAN PLAN: failedNodeId=$failedNodeId, cause=$cause")
            println("Before replan nodes:")
            graph.nodes.forEach { println("  ${it.id} - ${it.status} - prereqs: ${it.prerequisites}") }
            graph = planner.replan(graph, failedNodeId, cause)
            println("After replan nodes:")
            graph.nodes.forEach { println("  ${it.id} - ${it.status} - prereqs: ${it.prerequisites}") }
            return graph
        }
        override fun recordDecisionOutcome(outcome: DecisionOutcome) {
            recordedOutcomes.add(outcome)
        }
        override fun emit(event: CognitiveEvent) {
            events.add(event)
        }
    }

    private fun testImmune(): ImmuneSystem = ImmuneSystem(
        surface = FailureSurface(nowMs = { 0L }),
        backoffPolicy = BackoffPolicy(maxAttempts = 1, baseDelayMs = 0, jitterMs = 0),
        breakerFactory = { key -> CircuitBreaker(name = key, openMs = 30_000, nowMs = { 0L }) },
        emit = {}
    )

    private fun executor(fabric: CapabilityFabric, strategy: DecompositionStrategy): Triple<CapabilityExecutor, FakeDriver, GoalPlanner> {
        val planner = GoalPlanner(strategy)
        val driver = FakeDriver(planner, graphOf(strategy))
        val exec = CapabilityExecutor(fabric, driver, goalPlanner = planner)
        return Triple(exec, driver, planner)
    }

    @Test
    fun debugReplan() = runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("A", operation = "compute_a")) {
            ExecutionResult.fail(ActionFailure.Error("X", "boom", recoverable = false))
        })
        fabric.register(FakeCapability(descriptor("ALT", operation = "compute_alt")))
        fabric.register(FakeCapability(descriptor("B", operation = "compute_b")))
        fabric.register(FakeCapability(descriptor("C", operation = "compute_c")))

        val (exec, driver, _) = executor(fabric, FullStrategy)
        println("=== INITIAL GRAPH ===")
        driver.graph.nodes.forEach { println("  ${it.id} - ${it.status} - prereqs: ${it.prerequisites}") }
        
        val summary = exec.run()
        
        println("=== FINAL GRAPH ===")
        driver.graph.nodes.forEach { println("  ${it.id} - ${it.status} - prereqs: ${it.prerequisites}") }
        println("Replan triggered: ${summary.replanTriggered}")
        println("Replan count: ${exec.replanCount}")
        println("Summary: ${summary}")
    }
}
