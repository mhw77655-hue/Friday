package com.jarvis.app.cognitive.execution

import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.DecisionOutcome
import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.planning.DecompositionStrategy
import com.jarvis.app.cognitive.planning.GoalPlanner
import com.jarvis.app.cognitive.planning.PlanGraph
import com.jarvis.app.cognitive.planning.PlanNode
import com.jarvis.app.cognitive.planning.StepSpec
import com.jarvis.app.cognitive.planning.SubgoalSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ExecutionEngine: step selection, dispatch through a fake ActionPort, retry
 * policy, replan triggering, terminals (completed/blocked/failed/cancelled/
 * paused), state transitions, outcome feedback, determinism.
 */
class ExecutionEngineTest {

    private val strategy = object : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a"),
            SubgoalSpec("sg2", "b", dependencies = listOf("sg1")),
            SubgoalSpec("sg3", "c", dependencies = listOf("sg2"))
        )

        override fun decomposeSubgoal(s: SubgoalSpec): List<StepSpec> = when (s.id) {
            "sg1" -> listOf(StepSpec("s1", "do a", "action_a", "done a"))
            "sg2" -> listOf(StepSpec("s1", "do b", "action_b", "done b"))
            "sg3" -> listOf(StepSpec("s1", "do c", "action_c", "done c"))
            else -> emptyList()
        }

        override fun proposeReplacement(failed: PlanNode, cause: String): List<StepSpec> =
            listOf(StepSpec("alt", "alternate for ${failed.id}", "action_alt", failed.expectedOutcome))
    }

    /** A fully-decomposable strategy that never proposes a replacement. */
    private object NoReplaceStrategy : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(SubgoalSpec("sg1", "a"))
        override fun decomposeSubgoal(s: SubgoalSpec): List<StepSpec> =
            listOf(StepSpec("s1", "do a", "action_a", "done a"))
        // proposeReplacement -> empty (base default)
    }

    /** A strategy whose second subgoal is undecomposable -> sentinel blocked. */
    private object PartialStrategy : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a"),
            SubgoalSpec("sg2", "b", dependencies = listOf("sg1"))
        )
        override fun decomposeSubgoal(s: SubgoalSpec): List<StepSpec> = when (s.id) {
            "sg1" -> listOf(StepSpec("s1", "do a", "action_a", "done a"))
            else -> emptyList()
        }
    }

    private class FakePort(
        private val handler: suspend (ActionRequest) -> ExecutionResult
    ) : ActionPort {
        var calls = 0
        override suspend fun execute(request: ActionRequest): ExecutionResult {
            calls++
            return handler(request)
        }
    }

    private class FakeDriver(private val planner: GoalPlanner, initialGraph: PlanGraph) : PlanDriver {
        var graph: PlanGraph = initialGraph
        val events = mutableListOf<CognitiveEngine.CognitiveEvent>()
        val recordedOutcomes = mutableListOf<DecisionOutcome>()

        override fun getPlanGraph(): PlanGraph? = graph
        override fun completePlanStep(nodeId: String, outcome: String?): PlanGraph? {
            graph = planner.completeNode(graph, nodeId, outcome)
            return graph
        }
        override fun failPlanStep(nodeId: String, cause: String): PlanGraph? {
            graph = planner.failNode(graph, nodeId, cause)
            return graph
        }
        override fun replanPlan(failedNodeId: String, cause: String): PlanGraph? {
            graph = planner.replan(graph, failedNodeId, cause)
            return graph
        }
        override fun recordDecisionOutcome(outcome: DecisionOutcome) {
            recordedOutcomes.add(outcome)
        }
        override fun emit(event: CognitiveEngine.CognitiveEvent) {
            events.add(event)
        }
    }

    private fun graphOf(decomposition: DecompositionStrategy, description: String = "ship"): PlanGraph {
        val planner = GoalPlanner(decomposition)
        return planner.createPlan(Goal("g1", description, successCriteria = listOf("done"))).graph
    }

    private fun alwaysOk(): FakePort = FakePort { ExecutionResult.ok(output = mapOf("outcome" to "done")) }

    private fun engineOf(
        port: ActionPort,
        driver: PlanDriver,
        retry: RetryPolicy = RetryPolicy(),
        config: ExecutionConfig = ExecutionConfig()
    ): ExecutionEngine = ExecutionEngine(port, driver, goalPlanner = GoalPlanner(strategy), retryPolicy = retry, config = config)

    // ------------------------------------------------------------------
    // Selection / success
    // ------------------------------------------------------------------

    @Test
    fun `next actionable step is selected and dispatched`() = runBlocking {
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))
        val engine = engineOf(alwaysOk(), driver)

        engine.load()
        engine.executeNextStep()

        assertEquals("sg1:s1", engine.getState().currentStepId)
        assertEquals(ExecutionStatus.EXECUTING, engine.getState().status)
        assertEquals(1, engine.getState().completedSteps)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.StepStarted && it.stepId == "sg1:s1" })
    }

    @Test
    fun `action request carries plan step and action`() = runBlocking {
        var captured: ActionRequest? = null
        val port = FakePort { req -> captured = req; ExecutionResult.ok() }
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))

        val engine = engineOf(port, driver)
        engine.load()
        engine.executeNextStep()

        assertEquals("g1", captured!!.planId)
        assertEquals("sg1:s1", captured!!.stepId)
        assertEquals("action_a", captured!!.action)
    }

    @Test
    fun `successful multi step plan completes in dependency order`() = runBlocking {
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))
        val summary = engineOf(alwaysOk(), driver).runToTerminal()

        assertEquals(ExecutionStatus.COMPLETED, summary.status)
        assertEquals(3, summary.stepsCompleted)
        assertEquals(0, summary.stepsFailed)

        val started = driver.events.filterIsInstance<CognitiveEngine.CognitiveEvent.StepStarted>().map { it.stepId }
        assertEquals(listOf("sg1:s1", "sg2:s1", "sg3:s1"), started)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.PlanExecutionCompleted })
    }

    @Test
    fun `execution state transitions through to completed`() = runBlocking {
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))
        val engine = engineOf(alwaysOk(), driver)
        engine.runToTerminal()

        val state = engine.getState()
        assertEquals(ExecutionStatus.COMPLETED, state.status)
        assertEquals(3, state.completedSteps)
        assertEquals(3, state.executedSteps)
        assertEquals("sg3:s1", state.currentStepId)
        assertTrue(state.lastResult!!.success)
    }

    // ------------------------------------------------------------------
    // Failure / retry
    // ------------------------------------------------------------------

    @Test
    fun `retryable failure is retried then succeeds`() = runBlocking {
        var calls = 0
        val port = FakePort {
            calls++
            if (calls <= 1) ExecutionResult.fail(ActionFailure.Error("NET", "timeout", recoverable = true))
            else ExecutionResult.ok()
        }
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))
        val summary = engineOf(port, driver, retry = RetryPolicy(maxAttempts = 3)).runToTerminal()

        assertEquals(ExecutionStatus.COMPLETED, summary.status)
        // sg1:s1 attempted twice, sg2:s1 once, sg3:s1 once
        assertEquals(4, calls)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.StepRetrying })
        assertEquals(3, driver.recordedOutcomes.size)
        assertTrue(driver.recordedOutcomes.all { it == DecisionOutcome.SUCCESS })
    }

    @Test
    fun `no infinite retry on a permanently retryable failure`() = runBlocking {
        val port = FakePort { ExecutionResult.fail(ActionFailure.Error("NET", "down", recoverable = true)) }
        val driver = FakeDriver(GoalPlanner(NoReplaceStrategy), graphOf(NoReplaceStrategy))
        val engine = engineOf(port, driver, retry = RetryPolicy(maxAttempts = 3))

        val summary = engine.runToTerminal()

        assertEquals(ExecutionStatus.FAILED, summary.status)
        // 3 attempts for the single step, then no more
        assertEquals(3, port.calls)
        val started = driver.events.filterIsInstance<CognitiveEngine.CognitiveEvent.StepStarted>().count { it.stepId == "sg1:s1" }
        assertEquals(3, started)
        val retries = driver.events.filterIsInstance<CognitiveEngine.CognitiveEvent.StepRetrying>().size
        assertEquals(2, retries)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.PlanExecutionFailed })
        assertTrue(driver.recordedOutcomes.contains(DecisionOutcome.FAILED))
    }

    @Test
    fun `non retryable failure with no replacement fails the plan`() = runBlocking {
        val port = FakePort { ExecutionResult.fail(ActionFailure.Error("AUTH", "denied", recoverable = false)) }
        val driver = FakeDriver(GoalPlanner(NoReplaceStrategy), graphOf(NoReplaceStrategy))

        val summary = engineOf(port, driver).runToTerminal()

        assertEquals(ExecutionStatus.FAILED, summary.status)
        assertEquals(1, port.calls) // no retry, no repeated dispatch
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.StepFailed })
        assertTrue(driver.recordedOutcomes.contains(DecisionOutcome.FAILED))
    }

    @Test
    fun `replan is triggered on non retryable failure and execution continues`() = runBlocking {
        val port = FakePort { req ->
            if (req.action == "action_b") ExecutionResult.fail(ActionFailure.Error("API", "down", recoverable = false))
            else ExecutionResult.ok()
        }
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))

        val summary = engineOf(port, driver).runToTerminal()

        assertEquals(ExecutionStatus.COMPLETED, summary.status)
        assertTrue(summary.replanTriggered)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.ReplanTriggered })
        // replacement node was executed
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.StepStarted && it.stepId == "sg2:s1_r0" })
        assertEquals(1, summary.stepsFailed)
        assertEquals(3, summary.stepsCompleted)
    }

    @Test
    fun `invalid action fails the step and replans`() = runBlocking {
        val port = FakePort { req ->
            if (req.action == "action_b") ExecutionResult.fail(ActionFailure.Unsupported("action_b"))
            else ExecutionResult.ok()
        }
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))

        val summary = engineOf(port, driver).runToTerminal()

        assertEquals(ExecutionStatus.COMPLETED, summary.status)
        val stepFailed = driver.events.filterIsInstance<CognitiveEngine.CognitiveEvent.StepFailed>().first()
        assertTrue(stepFailed.cause!!.contains("unsupported action"))
    }

    @Test
    fun `action port throwing is treated as a non recoverable port failure`() = runBlocking {
        val port = FakePort { throw RuntimeException("port down") }
        val driver = FakeDriver(GoalPlanner(NoReplaceStrategy), graphOf(NoReplaceStrategy))

        val summary = engineOf(port, driver).runToTerminal()

        assertEquals(ExecutionStatus.FAILED, summary.status)
        val failed = driver.events.filterIsInstance<CognitiveEngine.CognitiveEvent.StepFailed>().first()
        assertTrue(failed.cause!!.contains("PORT_EXCEPTION"))
    }

    @Test
    fun `repeated failure is bounded by max replans`() = runBlocking {
        val port = FakePort { ExecutionResult.fail(ActionFailure.Error("X", "always", recoverable = false)) }
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))

        val summary = engineOf(port, driver, config = ExecutionConfig(maxStepsPerRun = 100, maxReplans = 3)).runToTerminal()

        assertEquals(ExecutionStatus.FAILED, summary.status)
        val replans = driver.events.filterIsInstance<CognitiveEngine.CognitiveEvent.ReplanTriggered>().size
        assertEquals(3, replans)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.PlanExecutionFailed })
    }

    // ------------------------------------------------------------------
    // Terminals
    // ------------------------------------------------------------------

    @Test
    fun `blocked plan terminates blocked with a StepBlocked event`() = runBlocking {
        val driver = FakeDriver(GoalPlanner(PartialStrategy), graphOf(PartialStrategy))
        val summary = engineOf(alwaysOk(), driver).runToTerminal()

        assertEquals(ExecutionStatus.BLOCKED, summary.status)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.StepBlocked })
    }

    @Test
    fun `cancelled execution stops and emits cancellation`() = runBlocking {
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))
        val engine = engineOf(alwaysOk(), driver)
        engine.load()
        engine.cancel()

        assertEquals(ExecutionStatus.CANCELLED, engine.getState().status)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.PlanExecutionCancelled })

        val summary = engine.runToTerminal()
        assertEquals(ExecutionStatus.CANCELLED, summary.status)
    }

    @Test
    fun `paused execution stops between steps`() = runBlocking {
        val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))
        val engine = engineOf(alwaysOk(), driver)
        engine.load()
        engine.pause()

        assertEquals(ExecutionStatus.PAUSED, engine.getState().status)
        val summary = engine.runToTerminal()
        assertEquals(ExecutionStatus.PAUSED, summary.status)

        engine.resume()
        assertEquals(ExecutionStatus.EXECUTING, engine.getState().status)
    }

    @Test
    fun `empty plan fails on load`() = runBlocking {
        val planner = GoalPlanner(strategy)
        val emptyGraph = PlanGraph(goalId = "g", goalDescription = "empty", nodes = emptyList())
        val driver = FakeDriver(planner, emptyGraph)

        val summary = engineOf(alwaysOk(), driver).runToTerminal()

        assertEquals(ExecutionStatus.FAILED, summary.status)
        assertTrue(driver.events.any { it is CognitiveEngine.CognitiveEvent.PlanExecutionFailed })
    }

    // ------------------------------------------------------------------
    // Determinism
    // ------------------------------------------------------------------

    @Test
    fun `same plan and port produce the same execution deterministically`() = runBlocking {
        suspend fun runOnce(): ExecutionSummary {
            val driver = FakeDriver(GoalPlanner(strategy), graphOf(strategy))
            return engineOf(alwaysOk(), driver).runToTerminal()
        }
        val s1 = runOnce()
        val s2 = runOnce()

        assertEquals(s1.status, s2.status)
        assertEquals(s1.stepsCompleted, s2.stepsCompleted)
        assertEquals(s1.stepsFailed, s2.stepsFailed)
    }
}
