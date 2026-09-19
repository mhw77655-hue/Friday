package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.body.MemoryType
import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CognitiveEngine: the orchestrated cognitive cycle. Covers intent → state →
 * working memory → attention → context, the event/state transitions, the
 * memory activation request, and the empty-input failure case. Uses an
 * in-memory [MemoryStorePort] fake so nothing touches Android or disk.
 */
class CognitiveEngineTest {

    private lateinit var scope: CoroutineScope
    private lateinit var memoryStore: FakeMemoryStore
    private lateinit var engine: CognitiveEngine

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default)
        memoryStore = FakeMemoryStore()
        engine = CognitiveEngine(
            scope = scope,
            memoryStore = memoryStore,
            humanCore = HumanCore,
            config = CognitiveEngine.Config()
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

    private suspend fun collectEvents(): Pair<MutableList<CognitiveEvent>, kotlinx.coroutines.Job> {
        val received = java.util.Collections.synchronizedList(mutableListOf<CognitiveEvent>())
        val job = scope.launch { engine.observeEvents().collect { received.add(it) } }
        return received to job
    }

    @Test
    fun `processInput runs the full cognitive cycle`() = runBlocking {
        val result = engine.processInput("hello jarvis")

        assertEquals(1, result.turnIndex)
        assertEquals(IntentState.GREETING, result.intentResult.finalIntent)
        assertTrue(result.intentResult.confidence > 0f)
        assertNotNull(result.cognitiveContext)
        assertTrue(result.cognitiveContext.withinBudget)
        assertEquals(1L, engine.getCurrentState().turnIndex)
    }

    @Test
    fun `command input updates intent and state`() = runBlocking {
        val result = engine.processInput("jarvis turn on the lights")

        assertEquals(IntentState.COMMAND, result.finalState.currentIntent)
        assertEquals(IntentState.COMMAND, result.intentResult.finalIntent)
        assertTrue(result.finalState.intentConfidence >= 0.7f)
    }

    @Test
    fun `relevant memories are pulled into working memory`() = runBlocking {
        memoryStore.add(
            MemoryItem("body_1", MemoryType.FACT, "User likes coffee",
                System.currentTimeMillis(), listOf("preference", "user"), 0.8f)
        )

        val result = engine.processInput("what do I like to drink?")

        assertTrue(
            result.workingMemorySnapshot.any { it.content.contains("coffee") }
        )
        assertTrue(engine.getCurrentState().activeMemories.isNotEmpty())
    }

    @Test
    fun `attention spotlight is populated and reflected in state`() = runBlocking {
        val result = engine.processInput("jarvis set an alarm for 7am")

        assertTrue(result.attentionSpotlight.items.isNotEmpty())
        assertTrue(engine.getCurrentState().attentionItems.isNotEmpty())
    }

    @Test
    fun `empty input is handled as unknown without crashing`() = runBlocking {
        val result = engine.processInput("   ")

        assertEquals(IntentState.UNKNOWN, result.intentResult.finalIntent)
        assertEquals(0.0f, result.intentResult.confidence, 0.0001f)
        assertTrue(result.cognitiveContext.withinBudget)
    }

    @Test
    fun `goal subgoal and plan transitions update state and emit events`() = runBlocking {
        val (received, job) = collectEvents()

        engine.setGoal(Goal("g1", "Learn Kotlin", GoalPriority.HIGH))
        assertEquals("Learn Kotlin", engine.getCurrentState().currentGoal?.description)

        engine.addSubgoal(Subgoal("sg1", "g1", "Practice basics"))
        assertEquals(1, engine.getCurrentState().activeSubgoals.size)

        engine.updateSubgoal("sg1", SubgoalStatus.COMPLETED)
        assertEquals(SubgoalStatus.COMPLETED, engine.getCurrentState().activeSubgoals[0].status)

        engine.setPlan(Plan("p1", "g1", steps = listOf(
            PlanStep("s1", "Study", action = "Study", expectedOutcome = "Understand")
        )))
        assertEquals("p1", engine.getCurrentState().currentPlan?.id)

        val option = DecisionOption("o1", "Do it", "Success", 0.2f, 0.3f, 0.9f)
        engine.recordDecision(
            DecisionRecord("d1", "test context", listOf(option), option, "test rationale", 0.8f)
        )
        assertEquals("d1", engine.getCurrentState().lastDecision?.id)

        awaitUntil {
            received.any { it is CognitiveEvent.GoalChanged } &&
            received.any { it is CognitiveEvent.SubgoalUpdated } &&
            received.any { it is CognitiveEvent.PlanUpdated } &&
            received.any { it is CognitiveEvent.DecisionMade }
        }
        job.cancel()

        assertTrue(received.any { it is CognitiveEvent.GoalChanged })
        assertTrue(received.any { it is CognitiveEvent.SubgoalUpdated })
        assertTrue(received.any { it is CognitiveEvent.PlanUpdated })
        assertTrue(received.any { it is CognitiveEvent.DecisionMade })
    }

    @Test
    fun `turn completed event is emitted per turn`() = runBlocking {
        val (received, job) = collectEvents()

        engine.processInput("what time is it?")
        awaitUntil { received.any { it is CognitiveEvent.TurnCompleted } }
        job.cancel()

        val turn = received.filterIsInstance<CognitiveEvent.TurnCompleted>().firstOrNull()
        assertNotNull(turn)
        assertEquals("what time is it?", turn!!.userText)
        assertTrue(turn.intentResult.confidence > 0f)
    }

    @Test
    fun `resource pressure flows into a constraint`() = runBlocking {
        engine.updateResourceState(ResourceState(cpuPressure = 0.9f, memoryPressure = 0.8f))

        val result = engine.processInput("run the report now")
        assertTrue(
            result.intentResult.constraints.any { it.type == IntentInference.ConstraintType.RESOURCE }
        )
    }

    @Test
    fun `working memory activation request raises activation`() = runBlocking {
        val wm = engine.getWorkingMemory()
        wm.insert(
            ActiveMemory(
                id = "wm_1", sourceId = "s1", content = "Something important",
                memoryType = MemoryType.FACT, activation = 0.5f,
                relevance = 0.5f, goalAlignment = 0.5f, uncertainty = 0.1f
            )
        )

        val result = wm.activate("wm_1", amount = 0.2f)
        assertTrue(result is WorkingMemory.AccessResult.SUCCESS)
        assertEquals(0.7f, (result as WorkingMemory.AccessResult.SUCCESS).item.activation, 0.0001f)
        assertEquals(WorkingMemory.AccessResult.NOT_FOUND, wm.activate("missing"))
    }

    @Test
    fun `cognitiveEngine instantiation in JarvisEngine pattern succeeds`() {
        val testEngine = CognitiveEngine(
            scope = scope,
            memoryStore = memoryStore,
            humanCore = HumanCore
        )
        assertNotNull(testEngine)
    }

    @Test
    fun `process routes turn through full cognitive cycle and delegates to modelCall`() = runBlocking {
        var calledModelMessage: String? = null
        val turnResult = engine.process("what time is it?", modelCall = { message ->
            calledModelMessage = message
            "It is 10:00 AM."
        })

        assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, turnResult.decision)
        assertEquals("what time is it?", calledModelMessage)
        assertEquals("It is 10:00 AM.", turnResult.responseText)
        assertEquals(1L, engine.getCurrentState().turnIndex)
    }

    @Test
    fun `process supports sendBlock for fire-and-forget bridge send`() = runBlocking {
        var sentMessage: String? = null
        val turnResult = engine.process("hello jarvis", sendBlock = { msg ->
            sentMessage = msg
        })

        assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, turnResult.decision)
        assertEquals("hello jarvis", sentMessage)
        assertEquals(1L, engine.getCurrentState().turnIndex)
    }

    /** In-memory MemoryStorePort fake — no Android Context, no disk. */
    private class FakeMemoryStore(
        private val memories: MutableList<MemoryItem> = mutableListOf()
    ) : MemoryStorePort {

        fun add(item: MemoryItem) {
            memories.add(item)
        }

        override fun queryMemories(query: String, limit: Int): List<MemoryItem> {
            val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
            return memories.filter { mem ->
                val memWords = mem.content.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
                queryWords.isEmpty() ||
                    queryWords.any { q -> memWords.any { m -> q.startsWith(m) || m.startsWith(q) } }
            }.take(limit)
        }
    }
}
