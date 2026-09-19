package com.jarvis.app.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskWorkingMemoryTest {

    private lateinit var twm: TaskWorkingMemory

    @Before
    fun setup() {
        twm = TaskWorkingMemory()
    }

    @Test
    fun `setGoal stores and getGoal retrieves the goal`() {
        twm.setGoal("Compile and ship the app")
        assertEquals("Compile and ship the app", twm.getGoal())
    }

    @Test
    fun `nextSubgoal returns null when empty`() {
        assertNull(twm.nextSubgoal())
    }

    @Test
    fun `nextSubgoal pops in FIFO order`() {
        twm.pushSubgoals(listOf("Step A", "Step B", "Step C"))

        assertEquals("Step A", twm.nextSubgoal()!!.description)
        assertEquals("Step B", twm.nextSubgoal()!!.description)
        assertEquals("Step C", twm.nextSubgoal()!!.description)
        assertNull(twm.nextSubgoal())
    }

    @Test
    fun `nextSubgoal returns null once all subgoals consumed`() {
        twm.pushSubgoals(listOf("X", "Y"))
        twm.nextSubgoal()
        twm.nextSubgoal()
        assertNull(twm.nextSubgoal())
    }

    @Test
    fun `recordResult builds queryable execution history`() {
        twm.recordResult("Step A", success = true, detail = "done")
        twm.recordResult("Step B", success = false, detail = "error")

        val history = twm.getHistory()
        assertEquals(2, history.size)
        assertEquals("Step A", history[0].subgoal)
        assertTrue(history[0].success)
        assertEquals("Step B", history[1].subgoal)
        assertEquals("error", history[1].detail)
    }

    @Test
    fun `history is in insertion order`() {
        twm.recordResult("first", success = true)
        twm.recordResult("second", success = true)
        twm.recordResult("third", success = true)

        val history = twm.getHistory()
        assertEquals("first", history[0].subgoal)
        assertEquals("second", history[1].subgoal)
        assertEquals("third", history[2].subgoal)
    }

    @Test
    fun `clear resets everything`() {
        twm.setGoal("something")
        twm.pushSubgoals(listOf("a", "b"))
        twm.recordResult("a", success = true)

        twm.clear()

        assertNull(twm.getGoal())
        assertNull(twm.nextSubgoal())
        assertTrue(twm.getHistory().isEmpty())
    }

    @Test
    fun `three subgoals execute in order and history records all`() {
        twm.setGoal("Full task")
        twm.pushSubgoals(listOf("Subgoal 1", "Subgoal 2", "Subgoal 3"))

        val sg1 = twm.nextSubgoal()!!
        twm.recordResult(sg1.description, success = true, detail = "ok1")

        val sg2 = twm.nextSubgoal()!!
        twm.recordResult(sg2.description, success = true, detail = "ok2")

        val sg3 = twm.nextSubgoal()!!
        twm.recordResult(sg3.description, success = true, detail = "ok3")

        assertNull(twm.nextSubgoal())

        val history = twm.getHistory()
        assertEquals(3, history.size)
        assertEquals("Subgoal 1", history[0].subgoal)
        assertEquals("Subgoal 2", history[1].subgoal)
        assertEquals("Subgoal 3", history[2].subgoal)
        assertTrue(history.all { it.success })
    }

    @Test
    fun `CognitiveEngine exposes TaskWorkingMemory`() {
        val engine = CognitiveEngine(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            memoryStore = object : com.jarvis.app.body.MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<com.jarvis.app.body.MemoryItem>()
            },
            humanCore = com.jarvis.app.humancore.HumanCore
        )
        val twm = engine.getTaskWorkingMemory()
        assertNotNull(twm)
        twm.setGoal("test")
        assertEquals("test", twm.getGoal())
    }
}
