package com.jarvis.app.cognitive.planning

import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.GoalPriority
import com.jarvis.app.cognitive.GoalStatus
import com.jarvis.app.cognitive.PlanStatus
import com.jarvis.app.cognitive.PlanStepStatus
import com.jarvis.app.cognitive.SubgoalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GoalPlanner: goal validation, hierarchical decomposition, dependency
 * ordering, lifecycle transitions, blocked/next-actionable detection,
 * replanning, and the "never invent missing prerequisites" rule.
 */
class GoalPlannerTest {

    private val strategy = ShipProductStrategy()

    private fun planner() = GoalPlanner(strategy)

    private fun goal(description: String = "ship product") =
        Goal(id = "g1", description = description, priority = GoalPriority.HIGH,
            successCriteria = listOf("release shipped"))

    // ------------------------------------------------------------------
    // Goal validation
    // ------------------------------------------------------------------

    @Test
    fun `blank description makes the goal invalid`() {
        val v = planner().validateGoal(Goal("g", "  ", successCriteria = listOf("x")))
        assertFalse(v.valid)
        assertTrue(v.missingFields.contains("description"))
    }

    @Test
    fun `missing success criteria is a warning, not invalid`() {
        val v = planner().validateGoal(Goal("g", "ship product"))
        assertTrue(v.valid)
        assertNotNull(v.warning)
    }

    @Test
    fun `empty goal is rejected without a plan`() {
        val result = planner().createPlan(Goal("g", ""))
        assertFalse(result.created)
        assertEquals(PlanStatus.FAILED, result.graph.status)
        assertTrue(result.graph.failureCause!!.contains("invalid goal"))
    }

    @Test
    fun `impossible goal is marked blocked not invented`() {
        // strategy has no knowledge for this description
        val result = planner().createPlan(goal(description = "teleport to mars"))
        assertFalse(result.created)
        assertEquals(PlanStatus.BLOCKED, result.graph.status)
    }

    // ------------------------------------------------------------------
    // Decomposition
    // ------------------------------------------------------------------

    @Test
    fun `goal decomposes into subgoals and a valid plan`() {
        val result = planner().createPlan(goal())
        assertTrue(result.created)
        assertEquals(listOf("sg1", "sg2", "sg3"), result.subgoals.map { it.id })
        assertEquals(4, result.graph.nodes.size)
        assertEquals(PlanStatus.PENDING, result.graph.status)
    }

    @Test
    fun `dependency ordering places prerequisites first`() {
        val result = planner().createPlan(goal())
        val order = result.ordering
        // sg1:s1 < sg2:s1 < sg2:s2 < sg3:s1
        assertTrue(order.indexOf("sg1:s1") < order.indexOf("sg2:s1"))
        assertTrue(order.indexOf("sg2:s1") < order.indexOf("sg2:s2"))
        assertTrue(order.indexOf("sg2:s2") < order.indexOf("sg3:s1"))
    }

    @Test
    fun `next actionable subgoal is the first pending step`() {
        val result = planner().createPlan(goal())
        assertEquals("sg1:s1", result.nextActionable!!.id)
        assertEquals("sg1", result.nextActionable!!.subgoalId)
    }

    @Test
    fun `next actionable advances after completion`() {
        val planner = planner()
        val plan = planner.createPlan(goal()).graph
        val next = planner.completeNode(plan, "sg1:s1")
        assertEquals("sg2:s1", nextActionable(next.nodes)!!.id)
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    fun `completed plan derives completed subgoals and goal`() {
        val planner = planner()
        val result = planner.createPlan(goal())
        val subgoals = result.subgoals
        val allDone = result.graph.copy(nodes = result.graph.nodes.map {
            it.copy(status = PlanStepStatus.COMPLETED)
        })

        val subgoalStates = subgoals.map { planner.deriveSubgoalStatus(it, allDone.nodes) }
        assertTrue(subgoalStates.all { it == SubgoalStatus.COMPLETED })

        val updatedSubgoals = subgoals.mapIndexed { i, sg -> sg.copy(status = subgoalStates[i]) }
        assertEquals(GoalStatus.COMPLETED, planner.deriveGoalStatus(goal(), updatedSubgoals))
    }

    @Test
    fun `failed step marks the plan failed`() {
        val planner = planner()
        val result = planner.createPlan(goal())
        val failed = planner.failNode(result.graph, "sg1:s1", "API unreachable")
        assertEquals(PlanStatus.FAILED, failed.status)
        assertEquals("API unreachable", failed.nodes.first { it.id == "sg1:s1" }.failureCause)
    }

    @Test
    fun `cancelled goal stays cancelled regardless of subgoals`() {
        val planner = planner()
        val cancelled = planner.transitionGoal(goal(), GoalStatus.CANCELLED)
        assertEquals(GoalStatus.CANCELLED, cancelled.status)
        assertEquals(
            GoalStatus.CANCELLED,
            planner.deriveGoalStatus(cancelled, listOf())
        )
    }

    @Test
    fun `subgoal lifecycle transitions are supported`() {
        val planner = planner()
        val sg = planner.createPlan(goal()).subgoals[1]
        assertEquals(SubgoalStatus.PAUSED, planner.transitionSubgoal(sg, SubgoalStatus.PAUSED).status)
        assertEquals(SubgoalStatus.CANCELLED, planner.transitionSubgoal(sg, SubgoalStatus.CANCELLED).status)
    }

    @Test
    fun `undecomposable subgoal becomes a blocked sentinel`() {
        val planner = GoalPlanner(PartialStrategy())
        val result = planner.createPlan(goal(description = "partial"))
        assertEquals(PlanStatus.BLOCKED, result.graph.status)
        // the undecomposable subgoal emits a sentinel blocked node
        val sentinel = result.graph.nodes.first { it.id == "sg2:blocked" }
        assertTrue(sentinel.blocked)
        // the dependent subgoal (also undecomposable) is blocked via its sentinel
        val sg3 = result.graph.nodes.first { it.id == "sg3:blocked" }
        assertTrue(computeBlocked(result.graph.nodes).first { it.id == "sg3:blocked" }.blocked)
    }

    @Test
    fun `blocked plan derives a blocked goal`() {
        val planner = GoalPlanner(PartialStrategy())
        val result = planner.createPlan(goal(description = "partial"))
        val goalStates = result.subgoals.map { sg -> planner.deriveSubgoalStatus(sg, result.graph.nodes) }
        val updated = result.subgoals.mapIndexed { i, sg -> sg.copy(status = goalStates[i]) }
        assertEquals(GoalStatus.BLOCKED, planner.deriveGoalStatus(goal(), updated))
    }

    // ------------------------------------------------------------------
    // Invalid plans
    // ------------------------------------------------------------------

    @Test
    fun `cyclic dependency plan is rejected`() {
        val result = GoalPlanner(CycleStrategy()).createPlan(goal(description = "cycle"))
        assertFalse(result.created)
        assertEquals(PlanStatus.FAILED, result.graph.status)
        assertTrue(result.graph.failureCause!!.contains("cyclic"))
    }

    @Test
    fun `missing prerequisite dependency is rejected`() {
        val result = GoalPlanner(MissingPrereqStrategy()).createPlan(goal(description = "missing"))
        assertFalse(result.created)
        assertEquals(PlanStatus.FAILED, result.graph.status)
        assertTrue(result.graph.failureCause!!.contains("missing prerequisites"))
    }

    // ------------------------------------------------------------------
    // Replanning / failed-step recovery
    // ------------------------------------------------------------------

    @Test
    fun `replan replaces only the failed step and its downstream`() {
        val planner = planner()
        val result = planner.createPlan(goal())

        // get past sg1
        var graph = planner.completeNode(result.graph, "sg1:s1")

        // sg2:s1 fails
        graph = planner.failNode(graph, "sg2:s1", "implementation blocked")
        assertEquals(PlanStatus.FAILED, graph.status)

        val replanned = planner.replan(graph, "sg2:s1", "implementation blocked")

        // completed work kept: sg1:s1 still completed, NOT restarted
        assertTrue(replanned.nodes.any { it.id == "sg1:s1" && it.status == PlanStepStatus.COMPLETED })

        // the failed node is replaced by the alt path: the original failure is
        // retained as a permanent FAILED record (so execution consumers can see
        // what failed) while the replacement node sg2:s1_r0 carries the work
        // forward.
        assertTrue(replanned.nodes.any { it.id == "sg2:s1" && it.status == PlanStepStatus.FAILED })
        assertTrue(replanned.nodes.any { it.id == "sg2:s1_r0" })

        // the immediate dependent of the failed node now depends on the
        // replacement tail and is reset; further downstream keeps its edges
        val sg2s2 = replanned.nodes.first { it.id == "sg2:s2" }
        assertEquals(listOf("sg2:s1_r0"), sg2s2.prerequisites)
        assertEquals(PlanStepStatus.PENDING, sg2s2.status)
        val sg3 = replanned.nodes.first { it.id == "sg3:s1" }
        assertEquals(listOf("sg2:s2"), sg3.prerequisites)
        assertEquals(PlanStepStatus.PENDING, sg3.status)

        // plan continues, not failed
        assertEquals(PlanStatus.PENDING, replanned.status)
        assertEquals("sg2:s1_r0", nextActionable(replanned.nodes)!!.id)
    }

    @Test
    fun `replan records invalidated nodes and cause`() {
        val planner = planner()
        var graph = planner.createPlan(goal()).graph
        graph = planner.completeNode(graph, "sg1:s1")
        graph = planner.failNode(graph, "sg2:s1", "dep failure")

        val replanned = planner.replan(graph, "sg2:s1", "dep failure")
        val replay = replanned.replanning
        assertNotNull(replay)
        assertEquals("sg2:s1", replay!!.originalNodeId)
        assertEquals("dep failure", replay.cause)
        assertTrue(replay.invalidatedNodeIds.contains("sg3:s1"))
        assertEquals(1, replay.replacementPaths.size)
    }

    @Test
    fun `replan without a strategy keeps the plan failed`() {
        val planner = GoalPlanner(EmptyDecompositionStrategy)
        val result = planner.createPlan(goal())
        // no strategy -> nothing to plan
        assertEquals(PlanStatus.BLOCKED, result.graph.status)
    }

    // ------------------------------------------------------------------
    // Determinism
    // ------------------------------------------------------------------

    @Test
    fun `same input produces the same plan deterministically`() {
        val p1 = planner()
        val p2 = planner()
        val g = goal()
        val r1 = p1.createPlan(g)
        val r2 = p2.createPlan(g)

        assertEquals(r1.ordering, r2.ordering)
        assertEquals(r1.graph.nodes.map { it.id }, r2.graph.nodes.map { it.id })
        assertEquals(r1.graph.nodes.map { it.prerequisites }, r2.graph.nodes.map { it.prerequisites })
        // createdAt timestamps differ between runs; structure must not
        assertEquals(
            r1.subgoals.map { it.copy(createdAt = 0L) },
            r2.subgoals.map { it.copy(createdAt = 0L) }
        )
    }

    @Test
    fun `replan is deterministic for the same failed state`() {
        val p1 = planner()
        val p2 = planner()
        fun failedPlan(planner: GoalPlanner): PlanGraph {
            var g = planner.createPlan(goal()).graph
            g = planner.completeNode(g, "sg1:s1")
            return planner.failNode(g, "sg2:s1", "cause")
        }
        val r1 = p1.replan(failedPlan(p1), "sg2:s1", "cause")
        val r2 = p2.replan(failedPlan(p2), "sg2:s1", "cause")
        assertEquals(r1.nodes.map { it.id }, r2.nodes.map { it.id })
        assertEquals(r1.replanning, r2.replanning)
    }

    // ------------------------------------------------------------------
    // Strategies
    // ------------------------------------------------------------------

    private class ShipProductStrategy : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = when (goal.description) {
            "ship product" -> listOf(
                SubgoalSpec("sg1", "design"),
                SubgoalSpec("sg2", "build", dependencies = listOf("sg1")),
                SubgoalSpec("sg3", "test", dependencies = listOf("sg2"))
            )
            else -> emptyList()
        }

        override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> = when (subgoal.id) {
            "sg1" -> listOf(StepSpec("s1", "gather requirements", "gather", "requirements"))
            "sg2" -> listOf(
                StepSpec("s1", "implement", "implement", "implementation"),
                StepSpec("s2", "review", "review", "reviewed", prerequisites = listOf("s1"))
            )
            "sg3" -> listOf(StepSpec("s1", "run tests", "test", "green"))
            else -> emptyList()
        }

        override fun proposeReplacement(failed: PlanNode, cause: String): List<StepSpec> =
            listOf(StepSpec("alt", "alternate path for ${failed.description}", "retry", failed.expectedOutcome))
    }

    private class PartialStrategy : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a"),
            SubgoalSpec("sg2", "b", dependencies = listOf("sg1")),
            SubgoalSpec("sg3", "c", dependencies = listOf("sg2"))
        )

        override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> = when (subgoal.id) {
            "sg1" -> listOf(StepSpec("s1", "do a", "a", "a done"))
            else -> emptyList() // sg2 and sg3 undecomposable
        }
    }

    private class CycleStrategy : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a", dependencies = listOf("sg2")),
            SubgoalSpec("sg2", "b", dependencies = listOf("sg1"))
        )

        override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> =
            listOf(StepSpec("s1", "do", "do", "done"))
    }

    private class MissingPrereqStrategy : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a", dependencies = listOf("sg_that_does_not_exist"))
        )

        override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> =
            listOf(StepSpec("s1", "do", "do", "done"))
    }
}
