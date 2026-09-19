package com.jarvis.app.cognitive.planning

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.body.MemoryType
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.DecisionOption
import com.jarvis.app.cognitive.DecisionOutcome
import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.GoalStatus
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CognitiveEngine + GoalPlanner + DecisionEngine integration: events emitted
 * and state updated without any runtime wiring.
 */
class CognitivePlanningIntegrationTest {

    private lateinit var scope: CoroutineScope
    private lateinit var engine: CognitiveEngine

    private val strategy = object : DecompositionStrategy {
        override fun decomposeGoal(goal: Goal): List<SubgoalSpec> = listOf(
            SubgoalSpec("sg1", "design"),
            SubgoalSpec("sg2", "build", dependencies = listOf("sg1"))
        )

        override fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> = when (subgoal.id) {
            "sg1" -> listOf(StepSpec("s1", "gather", "gather", "requirements"))
            "sg2" -> listOf(StepSpec("s1", "implement", "implement", "impl"))
            else -> emptyList()
        }

        override fun proposeReplacement(failed: PlanNode, cause: String): List<StepSpec> =
            listOf(StepSpec("alt", "alternate", "retry", failed.expectedOutcome))
    }

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default)
        engine = CognitiveEngine(
            scope = scope,
            memoryStore = FakeMemoryStore(),
            humanCore = HumanCore,
            config = CognitiveEngine.Config(),
            goalPlanner = GoalPlanner(strategy),
            decisionEngine = DecisionEngine()
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            delay(10)
        }
    }

    private fun collector(): Pair<MutableList<CognitiveEngine.CognitiveEvent>, kotlinx.coroutines.Job> {
        val received =
            java.util.Collections.synchronizedList(mutableListOf<CognitiveEngine.CognitiveEvent>())
        val job = scope.launch { engine.observeEvents().collect { received.add(it) } }
        return received to job
    }

    private fun hasReceived(
        received: MutableList<CognitiveEngine.CognitiveEvent>,
        predicate: (CognitiveEngine.CognitiveEvent) -> Boolean
    ): Boolean = synchronized(received) { received.any(predicate) }

    private fun goal() = Goal("g1", "ship", successCriteria = listOf("released"))

    // ------------------------------------------------------------------

    @Test
    fun `planGoal decomposes and emits goal subgoal and plan events`() = runBlocking {
        val (received, job) = collector()
        val result = engine.planGoal(goal())

        assertTrue(result.created)
        assertEquals(2, result.subgoals.size)
        assertNotNull(engine.getCurrentPlanGraph())

        // Wait for ALL three expected events before cancelling the collector —
        // cancelling on the first event raced with the later emissions and made
        // this test flaky under full-suite scheduling.
        awaitUntil {
            hasReceived(received) { it is CognitiveEngine.CognitiveEvent.GoalCreated } &&
                hasReceived(received) { it is CognitiveEngine.CognitiveEvent.SubgoalCreated } &&
                hasReceived(received) { it is CognitiveEngine.CognitiveEvent.PlanCreated }
        }
        job.cancel()

        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.GoalCreated })
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.SubgoalCreated })
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.PlanCreated })
        assertEquals("ship", engine.getCurrentState().currentGoal!!.description)
        assertEquals(2, engine.getCurrentState().currentPlan!!.steps.size)
    }

    @Test
    fun `completing every step completes the plan and the goal`() = runBlocking {
        val (received, job) = collector()
        engine.planGoal(goal())

        engine.completePlanStep("sg1:s1")
        engine.completePlanStep("sg2:s1")

        awaitUntil { hasReceived(received) { it is CognitiveEngine.CognitiveEvent.PlanCompleted } }
        job.cancel()

        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.PlanCompleted })
        assertEquals(GoalStatus.COMPLETED, engine.getCurrentState().currentGoal!!.status)
        assertTrue(engine.getCurrentPlanGraph()!!.isComplete)
    }

    @Test
    fun `failing a step fails the plan`() = runBlocking {
        val (received, job) = collector()
        engine.planGoal(goal())
        engine.failPlanStep("sg1:s1", "requirements unavailable")

        awaitUntil { hasReceived(received) { it is CognitiveEngine.CognitiveEvent.PlanFailed } }
        job.cancel()

        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.PlanFailed })
        assertEquals(GoalStatus.FAILED, engine.getCurrentState().currentGoal!!.status)
    }

    @Test
    fun `replan recovers from a failed step without restarting`() = runBlocking {
        val (received, job) = collector()
        engine.planGoal(goal())

        engine.completePlanStep("sg1:s1")
        engine.failPlanStep("sg2:s1", "api down")
        engine.replanPlan("sg2:s1", "api down")

        awaitUntil { hasReceived(received) { it is CognitiveEngine.CognitiveEvent.ReplanRequested } }
        job.cancel()

        val graph = engine.getCurrentPlanGraph()!!
        // completed work kept, replacement path present
        assertTrue(graph.nodes.any { it.id == "sg1:s1" && it.isCompleted })
        assertTrue(graph.nodes.any { it.id == "sg2:s1_r0" })
        // the failed node is retained as a permanent FAILED record, not
        // restarted and not silently dropped
        assertTrue(graph.nodes.any { it.id == "sg2:s1" && it.isFailed })
        assertNotNull(graph.replanning)
    }

    @Test
    fun `updateGoalStatus emits GoalChanged`() = runBlocking {
        val (received, job) = collector()
        engine.planGoal(goal())
        engine.updateGoalStatus(GoalStatus.PAUSED)

        awaitUntil { hasReceived(received) { it is CognitiveEngine.CognitiveEvent.GoalChanged } }
        job.cancel()

        assertEquals(GoalStatus.PAUSED, engine.getCurrentState().currentGoal!!.status)
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.GoalChanged })
    }

    @Test
    fun `evaluateDecision proposes options and records the chosen one`() = runBlocking {
        val (received, job) = collector()
        val result = engine.evaluateDecision(
            DecisionRequest(
                context = "pick framework",
                goal = goal(),
                options = listOf(
                    DecisionOption("a", "use A", "works", 0.2f, 0.3f, 0.9f),
                    DecisionOption("b", "use B", "works", 0.5f, 0.3f, 0.4f)
                )
            )
        )

        assertTrue(result.made)
        awaitUntil { hasReceived(received) { it is CognitiveEngine.CognitiveEvent.DecisionMade } }
        job.cancel()

        assertEquals("a", result.chosen!!.id)
        assertEquals("a", engine.getCurrentState().lastDecision!!.chosen.id)
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.DecisionProposed })
        assertTrue(hasReceived(received) { it is CognitiveEngine.CognitiveEvent.DecisionMade })
        assertNotNull(engine.getCurrentState().lastDecision!!.reasonMetadata)
    }

    @Test
    fun `recordDecisionOutcome attaches the actual outcome later`() = runBlocking {
        engine.evaluateDecision(
            DecisionRequest(
                context = "deploy",
                goal = goal(),
                options = listOf(DecisionOption("a", "deploy now", "shipped", 0.3f, 0.4f, 0.8f))
            )
        )
        assertNull(engine.getCurrentState().lastDecision!!.outcome)

        engine.recordDecisionOutcome(DecisionOutcome.SUCCESS)
        assertEquals(
            DecisionOutcome.SUCCESS,
            engine.getCurrentState().lastDecision!!.outcome
        )
    }

    /** Minimal in-memory MemoryStorePort fake. */
    private class FakeMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }
}
