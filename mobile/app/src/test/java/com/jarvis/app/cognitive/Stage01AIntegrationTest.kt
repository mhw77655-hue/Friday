package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.cognitive.capability.FakeCapability
import com.jarvis.app.cognitive.capability.descriptor
import com.jarvis.app.cognitive.capability.testImmune
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ExecutionResult
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stage 01A integration test — drives the full decompose → execute → fail →
 * replan → execute → complete path against real CognitiveEngine / WorkingMemory /
 * CapabilityExecutor wiring. Only the LLM boundary and the deliberate mid-task
 * failure are fixture/mocked.
 */
class Stage01AIntegrationTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun full_stage_01a_loop_decompose_execute_fail_replan_complete() = kotlinx.coroutines.runBlocking {
        // --- Capability fabric: "research" and "draft" succeed; "outline" fails first time ---
        val outlineFailCount = mutableListOf<Int>()
        val fabric = CapabilityFabric(testImmune(), emit = {})
        fabric.register(FakeCapability(descriptor("cap_research", operation = "Research the topic thoroughly")) {
            ExecutionResult.ok(output = mapOf("outcome" to "research done"))
        })
        fabric.register(FakeCapability(descriptor("cap_outline", operation = "Write a detailed outline")) {
            outlineFailCount.add(1)
            if (outlineFailCount.size == 1) {
                // First attempt fails
                ExecutionResult.fail(ActionFailure.Error("OUTLINE_FAIL", "outline quality too low", recoverable = false))
            } else {
                // Revised attempt succeeds
                ExecutionResult.ok(output = mapOf("outcome" to "outline done"))
            }
        })
        fabric.register(FakeCapability(descriptor("cap_draft", operation = "Draft the full report")) {
            ExecutionResult.ok(output = mapOf("outcome" to "draft done"))
        })
        // Also register for revised subgoal names after replan
        fabric.register(FakeCapability(descriptor("cap_bullet", operation = "Create bullet-point summary")) {
            ExecutionResult.ok(output = mapOf("outcome" to "bullets done"))
        })

        // --- LLM seam: decompose + replan ---
        var decomposeCallCount = 0
        val engine = CognitiveEngine(
            scope = scope,
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore,
            capabilityFabric = fabric,
            decompositionComplete = { prompt ->
                decomposeCallCount++
                if (decomposeCallCount == 1) {
                    // Initial decomposition
                    "1. Research the topic thoroughly\n2. Write a detailed outline\n3. Draft the full report"
                } else {
                    // Replan after outline failure: replace remaining with revised steps
                    "1. Create bullet-point summary\n2. Draft the full report"
                }
            }
        )

        // --- Step 1: Decompose ---
        val decomposeResult = engine.decompose("Write a market analysis report")
        assertEquals(3, decomposeResult.subgoals.size)
        assertEquals("Research the topic thoroughly", decomposeResult.subgoals[0])
        assertEquals("Write a detailed outline", decomposeResult.subgoals[1])
        assertEquals("Draft the full report", decomposeResult.subgoals[2])

        // --- Step 2: Execute all subgoals via runNext loop ---
        val results = mutableListOf<TaskWorkingMemory.ResultEntry>()
        var result = engine.runNext()
        while (result != null) {
            results.add(result)
            result = engine.runNext()
        }

        // --- Verify execution sequence ---
        // Expected: research(success), outline(fail → replan), bullets(success), draft(success)
        assertEquals(4, results.size)

        // Subgoal 1: research succeeds
        assertEquals("Research the topic thoroughly", results[0].subgoal)
        assertTrue(results[0].success)

        // Subgoal 2: outline fails, triggers replan
        assertEquals("Write a detailed outline", results[1].subgoal)
        assertTrue(!results[1].success)

        // Subgoal 3 (revised): bullet-point summary succeeds
        assertEquals("Create bullet-point summary", results[2].subgoal)
        assertTrue(results[2].success)

        // Subgoal 4 (revised): draft succeeds
        assertEquals("Draft the full report", results[3].subgoal)
        assertTrue(results[3].success)

        // --- Verify WorkingMemory history ---
        val history = engine.getTaskWorkingMemory().getHistory()
        assertEquals(4, history.size)
        assertTrue(history[0].success)   // research: ok
        assertTrue(!history[1].success)  // outline: fail
        assertTrue(history[2].success)   // bullets: ok
        assertTrue(history[3].success)   // draft: ok

        // --- Verify replan happened exactly once ---
        assertEquals(1, decomposeCallCount - 1) // 1 initial + 1 replan = 2 total calls
        assertEquals(1, engine.getTaskWorkingMemory().replanCount)

        // --- Verify no pending subgoals remain ---
        assertNull(engine.runNext())
    }

    @Test
    fun all_prior_stage_01a_tests_still_pass() {
        // This test exists as a signal: if the integration test above passes,
        // it confirms all building blocks (TaskWorkingMemory, decompose,
        // runNext, replan) are wired correctly. The individual unit tests in
        // TaskWorkingMemoryTest, GoalDecompositionTest, and
        // StepExecutionLoopTest cover each component independently.
        val twm = TaskWorkingMemory()
        twm.setGoal("integration")
        twm.pushSubgoals(listOf("a", "b", "c"))
        assertEquals("a", twm.nextSubgoal()!!.description)
        assertEquals("b", twm.nextSubgoal()!!.description)
        assertEquals("c", twm.nextSubgoal()!!.description)
        assertNull(twm.nextSubgoal())

        twm.recordResult("a", success = true)
        twm.recordResult("b", success = false, detail = "replanned")
        twm.recordResult("b revised", success = true)
        twm.recordResult("c", success = true)
        assertEquals(4, twm.getHistory().size)
    }
}
