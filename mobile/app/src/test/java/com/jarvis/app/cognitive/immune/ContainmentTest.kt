package com.jarvis.app.cognitive.immune

import com.jarvis.app.cognitive.ResourceState
import com.jarvis.app.cognitive.ThermalState
import com.jarvis.app.cognitive.planning.GoalPlanner
import com.jarvis.app.cognitive.planning.DecompositionStrategy
import com.jarvis.app.cognitive.planning.StepSpec
import com.jarvis.app.cognitive.planning.SubgoalSpec
import com.jarvis.app.cognitive.Goal
import com.jarvis.app.failure.ContainmentStatus
import com.jarvis.app.failure.DegradationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainmentTest {

    // ────────────────────────────────────────────── DependencyGraph

    @Test
    fun `dependency graph finds direct and transitive dependents`() {
        val graph = DependencyGraph()
        graph.addRequirement("BRAIN", "MEMORY")
        graph.addRequirement("TTS", "MODEL")
        graph.addRequirement("VOICE_INPUT", "AUDIO")

        // BRAIN requires MEMORY; TTS requires MODEL — independent branches.
        assertEquals(listOf("BRAIN"), graph.affectedDependents("MEMORY"))
        assertEquals(listOf("TTS"), graph.affectedDependents("MODEL"))
        assertEquals(listOf("BRAIN"), graph.directDependents("MEMORY").toList())
    }

    @Test
    fun `dependency propagation is transitive`() {
        val graph = DependencyGraph()
        graph.addRequirement("A", "B")
        graph.addRequirement("B", "C")
        graph.addRequirement("D", "C")

        assertEquals(listOf("A", "B", "D"), graph.affectedDependents("C"))
        assertTrue(graph.dependsOn("A", "C"))
        assertFalse(graph.dependsOn("D", "B"))
    }

    @Test
    fun `dependency cycle is detected`() {
        val graph = DependencyGraph()
        graph.addRequirement("A", "B")
        graph.addRequirement("B", "A")
        assertTrue(graph.findCycle() != null)
        assertEquals(null, DependencyGraph().findCycle())
    }

    @Test
    fun `unrelated subsystems are never marked as dependents`() {
        val graph = DependencyGraph()
        graph.addRequirement("BRAIN", "MEMORY")
        // VOICE failure does not touch BRAIN
        assertEquals(emptyList<String>(), graph.affectedDependents("VOICE_INPUT"))
    }

    // ────────────────────────────────────────────── ContainmentRegistry

    @Test
    fun `containment isolates one subsystem while others stay healthy`() {
        val registry = ContainmentRegistry()
        registry.setStatus("STT", ContainmentStatus.ISOLATED)
        assertEquals(ContainmentStatus.ISOLATED, registry.statusOf("STT"))
        assertEquals(ContainmentStatus.HEALTHY, registry.statusOf("BRAIN")) // untouched
        assertTrue(registry.isHealthy("BRAIN"))
        assertFalse(registry.isHealthy("STT"))
    }

    @Test
    fun `containment transition is reported once and is idempotent`() {
        val registry = ContainmentRegistry()
        val first = registry.setStatus("STT", ContainmentStatus.ISOLATED)
        assertTrue(first != null)
        assertEquals(ContainmentStatus.HEALTHY, first!!.first)
        assertEquals(ContainmentStatus.ISOLATED, first.second)

        assertNull(registry.setStatus("STT", ContainmentStatus.ISOLATED)) // no-op
    }

    // ────────────────────────────────────────────── DegradedModeController

    @Test
    fun `degraded mode enters and exits`() {
        val ctl = DegradedModeController()
        assertEquals(DegradationLevel.FULL, ctl.levelOf("TTS"))
        val entered = ctl.enter("TTS", DegradationLevel.LIMITED)
        assertEquals(DegradationLevel.FULL, entered!!.first)
        assertEquals(DegradationLevel.LIMITED, entered.second)
        assertEquals(DegradationLevel.LIMITED, ctl.levelOf("TTS"))

        val exited = ctl.exit("TTS")
        assertEquals(DegradationLevel.LIMITED, exited!!.first)
        assertEquals(DegradationLevel.FULL, exited.second)
        assertEquals(DegradationLevel.FULL, ctl.levelOf("TTS"))
        assertNull(ctl.exit("TTS")) // already full
    }

    // ────────────────────────────────────────────── ResourceFailureHandler

    @Test
    fun `resource response hierarchy escalates with pressure`() {
        val normal = ResourceState(cpuPressure = 0.2f, memoryPressure = 0.2f, batteryLevel = 0.9f)
        assertEquals(ResourceResponse.NORMAL, ResourceFailureHandler.respond(normal))
        assertEquals(DegradationLevel.FULL, ResourceFailureHandler.degradationLevel(normal))

        val cpuBusy = ResourceState(cpuPressure = 0.7f, memoryPressure = 0.2f, batteryLevel = 0.9f)
        assertEquals(ResourceResponse.REDUCE_CONCURRENCY, ResourceFailureHandler.respond(cpuBusy))
        assertEquals(DegradationLevel.DEGRADED, ResourceFailureHandler.degradationLevel(cpuBusy))

        val memoryHeavy = ResourceState(cpuPressure = 0.2f, memoryPressure = 0.75f, batteryLevel = 0.9f)
        assertEquals(ResourceResponse.UNLOAD_EXPENSIVE_CAPABILITY, ResourceFailureHandler.respond(memoryHeavy))
        assertEquals(DegradationLevel.LIMITED, ResourceFailureHandler.degradationLevel(memoryHeavy))

        val hot = ResourceState(cpuPressure = 0.2f, memoryPressure = 0.85f, batteryLevel = 0.9f, thermalState = ThermalState.HOT)
        assertEquals(ResourceResponse.PAUSE, ResourceFailureHandler.respond(hot))
        assertEquals(DegradationLevel.OFFLINE, ResourceFailureHandler.degradationLevel(hot))

        val critical = ResourceState(cpuPressure = 0.2f, memoryPressure = 0.95f, batteryLevel = 0.9f, thermalState = ThermalState.CRITICAL)
        assertEquals(ResourceResponse.REJECT, ResourceFailureHandler.respond(critical))
        assertEquals(DegradationLevel.UNAVAILABLE, ResourceFailureHandler.degradationLevel(critical))

        val lowBattery = ResourceState(cpuPressure = 0.2f, memoryPressure = 0.2f, batteryLevel = 0.1f)
        assertEquals(ResourceResponse.REDUCE_CONCURRENCY, ResourceFailureHandler.respond(lowBattery))
    }

    // ────────────────────────────────────────────── PartialFailureEvaluator

    @Test
    fun `partial failure keeps a usable plan alive`() {
        val graph = tripleStepPlan()
        // A and B succeed, C fails (2/3 success).
        val verdict = PartialFailureEvaluator.evaluate(
            graph,
            failures = mapOf("sg1:s1" to false, "sg2:s1" to false, "sg3:s1" to true),
            usableThreshold = 0.5f,
            acceptableThreshold = 0.5f
        )

        assertEquals(listOf("sg3:s1"), verdict.failedSteps)
        assertEquals(listOf("sg1:s1", "sg2:s1"), verdict.successfulSteps)
        assertTrue(verdict.usable)
        assertTrue(verdict.partialResultAcceptable)
        assertFalse(verdict.needsReplan)
        assertFalse(verdict.alternativeCapabilityExists)
    }

    @Test
    fun `failure of a dependency step blocks downstream`() {
        val graph = tripleStepPlan()
        // middle step fails -> sg3:s1 is blocked (it depends on sg2:s1)
        val verdict = PartialFailureEvaluator.evaluate(graph, failures = mapOf("sg1:s1" to false, "sg2:s1" to true, "sg3:s1" to false))

        assertTrue(verdict.blockedSteps.contains("sg3:s1"))
        assertTrue(verdict.needsReplan)
        assertTrue(verdict.usable) // 2/3 still usable
    }

    private fun tripleStepPlan(): com.jarvis.app.cognitive.planning.PlanGraph {
        val strategy = object : DecompositionStrategy {
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
        return GoalPlanner(strategy).createPlan(Goal("g1", "ship", successCriteria = listOf("done"))).graph
    }
}
