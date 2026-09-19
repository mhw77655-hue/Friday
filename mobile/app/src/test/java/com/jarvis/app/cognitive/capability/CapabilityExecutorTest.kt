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
import com.jarvis.app.cognitive.immune.PartialFailureEvaluator
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

/**
 * 01F — capability-resolved planning & execution, end to end:
 * PlanGraph → resolve → invoke (through the fabric) → plan update →
 * DecisionOutcome → partial-failure verdict → replan. Fake capabilities only;
 * no real capability implementation exists anywhere in this suite.
 */
class CapabilityExecutorTest {

    // -- Plan shapes -------------------------------------------------------

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

    /** sg1 has no capability; its failure must not take down sg3 (independent). */
    private object OneFailedIndependentStrategy : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a"),
            SubgoalSpec("sg3", "c")
        )
        override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> = when (subgoal.id) {
            "sg1" -> listOf(StepSpec("s1", "do a", "compute_a", "done a"))
            "sg3" -> listOf(StepSpec("s1", "do c", "compute_c", "done c"))
            else -> emptyList()
        }
    }

    private fun graphOf(strategy: DecompositionStrategy): PlanGraph =
        GoalPlanner(strategy).createPlan(Goal("g1", "ship", successCriteria = listOf("done"))).graph

    // -- Fakes -------------------------------------------------------------

    private class FakeDriver(
        private val planner: GoalPlanner,
        initialGraph: PlanGraph,
        private val busSink: (CognitiveEvent) -> Unit = {}
    ) : PlanDriver {
        var graph: PlanGraph = initialGraph
        val events = mutableListOf<CognitiveEvent>()
        val recordedOutcomes = mutableListOf<DecisionOutcome>()

        override fun getPlanGraph(): PlanGraph? = graph
        override fun completePlanStep(nodeId: String, outcome: String?): PlanGraph? {
            graph = planner.completeNode(graph, nodeId, outcome); return graph
        }
        override fun failPlanStep(nodeId: String, cause: String): PlanGraph? {
            graph = planner.failNode(graph, nodeId, cause); return graph
        }
        override fun replanPlan(failedNodeId: String, cause: String): PlanGraph? {
            graph = planner.replan(graph, failedNodeId, cause); return graph
        }
        override fun recordDecisionOutcome(outcome: DecisionOutcome) {
            recordedOutcomes.add(outcome)
        }
        override fun emit(event: CognitiveEvent) {
            events.add(event)
            busSink(event)
        }
    }

    private fun makeFabric(caps: List<FakeCapability>): Pair<ImmuneSystem, CapabilityFabric> {
        val immune = testImmune()
        val fabric = CapabilityFabric(immune, emit = {}, defaultTimeoutMs = 500)
        caps.forEach { fabric.register(it) }
        return immune to fabric
    }

    private fun executor(fabric: CapabilityFabric, strategy: DecompositionStrategy): Triple<CapabilityExecutor, FakeDriver, GoalPlanner> {
        val planner = GoalPlanner(strategy)
        val driver = FakeDriver(planner, graphOf(strategy))
        val exec = CapabilityExecutor(fabric, driver, goalPlanner = planner)
        return Triple(exec, driver, planner)
    }

    // -- SUCCESS PATH --------------------------------------------------------

    @Test
    fun `plan steps resolve invoke complete and finish the plan`() = runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("A", operation = "compute_a")))
        fabric.register(FakeCapability(descriptor("B", operation = "compute_b")))
        fabric.register(FakeCapability(descriptor("C", operation = "compute_c")))

        val (exec, driver, _) = executor(fabric, FullStrategy)
        val summary = exec.run()

        assertEquals(3, summary.stepsCompleted)
        assertEquals(0, summary.stepsFailed)
        assertEquals(ExecutionStatus.COMPLETED, summary.status)
        assertTrue(driver.graph.isComplete)
        assertEquals(listOf(DecisionOutcome.SUCCESS, DecisionOutcome.SUCCESS, DecisionOutcome.SUCCESS), driver.recordedOutcomes)
        // each step picked the capability registered for its operation
        assertEquals(listOf("A", "B", "C"), summary.steps.map { it.resolvedCapabilityId })
        // events: started / resolved / invocation / step completed for each step
        assertEquals(3, driver.events.count { it is CognitiveEvent.StepStarted })
        assertEquals(3, driver.events.count { it is CognitiveEvent.StepCompleted })
        assertEquals(3, driver.events.count { it is CognitiveEvent.CapabilityInvocationCompleted })
    }

    @Test
    fun `each step invokes through the fabric and completes the correct node`() = runBlocking {
        val calls = mutableListOf<ActionRequest>()
        val a = FakeCapability(descriptor("A", operation = "compute_a")) { calls.add(it); ExecutionResult.ok(output = mapOf("outcome" to "done a")) }
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(a)

        val (exec, driver, _) = executor(fabric, FullStrategy)
        exec.executeNextStep()

        assertEquals(1, a.calls)
        assertEquals("compute_a", calls.single().action)
        assertEquals("sg1:s1", calls.single().stepId)
        assertEquals("A", exec.state.currentCapabilityId)
        assertTrue(driver.graph.nodes.first { it.id == "sg1:s1" }.isCompleted)
        assertEquals(1, driver.recordedOutcomes.filter { it == DecisionOutcome.SUCCESS }.size)
    }

    // -- RESOLUTION FAILURE ---------------------------------------------------

    @Test
    fun `unknown operation fails the step and blocks dependents but not unrelated steps`() = runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("C", operation = "compute_c")))

        val (exec, driver, _) = executor(fabric, OneFailedIndependentStrategy)
        val summary = exec.run()

        // sg1:s1 unresolved -> step failed; sg3:c independent still ran
        assertEquals(1, summary.stepsFailed)
        assertEquals(1, summary.stepsCompleted)
        val first = summary.steps.first { it.stepId == "sg1:s1" }
        assertTrue((first.resolution as Resolution.Failure).reason == ResolutionReason.OPERATION_NOT_FOUND)
        assertEquals("RESOLUTION_OPERATION_NOT_FOUND", (first.result.failure as ActionFailure.Error).code)
        // the unrelated step is protected: completed, not failed
        assertTrue(driver.graph.nodes.first { it.id == "sg3:s1" }.isCompleted)
        assertEquals(DecisionOutcome.FAILED, driver.recordedOutcomes.first())
        assertEquals(DecisionOutcome.SUCCESS, driver.recordedOutcomes.last())
    }

    @Test
    fun `disabled capability is not invoked`() = runBlocking {
        val cap = FakeCapability(descriptor("A", operation = "compute_a", enabled = false))
        val (_, fabric) = makeFabric(listOf(cap))

        val (exec, _, _) = executor(fabric, FullStrategy)
        val first = exec.executeNextStep()!!

        assertEquals(ResolutionReason.CAPABILITY_DISABLED, (first.resolution as Resolution.Failure).reason)
        assertEquals(0, cap.calls)
        assertEquals("RESOLUTION_CAPABILITY_DISABLED", (first.result.failure as ActionFailure.Error).code)
    }

    @Test
    fun `missing dependency is represented without manufacturing a capability`() = runBlocking {
        // Use a strategy with a step that requires a missing dependency
        val strategy = object : DecompositionStrategy {
            override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
                SubgoalSpec("sg1", "a")
            )
            override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> = when (subgoal.id) {
                "sg1" -> listOf(StepSpec("s1", "do a", "lookup", "done"))
                else -> emptyList()
            }
        }
        // NETWORK capability is absent — CACHE cannot resolve because its
        // dependency is unavailable.
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("CACHE", operation = "lookup", deps = setOf("NETWORK"))))

        val (exec, _, _) = executor(fabric, strategy)
        val first = exec.executeNextStep()!!

        assertEquals(ResolutionReason.DEPENDENCY_UNAVAILABLE, (first.resolution as Resolution.Failure).reason)
        assertNull(first.resolvedCapabilityId)
    }

    @Test
    fun `missing permission is represented`() = runBlocking {
        val cap = FakeCapability(
            descriptor("A", operation = "compute_a", permissions = setOf(CapabilityPermission.NETWORK))
        )
        val (_, fabric) = makeFabric(listOf(cap))

        val (exec, _, _) = executor(fabric, FullStrategy)
        val first = exec.executeNextStep()!!

        assertEquals(ResolutionReason.PERMISSION_DENIED, (first.resolution as Resolution.Failure).reason)
        assertEquals(0, cap.calls)
    }

    @Test
    fun `ambiguous resolution is represented and nothing executes`() = runBlocking {
        val a = FakeCapability(descriptor("A1", operation = "compute_a"))
        val b = FakeCapability(descriptor("A2", operation = "compute_a"))
        val (_, fabric) = makeFabric(listOf(a, b))

        val (exec, _, _) = executor(fabric, FullStrategy)
        val first = exec.executeNextStep()!!

        assertEquals(ResolutionReason.AMBIGUOUS, (first.resolution as Resolution.Failure).reason)
        assertEquals(0, a.calls)
        assertEquals(0, b.calls)
    }

    @Test
    fun `degraded capability reflects immune state`() = runBlocking {
        val cap = FakeCapability(descriptor("A", operation = "compute_a"))
        val (immune, fabric) = makeFabric(listOf(cap))
        // Simulate an immune-declared degradation -> resolver sees OFFLINE.
        immune.degradation.enter("A", DegradationLevel.OFFLINE)

        val (exec, _, _) = executor(fabric, FullStrategy)
        val first = exec.executeNextStep()!!

        assertEquals(ResolutionReason.CAPABILITY_UNAVAILABLE, (first.resolution as Resolution.Failure).reason)
        assertEquals(0, cap.calls)
        assertTrue(immune.surface.recentFailures.value.isEmpty())
    }

    // -- EXECUTION FAILURE ----------------------------------------------------

    @Test
    fun `non-recoverable execution failure reaches the failure surface and is contained`() = runBlocking {
        val cap = FakeCapability(descriptor("A", operation = "compute_a"))
        cap.failWith(ExecutionResult.fail(ActionFailure.Error("X", "boom", recoverable = false)))
        val (immune, fabric) = makeFabric(listOf(cap))

        val (exec, driver, _) = executor(fabric, FullStrategy)
        val first = exec.executeNextStep()!!

        assertFalse(first.success)
        assertEquals(0, exec.state.completedSteps)
        assertEquals("A", first.invocation?.capabilityId)
        assertTrue(driver.graph.nodes.first { it.id == "sg1:s1" }.isFailed)
        assertFalse(first.result.retryable)
        // failure reached the existing FailureSurface
        assertEquals(1, immune.surface.recentFailures.value.size)
        val agg = immune.surface.recentFailures.value.first()
        assertTrue(agg.subsystem == "A" || agg.failureCause != null)
        assertEquals(DecisionOutcome.FAILED, driver.recordedOutcomes.first())
    }

    @Test
    fun `circuit-open refusal is passed through as CIRCUIT_OPEN`() = runBlocking {
        val cap = FakeCapability(descriptor("A", operation = "compute_a"))
        cap.failWith(ExecutionResult.fail(ActionFailure.Error("X", "boom", recoverable = false)))
        val (immune, fabric) = makeFabric(listOf(cap))

        // Hammer until the breaker opens.
        repeat(3) { fabric.invoker.invoke(request(action = "compute_a")) }
        assertTrue(immune.breaker("A", "compute_a").isOpen)

        val (exec, driver, _) = executor(fabric, FullStrategy)
        val first = exec.executeNextStep()!!

        assertTrue(first.invocation!!.refused)
        assertEquals("REFUSED", exec.state.invocationResult)
        assertEquals("CIRCUIT_OPEN", (first.result.failure as ActionFailure.Error).code)
        assertFalse(first.result.retryable)
        // the capability was never called for the step
        assertEquals(3, cap.calls)
        assertTrue(driver.graph.nodes.first { it.id == "sg1:s1" }.isFailed)
    }

    // -- REPLANNING -------------------------------------------------------------

    @Test
    fun `failed step triggers replan and the replacement path executes`() = runBlocking {
        // sg1 fails; strategy proposes a replacement step; sg2/sg3 chain continues.
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("A", operation = "compute_a")) {
            ExecutionResult.fail(ActionFailure.Error("X", "boom", recoverable = false))
        })
        fabric.register(FakeCapability(descriptor("ALT", operation = "compute_alt")))
        fabric.register(FakeCapability(descriptor("B", operation = "compute_b")))
        fabric.register(FakeCapability(descriptor("C", operation = "compute_c")))

        val (exec, driver, _) = executor(fabric, FullStrategy)
        val summary = exec.run()

        assertTrue(summary.replanTriggered)
        // original step failed, replacement ran, downstream still completed
        assertTrue(driver.graph.nodes.first { it.id == "sg1:s1" }.isFailed)
        assertTrue(driver.graph.nodes.any { it.id == "sg1:s1_r0" && it.isCompleted })
        assertTrue(driver.graph.nodes.first { it.id == "sg2:s1" }.isCompleted)
        assertTrue(driver.graph.nodes.first { it.id == "sg3:s1" }.isCompleted)
        assertEquals(1, driver.events.count { it is CognitiveEvent.ReplanTriggered })
        // the replacement step resolved to the ALT capability
        assertTrue(summary.steps.any { it.resolvedCapabilityId == "ALT" })
        assertEquals(DecisionOutcome.FAILED, driver.recordedOutcomes.first())
        assertTrue(driver.recordedOutcomes.drop(1).all { it == DecisionOutcome.SUCCESS })
    }

    @Test
    fun `replan preserves completed prior work`() = runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("A", operation = "compute_a")))
        fabric.register(FakeCapability(descriptor("ALT", operation = "compute_alt")))
        fabric.register(FakeCapability(descriptor("B", operation = "compute_b")) {
            ExecutionResult.fail(ActionFailure.Error("X", "boom", recoverable = false))
        })
        fabric.register(FakeCapability(descriptor("C", operation = "compute_c")))

        val (exec, driver, _) = executor(fabric, FullStrategy)
        val summary = exec.run()

        // sg1 completed BEFORE the sg2 failure — it must stay completed
        assertTrue(driver.graph.nodes.first { it.id == "sg1:s1" }.isCompleted)
        assertEquals(3, summary.stepsCompleted) // successes only: a, alt(replace b), c
        assertTrue(driver.graph.nodes.any { it.id == "sg2:s1_r0" && it.isCompleted })
        assertTrue(driver.graph.nodes.first { it.id == "sg3:s1" }.isCompleted)
    }

    @Test
    fun `replan budget exhausted leads to terminal failure`() = runBlocking {
        // Every capability for every operation fails; strategy proposes a
        // replacement each time — after maxReplans the run terminates FAILED.
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("A", operation = "compute_a")) {
            ExecutionResult.fail(ActionFailure.Error("X", "boom", recoverable = false))
        })

        val (exec, _, _) = executor(fabric, FullStrategy)
        val summary = exec.run()

        assertEquals(ExecutionStatus.FAILED, summary.status)
        assertTrue(summary.replanTriggered)
        assertEquals(3, exec.replanCount) // capped
    }

    // -- PARTIAL RESULTS ---------------------------------------------------------

    @Test
    fun `usable partial result is retained with blocked steps identified`() = runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("A", operation = "compute_a")))
        fabric.register(FakeCapability(descriptor("B", operation = "compute_b")) {
            ExecutionResult.fail(ActionFailure.Error("X", "boom", recoverable = false))
        })
        fabric.register(FakeCapability(descriptor("C", operation = "compute_c")))

        val (exec, driver, _) = executor(fabric, FullStrategy)
        val summary = exec.run()
        val verdict = exec.lastVerdict!!

        // sg1 completed and retained; sg3 blocked (chain via failed sg2)
        assertTrue(driver.graph.nodes.first { it.id == "sg1:s1" }.isCompleted)
        assertTrue(verdict.successfulSteps.contains("sg1:s1"))
        assertTrue(verdict.blockedSteps.contains("sg2:s1"))
        assertTrue(verdict.blockedSteps.contains("sg3:s1"))
        // alternative path exists (the strategy proposes one)
        assertTrue(verdict.alternativeCapabilityExists)
        assertEquals(ExecutionStatus.FAILED, summary.status)
    }

    @Test
    fun `alternative capability is recognized in the verdict`() = runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("B", operation = "compute_b")) {
            ExecutionResult.fail(ActionFailure.Error("X", "boom", recoverable = false))
        })
        fabric.register(FakeCapability(descriptor("ALT", operation = "compute_alt")))

        val (exec, _, _) = executor(fabric, FullStrategy)
        exec.executeNextStep() // a fails (no capability) -> replan happens
        // after replan, lastVerdict reflects the replacement path
        assertNotNull(exec.lastVerdict)
        assertTrue(exec.state.alternativeAvailable)
    }

    // -- INTEGRATION ---------------------------------------------------------------

    @Test
    fun `events flow on the single CognitiveEvent bus end to end`() = runBlocking {
        val events = mutableListOf<CognitiveEvent>()
        val immune = ImmuneSystem(
            surface = FailureSurface(nowMs = { 0L }),
            backoffPolicy = BackoffPolicy(maxAttempts = 1, baseDelayMs = 0, jitterMs = 0),
            breakerFactory = { key -> CircuitBreaker(name = key, openMs = 30_000, nowMs = { 0L }) },
            emit = { events.add(it) }
        )
        val fabric = CapabilityFabric(immune, emit = { events.add(it) })
        fabric.register(FakeCapability(descriptor("A", operation = "compute_a")))

        val planner = GoalPlanner(FullStrategy)
        val driver = FakeDriver(planner, graphOf(FullStrategy), busSink = { events.add(it) })
        val exec = CapabilityExecutor(fabric, driver, goalPlanner = planner)
        exec.executeNextStep()

        assertTrue(events.any { it is CognitiveEvent.StepStarted })
        assertTrue(events.any { it is CognitiveEvent.CapabilityResolved })
        assertTrue(events.any { it is CognitiveEvent.CapabilityInvocationStarted })
        assertTrue(events.any { it is CognitiveEvent.CapabilityInvocationCompleted })
        assertTrue(events.any { it is CognitiveEvent.StepCompleted })
    }

    @Test
    fun `execution state tracks capability resolution and invocation`() = runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("A", operation = "compute_a")))

        val (exec, _, _) = executor(fabric, FullStrategy)
        exec.executeNextStep()

        assertEquals("sg1:s1", exec.state.currentStepId)
        assertEquals("compute_a", exec.state.currentAction)
        assertEquals("A", exec.state.currentCapabilityId)
        assertEquals("AVAILABLE", exec.state.resolutionResult)
        assertEquals("SUCCESS", exec.state.invocationResult)
        assertEquals(1, exec.state.completedSteps)
    }

    // -- DETERMINISM ---------------------------------------------------------------

    @Test
    fun `same plan and capabilities produce the same decision sequence`() = runBlocking {
        fun runOnce(): Triple<ExecutionStatus, List<String>, List<DecisionOutcome>> = runBlocking {
            val fabric = CapabilityFabric(testImmune(), emit = {})
            fabric.register(FakeCapability(descriptor("A", operation = "compute_a")))
            fabric.register(FakeCapability(descriptor("B", operation = "compute_b")))
            fabric.register(FakeCapability(descriptor("C", operation = "compute_c")))
            val planner = GoalPlanner(FullStrategy)
            val driver = FakeDriver(planner, graphOf(FullStrategy))
            val exec = CapabilityExecutor(fabric, driver, goalPlanner = planner)
            val summary = exec.run()
            return@runBlocking Triple(
                summary.status,
                summary.steps.map { "${it.stepId}:${it.resolvedCapabilityId}:${it.success}" },
                driver.recordedOutcomes
            )
        }

        val first = runOnce()
        val second = runOnce()
        assertEquals(first, second)
        assertEquals(ExecutionStatus.COMPLETED, first.first)
    }
}
