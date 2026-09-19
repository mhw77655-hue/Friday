package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.ResourceState
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.cognitive.immune.PartialFailureEvaluator
import com.jarvis.app.cognitive.immune.ResourceFailureHandler
import com.jarvis.app.cognitive.planning.DecompositionStrategy
import com.jarvis.app.cognitive.planning.GoalPlanner
import com.jarvis.app.cognitive.planning.PlanNode
import com.jarvis.app.cognitive.planning.StepSpec
import com.jarvis.app.cognitive.planning.SubgoalSpec
import com.jarvis.app.failure.ContainmentStatus
import com.jarvis.app.failure.DegradationLevel
import com.jarvis.app.failure.FailureCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmuneIntegrationTest {

    private val strategy = object : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a"),
            SubgoalSpec("sg2", "b", dependencies = listOf("sg1")),
            SubgoalSpec("sg3", "c", dependencies = listOf("sg2"))
        )
        override fun decomposeSubgoal(s: SubgoalSpec): List<StepSpec> = when (s.id) {
            "sg1" -> listOf(StepSpec("s1", "do a", "action_a", "done"))
            "sg2" -> listOf(StepSpec("s1", "do b", "action_b", "done"))
            "sg3" -> listOf(StepSpec("s1", "do c", "action_c", "done"))
            else -> emptyList()
        }
    }

    private fun planGraph() =
        GoalPlanner(strategy).createPlan(Goal("g1", "ship", successCriteria = listOf("done"))).graph

    private fun fabric(immune: ImmuneSystem, events: MutableList<CognitiveEvent>) =
        CapabilityFabric(immune, emit = { events.add(it) }, defaultTimeoutMs = 50)

    @Test
    fun `capability failure reaches the failure surface and isolates the capability`() = runBlocking {
        val immune = testImmune()
        val events = mutableListOf<CognitiveEvent>()
        val fabric = fabric(immune, events)
        val broker = FakeCapability(descriptor("BROKER", operation = "broker"))
        broker.failWith(com.jarvis.app.cognitive.execution.ExecutionResult.fail(
            com.jarvis.app.cognitive.execution.ActionFailure.Error("NET", "down")
        ))
        fabric.register(broker)

        fabric.invoker.invoke(request(action = "broker"))

        val failureEvent = immune.surface.recentFailures.value.last()
        assertEquals(FailureCategory.CAPABILITY, failureEvent.category)
        assertEquals("BROKER", failureEvent.subsystem)
        assertEquals(ContainmentStatus.ISOLATED, immune.containment.statusOf("BROKER"))
        assertTrue(events.any { it is CognitiveEvent.CapabilityInvocationFailed })
    }

    @Test
    fun `dependents degrade but unrelated capabilities stay healthy`() = runBlocking {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        fabric.register(FakeCapability(descriptor("BROWSER", operation = "search", deps = setOf("NETWORK"))))
        fabric.register(FakeCapability(descriptor("NETWORK", operation = "fetch")))
        fabric.register(FakeCapability(descriptor("CALC", operation = "compute")))

        // NETWORK fails
        val net = FakeCapability(descriptor("NETWORK", operation = "fetch"))
        net.failWith(com.jarvis.app.cognitive.execution.ExecutionResult.fail(
            com.jarvis.app.cognitive.execution.ActionFailure.Error("NET", "down")
        ))
        // replace NETWORK with the failing one so the failing identity is NETWORK
        fabric.replace("NETWORK", net)
        fabric.invoker.invoke(request(action = "fetch"))

        assertEquals(ContainmentStatus.ISOLATED, immune.containment.statusOf("NETWORK"))
        assertEquals(ContainmentStatus.DEGRADED, immune.containment.statusOf("BROWSER"))
        assertEquals(ContainmentStatus.HEALTHY, immune.containment.statusOf("CALC"))
    }

    @Test
    fun `repeated failures open the circuit and stop further attempts`() = runBlocking {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        val flaky = FakeCapability(descriptor("FLAKY", operation = "flaky"))
        flaky.failWith(com.jarvis.app.cognitive.execution.ExecutionResult.fail(
            com.jarvis.app.cognitive.execution.ActionFailure.Error("X", "boom")
        ))
        fabric.register(flaky)

        val invocations = (1..4).map { fabric.invoker.invoke(request(action = "flaky")) }

        assertTrue(immune.breaker("FLAKY", "flaky").isOpen)
        // 3 real attempts surface; the 4th is refused by the open circuit
        assertEquals(3, immune.surface.recentFailures.value.size)
        assertEquals(3, flaky.calls)
        assertTrue(invocations[3].refused)
    }

    @Test
    fun `successful invocation restores containment and closes the breaker`() = runBlocking {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        val cap = FakeCapability(descriptor("CALC", operation = "compute"))
        cap.failWith(com.jarvis.app.cognitive.execution.ExecutionResult.fail(
            com.jarvis.app.cognitive.execution.ActionFailure.Error("X", "boom")
        ))
        fabric.register(cap)
        fabric.invoker.invoke(request(action = "compute")) // 1 failure
        fabric.invoker.invoke(request(action = "compute")) // 2 failures
        assertEquals(ContainmentStatus.ISOLATED, immune.containment.statusOf("CALC"))

        cap.failWith(com.jarvis.app.cognitive.execution.ExecutionResult.ok())
        fabric.invoker.invoke(request(action = "compute")) // success

        assertEquals(ContainmentStatus.HEALTHY, immune.containment.statusOf("CALC"))
        assertFalse(immune.breaker("CALC", "compute").isOpen)
    }

    @Test
    fun `failed capability does not fail unrelated plan steps - partial result preserved`() = runBlocking {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        val a = FakeCapability(descriptor("CAP_A", operation = "action_a"))
        fabric.register(a)
        val b = FakeCapability(descriptor("CAP_B", operation = "action_b"))
        b.failWith(com.jarvis.app.cognitive.execution.ExecutionResult.fail(
            com.jarvis.app.cognitive.execution.ActionFailure.Error("X", "boom")
        ))
        fabric.register(b)
        val c = FakeCapability(descriptor("CAP_C", operation = "action_c"))
        fabric.register(c)

        val graph = planGraph()
        val invA = fabric.invoker.invoke(request(stepId = "sg1:s1", action = "action_a"))
        val invB = fabric.invoker.invoke(request(stepId = "sg2:s1", action = "action_b"))
        val invC = fabric.invoker.invoke(request(stepId = "sg3:s1", action = "action_c"))

        val verdict = fabric.partialFailureVerdict(graph, listOf(invA, invB, invC))

        assertEquals(listOf("sg2:s1"), verdict.failedSteps)
        assertEquals(listOf("sg1:s1", "sg3:s1"), verdict.successfulSteps)
        assertTrue(verdict.usable) // 2/3 succeeded
        assertTrue(verdict.blockedSteps.contains("sg3:s1")) // depends on the failed step
        assertTrue(verdict.needsReplan)
    }

    @Test
    fun `expensive capability is rejected once degraded under resource pressure`() = runBlocking {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        val expensive = FakeCapability(descriptor(
            "GPU",
            operation = "render",
            profile = CapabilityResourceProfile(cpuCost = 0.9f, latencyClass = LatencyClass.HIGH)
        ))
        fabric.register(expensive)
        assertTrue(expensive.descriptor.resourceProfile.expensive)

        // resource pressure drives the degraded level into the immune controller
        val level = ResourceFailureHandler.degradationLevel(ResourceState(memoryPressure = 0.9f))
        immune.degradation.enter("GPU", level)

        val invocation = fabric.invoker.invoke(request(action = "render"))
        assertFalse(invocation.invoked)
        assertEquals(ResolutionReason.CAPABILITY_UNAVAILABLE, (invocation.resolution as Resolution.Failure).reason)
    }

    @Test
    fun `paused optional capability is not invocable`() = runBlocking {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        fabric.register(FakeCapability(descriptor("OPT", operation = "opt", profile = CapabilityResourceProfile(optional = true))))

        fabric.registry.transitionTo("OPT", CapabilityLifecycle.AVAILABLE)
        fabric.registry.transitionTo("OPT", CapabilityLifecycle.PAUSED)

        val invocation = fabric.invoker.invoke(request(action = "opt"))
        assertFalse(invocation.invoked)
        assertEquals(ResolutionReason.CAPABILITY_UNAVAILABLE, (invocation.resolution as Resolution.Failure).reason)
    }

    @Test
    fun `resource profile classifications`() {
        assertFalse(CapabilityResourceProfile().expensive)
        assertTrue(CapabilityResourceProfile(cpuCost = 0.8f).expensive)
        assertTrue(CapabilityResourceProfile(memoryCostMb = 1_024).expensive)
        assertTrue(CapabilityResourceProfile(latencyClass = LatencyClass.HIGH).expensive)
        assertTrue(CapabilityResourceProfile(optional = true).optional)
        assertFalse(CapabilityResourceProfile(optional = false).optional)
    }

    @Test
    fun `capability registration declares its identity in the immune dependency graph`() {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        fabric.register(FakeCapability(descriptor("BROWSER", operation = "search", deps = setOf("NETWORK"))))

        assertTrue(immune.dependencyGraph.dependsOn("BROWSER", "NETWORK"))
        assertEquals(listOf("BROWSER"), immune.dependencyGraph.affectedDependents("NETWORK"))
    }

    @Test
    fun `duplicate registration is rejected and adds no dependency edges`() {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        fabric.register(FakeCapability(descriptor("BROWSER", operation = "search", deps = setOf("NETWORK"))))
        val dup = fabric.register(FakeCapability(descriptor("BROWSER", operation = "search", deps = setOf("NETWORK"))))

        assertEquals(CapabilityRegistry.RegisterResult.Duplicate("BROWSER"), dup)
        // exactly one edge even though registered twice
        assertEquals(listOf("BROWSER"), immune.dependencyGraph.affectedDependents("NETWORK"))
    }

    @Test
    fun `recovery does not bypass immune policy - open circuit refuses a healthy capability`() = runBlocking {
        val immune = testImmune() // frozen clock -> cooldown never elapses
        val fabric = fabric(immune, mutableListOf())
        val flaky = FakeCapability(descriptor("FLAKY", operation = "flaky"))
        flaky.failWith(com.jarvis.app.cognitive.execution.ExecutionResult.fail(
            com.jarvis.app.cognitive.execution.ActionFailure.Error("X", "boom")
        ))
        fabric.register(flaky)

        (1..3).map { fabric.invoker.invoke(request(action = "flaky")) }
        assertTrue(immune.breaker("FLAKY", "flaky").isOpen)

        // The capability is healthy again, but the circuit is still in
        // cooldown: the invocation boundary must refuse, not bypass immune policy.
        flaky.failWith(com.jarvis.app.cognitive.execution.ExecutionResult.ok())
        val invocation = fabric.invoker.invoke(request(action = "flaky"))

        assertTrue(invocation.refused)
        assertEquals("circuit open", invocation.refusalReason)
        assertEquals(3, flaky.calls) // the 4th call never reached the capability
    }

    @Test
    fun `replacing a capability reconciles its dependency edges in the immune graph`() {
        val immune = testImmune()
        val fabric = fabric(immune, mutableListOf())
        fabric.register(FakeCapability(descriptor("A", operation = "op_a", deps = setOf("B"))))
        assertTrue(immune.dependencyGraph.dependsOn("A", "B"))

        // Replace A with an implementation that depends on C instead — the stale
        // B edge must not survive.
        assertTrue(fabric.replace("A", FakeCapability(descriptor("A", operation = "op_a", deps = setOf("C")))))

        assertFalse(immune.dependencyGraph.dependsOn("A", "B"))
        assertTrue(immune.dependencyGraph.dependsOn("A", "C"))
    }

    @Test
    fun `critical capability receives the correct classification`() {
        assertFalse(CapabilityResourceProfile().critical) // optional by default
        assertFalse(CapabilityResourceProfile(optional = true).critical)
        assertTrue(CapabilityResourceProfile(optional = false).critical)
        assertTrue(CapabilityResourceProfile(optional = false).expensive.not()) // cheap but critical
    }

    @Test
    fun `alternative replan state is represented in the partial failure verdict`() = runBlocking {
        val strategy = object : DecompositionStrategy {
            override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
                SubgoalSpec("sg1", "a"),
                SubgoalSpec("sg2", "b", dependencies = listOf("sg1"))
            )
            override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> = when (subgoal.id) {
                "sg1" -> listOf(StepSpec("s1", "do a", "action_a", "done"))
                "sg2" -> listOf(StepSpec("s1", "do b", "action_b", "done"))
                else -> emptyList()
            }
            override fun proposeReplacement(failed: PlanNode, cause: String): List<StepSpec> =
                listOf(StepSpec("alt", "do alt", "action_alt", "done"))
        }
        val planner = GoalPlanner(strategy)
        val graph = planner.createPlan(Goal("g1", "ship", successCriteria = listOf("done"))).graph
        val replanned = planner.replan(graph, "sg1:s1", "boom")

        val verdict = PartialFailureEvaluator.evaluate(replanned, mapOf("sg1:s1" to true))

        assertTrue(verdict.alternativeCapabilityExists) // graph.replanning != null -> a replacement path exists
        assertTrue(verdict.needsReplan)
        assertTrue(verdict.blockedSteps.contains("sg1:s1"))
        assertFalse(verdict.usable)
    }
}
