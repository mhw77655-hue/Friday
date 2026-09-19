package com.jarvis.app.cognitive.planning

import com.jarvis.app.cognitive.PlanStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure graph-algorithm coverage for the plan DAG. */
class PlanGraphTest {

    private fun node(id: String, prerequisites: List<String> = emptyList()) =
        PlanNode(id = id, description = id, action = "a", expectedOutcome = "o", prerequisites = prerequisites)

    @Test
    fun `topological order respects chains`() {
        // a -> b -> c, plus d (independent)
        val nodes = listOf(node("c", listOf("b")), node("a"), node("b", listOf("a")), node("d"))
        val order = topologicalOrder(nodes)!!

        assertTrue(order.indexOf("a") < order.indexOf("b"))
        assertTrue(order.indexOf("b") < order.indexOf("c"))
        assertEquals(4, order.size)
    }

    @Test
    fun `topological order is deterministic for equal input`() {
        val nodes = listOf(node("a"), node("b", listOf("a")), node("c", listOf("a")), node("d", listOf("b", "c")))
        assertEquals(topologicalOrder(nodes), topologicalOrder(nodes))
    }

    @Test
    fun `cyclic dependency is detected`() {
        val nodes = listOf(node("a", listOf("b")), node("b", listOf("a")))
        val cycles = findCycles(nodes)
        assertTrue(cycles.isNotEmpty())
        assertNull(topologicalOrder(nodes))
        assertFalse(validateGraph(nodes).valid)
    }

    @Test
    fun `self cycle is detected`() {
        val nodes = listOf(node("a", listOf("a")))
        assertTrue(findCycles(nodes).isNotEmpty())
        assertFalse(validateGraph(nodes).valid)
    }

    @Test
    fun `missing prerequisites are reported`() {
        val nodes = listOf(node("a", listOf("ghost")))
        assertEquals(listOf("ghost"), missingPrerequisites(nodes))
        assertFalse(validateGraph(nodes).valid)
        assertEquals(listOf("ghost"), validateGraph(nodes).missingPrerequisites)
    }

    @Test
    fun `empty plan is invalid`() {
        assertFalse(validateGraph(emptyList()).valid)
    }

    @Test
    fun `blocking propagates through dependencies`() {
        val nodes = listOf(
            node("a"),
            node("b", listOf("a")),
            node("c", listOf("b"))
        )
        val blocked = computeBlocked(nodes)
        assertFalse(blocked.first { it.id == "a" }.blocked)
        assertFalse(blocked.first { it.id == "b" }.blocked)
        assertFalse(blocked.first { it.id == "c" }.blocked)

        // fail "a" -> everything downstream blocks
        val withFailure = nodes.map { if (it.id == "a") it.copy(status = PlanStepStatus.FAILED) else it }
        val recomputed = computeBlocked(withFailure)
        assertTrue(recomputed.first { it.id == "b" }.blocked)
        assertTrue(recomputed.first { it.id == "c" }.blocked)
    }

    @Test
    fun `missing prerequisite blocks the dependent`() {
        val nodes = listOf(node("a", listOf("ghost")))
        assertTrue(computeBlocked(nodes).first().blocked)
    }

    @Test
    fun `next actionable picks first pending with completed prerequisites`() {
        val nodes = listOf(
            node("a"),
            node("b", listOf("a")),
            node("c", listOf("b"))
        )
        assertEquals("a", nextActionable(nodes)!!.id)

        val aDone = nodes.map { if (it.id == "a") it.copy(status = PlanStepStatus.COMPLETED) else it }
        assertEquals("b", nextActionable(aDone)!!.id)
    }

    @Test
    fun `next actionable skips blocked nodes`() {
        val nodes = listOf(
            node("a", listOf("ghost")), // blocked
            node("b")
        )
        assertEquals("b", nextActionable(nodes)!!.id)
    }

    @Test
    fun `progress and completion derive from completed nodes`() {
        val nodes = listOf(node("a"), node("b"))
        val graph = PlanGraph("g", "goal", nodes)
        assertEquals(0, graph.completedCount)
        assertFalse(graph.isComplete)
        assertEquals(0.0f, graph.progress, 0.0001f)

        val done = graph.copy(nodes = nodes.map { it.copy(status = PlanStepStatus.COMPLETED) })
        assertEquals(1.0f, done.progress, 0.0001f)
        assertTrue(done.isComplete)
    }

    @Test
    fun `toPlan projects the graph onto the existing Plan type`() {
        val graph = PlanGraph("g", "goal", listOf(node("a", listOf("b")), node("b")))
        val plan = graph.toPlan("custom_plan")
        assertEquals("custom_plan", plan.id)
        assertEquals("g", plan.goalId)
        assertEquals(2, plan.steps.size)
        val a = plan.steps.first { it.id == "a" }
        assertEquals(listOf("b"), a.dependencies)
    }
}
