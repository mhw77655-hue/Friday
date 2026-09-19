package com.jarvis.app.cognitive.execution

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.DecisionOption
import com.jarvis.app.cognitive.Goal
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.cognitive.planning.DecompositionStrategy
import com.jarvis.app.cognitive.planning.GoalPlanner
import com.jarvis.app.cognitive.planning.StepSpec
import com.jarvis.app.cognitive.planning.SubgoalSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CognitiveEngine + ExecutionEngine integration through the SINGLE event bus:
 * execution events land on the existing CognitiveEvent channel, and real
 * step results close the decision-outcome feedback loop.
 */
class ExecutionIntegrationTest {

    private lateinit var scope: CoroutineScope
    private lateinit var engine: CognitiveEngine

    private val strategy = object : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "a"),
            SubgoalSpec("sg2", "b", dependencies = listOf("sg1"))
        )
        override fun decomposeSubgoal(s: SubgoalSpec): List<StepSpec> = when (s.id) {
            "sg1" -> listOf(StepSpec("s1", "do a", "action_a", "done a"))
            "sg2" -> listOf(StepSpec("s1", "do b", "action_b", "done b"))
            else -> emptyList()
        }
    }

    private class FakePort : ActionPort {
        override suspend fun execute(request: ActionRequest): ExecutionResult =
            ExecutionResult.ok(output = mapOf("outcome" to "done"))
    }

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default)
        engine = CognitiveEngine(
            scope = scope,
            memoryStore = FakeMemoryStore(),
            humanCore = HumanCore,
            config = CognitiveEngine.Config(),
            goalPlanner = GoalPlanner(strategy)
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun collector(): Pair<MutableList<CognitiveEngine.CognitiveEvent>, Job> {
        val received =
            java.util.Collections.synchronizedList(mutableListOf<CognitiveEngine.CognitiveEvent>())
        val job = scope.launch { engine.observeEvents().collect { received.add(it) } }
        return received to job
    }

    private fun hasReceived(
        received: MutableList<CognitiveEngine.CognitiveEvent>,
        predicate: (CognitiveEngine.CognitiveEvent) -> Boolean
    ): Boolean = synchronized(received) { received.any(predicate) }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        while (!condition() && System.currentTimeMillis() < deadline) delay(10)
    }

    @Test
    fun `execution events flow through the existing cognitive event bus`() = runBlocking {
        val (received, job) = collector()
        engine.planGoal(Goal("g1", "ship", successCriteria = listOf("done")))

        val execution = engine.startExecution(FakePort())
        val summary = execution.runToTerminal()

        awaitUntil { hasReceived(received) { it is CognitiveEngine.CognitiveEvent.PlanExecutionCompleted } }
        job.cancel()

        assertEquals(ExecutionStatus.COMPLETED, summary.status)
        assertEquals(2, summary.stepsCompleted)
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.ExecutionStarted })
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.StepStarted })
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.StepCompleted })
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.PlanExecutionCompleted })
        assertNotNull(engine.getCurrentPlanGraph())
    }

    @Test
    fun `actual step result closes the decision outcome loop`() = runBlocking {
        engine.planGoal(Goal("g1", "ship", successCriteria = listOf("done")))

        // a decision was made before execution
        engine.evaluateDecision(
            com.jarvis.app.cognitive.planning.DecisionRequest(
                context = "how to ship",
                goal = engine.getCurrentState().currentGoal,
                options = listOf(DecisionOption("a", "go", "ship", 0.2f, 0.3f, 0.9f))
            )
        )
        assertNotNull(engine.getCurrentState().lastDecision)
        assertEquals(
            null,
            engine.getCurrentState().lastDecision!!.outcome
        )

        // execution actually runs and feeds the real result back
        val summary = engine.startExecution(FakePort()).runToTerminal()

        assertEquals(ExecutionStatus.COMPLETED, summary.status)
        assertEquals(
            com.jarvis.app.cognitive.DecisionOutcome.SUCCESS,
            engine.getCurrentState().lastDecision!!.outcome
        )
    }

    /** Minimal in-memory MemoryStorePort fake. */
    private class FakeMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }
}
