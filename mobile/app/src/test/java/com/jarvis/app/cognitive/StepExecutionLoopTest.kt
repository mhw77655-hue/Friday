package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.cognitive.capability.FakeCapability
import com.jarvis.app.cognitive.capability.descriptor
import com.jarvis.app.cognitive.capability.testImmune
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ExecutionResult
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stage 01A — step execution loop: pop subgoals from TaskWorkingMemory,
 * dispatch through the real CapabilityExecutor, record results.
 */
class StepExecutionLoopTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun engine(fabric: CapabilityFabric): CognitiveEngine =
        CognitiveEngine(
            scope = scope,
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore,
            capabilityFabric = fabric
        )

    @Test
    fun runNext_pops_one_subgoal_executes_and_records_result() = kotlinx.coroutines.runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("cap_a", operation = "step a")) {
            com.jarvis.app.cognitive.execution.ExecutionResult.ok(output = mapOf("outcome" to "done a"))
        })

        val e = engine(fabric)
        e.getTaskWorkingMemory().setGoal("test goal")
        e.getTaskWorkingMemory().pushSubgoals(listOf("step a"))

        val result = e.runNext()

        assertNotNull(result)
        assertTrue(result!!.success)
        assertEquals("step a", result.subgoal)

        // Subgoal consumed
        assertNull(e.getTaskWorkingMemory().nextSubgoal())
        assertEquals(1, e.getTaskWorkingMemory().getHistory().size)
    }

    @Test
    fun runNext_returns_null_when_no_subgoals() = kotlinx.coroutines.runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        val e = engine(fabric)

        val result = e.runNext()
        assertNull(result)
    }

    @Test
    fun multi_subgoal_task_executes_in_order_and_completes() = kotlinx.coroutines.runBlocking {
        val callOrder = mutableListOf<String>()
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("cap_a", operation = "step a")) {
            callOrder.add("a")
            com.jarvis.app.cognitive.execution.ExecutionResult.ok(output = mapOf("outcome" to "done a"))
        })
        fabric.register(FakeCapability(descriptor("cap_b", operation = "step b")) {
            callOrder.add("b")
            com.jarvis.app.cognitive.execution.ExecutionResult.ok(output = mapOf("outcome" to "done b"))
        })
        fabric.register(FakeCapability(descriptor("cap_c", operation = "step c")) {
            callOrder.add("c")
            com.jarvis.app.cognitive.execution.ExecutionResult.ok(output = mapOf("outcome" to "done c"))
        })

        val e = engine(fabric)
        e.getTaskWorkingMemory().setGoal("full task")
        e.getTaskWorkingMemory().pushSubgoals(listOf("step a", "step b", "step c"))

        // Execute all three subgoals
        val r1 = e.runNext()
        val r2 = e.runNext()
        val r3 = e.runNext()
        val r4 = e.runNext() // should be null

        assertNotNull(r1)
        assertTrue(r1!!.success)
        assertNotNull(r2)
        assertTrue(r2!!.success)
        assertNotNull(r3)
        assertTrue(r3!!.success)
        assertNull(r4)

        // Execution order matches subgoal order
        assertEquals(listOf("a", "b", "c"), callOrder)

        // History records all three results in order
        val history = e.getTaskWorkingMemory().getHistory()
        assertEquals(3, history.size)
        assertEquals("step a", history[0].subgoal)
        assertEquals("step b", history[1].subgoal)
        assertEquals("step c", history[2].subgoal)
        assertTrue(history.all { it.success })
    }

    @Test
    fun runNext_records_failure_when_capability_fails() = kotlinx.coroutines.runBlocking {
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("cap_fail", operation = "doomed step")) {
            com.jarvis.app.cognitive.execution.ExecutionResult.fail(
                com.jarvis.app.cognitive.execution.ActionFailure.Error("TEST_FAIL", "intentional", recoverable = false)
            )
        })

        val e = engine(fabric)
        e.getTaskWorkingMemory().setGoal("failing task")
        e.getTaskWorkingMemory().pushSubgoals(listOf("doomed step"))

        val result = e.runNext()

        assertNotNull(result)
        assertEquals(false, result!!.success)
        assertEquals("doomed step", result.subgoal)

        val history = e.getTaskWorkingMemory().getHistory()
        assertEquals(1, history.size)
        assertEquals(false, history[0].success)
    }

    @Test
    fun failure_triggers_replan_and_revised_subgoals_execute() = kotlinx.coroutines.runBlocking {
        var replanCallCount = 0
        val e = engineWithReplan(
            fabric = CapabilityFabric(testImmune(), emit = {}).also { fabric ->
                fabric.register(FakeCapability(descriptor("cap_fix", operation = "revised step")) {
                    ExecutionResult.ok(output = mapOf("outcome" to "fixed"))
                })
            },
            complete = { prompt ->
                replanCallCount++
                "1. revised step"
            }
        )
        e.getTaskWorkingMemory().setGoal("replan task")
        e.getTaskWorkingMemory().pushSubgoals(listOf("doomed step"))

        // First run: "doomed step" fails, replan produces "revised step"
        val r1 = e.runNext()
        assertNotNull(r1)
        assertFalse(r1!!.success)

        // Replan happened
        assertEquals(1, replanCallCount)
        assertEquals(1, e.getTaskWorkingMemory().replanCount)

        // Remaining subgoal is the revised one
        val r2 = e.runNext()
        assertNotNull(r2)
        assertTrue(r2!!.success)
        assertEquals("revised step", r2.subgoal)

        // History: original failure + revised success
        val history = e.getTaskWorkingMemory().getHistory()
        assertEquals(2, history.size)
        assertFalse(history[0].success)
        assertTrue(history[1].success)
    }

    @Test
    fun max_replans_exceeded_throws_with_full_history() = kotlinx.coroutines.runBlocking {
        val e = engineWithReplan(
            fabric = CapabilityFabric(testImmune(), emit = {}).also { fabric ->
                fabric.register(FakeCapability(descriptor("cap_always_fail", operation = "flaky step")) {
                    ExecutionResult.fail(ActionFailure.Error("FLaky", "always fails", recoverable = false))
                })
            },
            complete = { _ -> "1. flaky step" } // replan always produces same failing step
        )
        e.maxReplansPerTask = 2
        e.getTaskWorkingMemory().setGoal(" doomed task")
        e.getTaskWorkingMemory().pushSubgoals(listOf("flaky step"))

        // First run: fails, replan 1
        e.runNext()
        // Second run (revised): fails, replan 2
        e.runNext()
        // Third run (revised again): fails, replan budget exhausted
        val ex = assertThrows(ReplanLimitExceededException::class.java) {
            kotlinx.coroutines.runBlocking { e.runNext() }
        }

        assertTrue(ex.history.isNotEmpty())
        assertTrue(ex.message!!.contains("2 replans"))
    }

    @Test
    fun replan_preserves_completed_history() = kotlinx.coroutines.runBlocking {
        val e = engineWithReplan(
            fabric = CapabilityFabric(testImmune(), emit = {}).also { fabric ->
                fabric.register(FakeCapability(descriptor("cap_succeed", operation = "good step")) {
                    ExecutionResult.ok(output = mapOf("outcome" to "ok"))
                })
                fabric.register(FakeCapability(descriptor("cap_fail", operation = "bad step")) {
                    ExecutionResult.fail(ActionFailure.Error("FAIL", "oops", recoverable = false))
                })
                fabric.register(FakeCapability(descriptor("cap_fixed", operation = "fixed step")) {
                    ExecutionResult.ok(output = mapOf("outcome" to "fixed"))
                })
            },
            complete = { _ -> "1. fixed step" }
        )
        e.getTaskWorkingMemory().setGoal("multi step")
        e.getTaskWorkingMemory().pushSubgoals(listOf("good step", "bad step"))

        // Execute "good step" (succeeds)
        val r1 = e.runNext()
        assertTrue(r1!!.success)

        // Execute "bad step" (fails, replan produces "fixed step")
        val r2 = e.runNext()
        assertFalse(r2!!.success)

        // Execute "fixed step" (succeeds)
        val r3 = e.runNext()
        assertTrue(r3!!.success)

        // History preserves all three: success, failure, success
        val history = e.getTaskWorkingMemory().getHistory()
        assertEquals(3, history.size)
        assertTrue(history[0].success)
        assertFalse(history[1].success)
        assertTrue(history[2].success)
        assertEquals("good step", history[0].subgoal)
        assertEquals("bad step", history[1].subgoal)
        assertEquals("fixed step", history[2].subgoal)
    }

    private fun engineWithReplan(
        fabric: CapabilityFabric,
        complete: suspend (String) -> String
    ): CognitiveEngine = CognitiveEngine(
        scope = scope,
        memoryStore = object : MemoryStorePort {
            override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
        },
        humanCore = HumanCore,
        capabilityFabric = fabric,
        decompositionComplete = complete
    )
}
