package com.jarvis.app.cognitive

import com.jarvis.app.cognitive.IntentInference.ContextSignal
import com.jarvis.app.cognitive.IntentInference.InferredIntent
import com.jarvis.app.cognitive.IntentInference.IntentInferenceResult
import com.jarvis.app.cognitive.IntentInference.ExplicitIntent
import com.jarvis.app.cognitive.IntentInference.AmbiguityAssessment
import com.jarvis.app.cognitive.IntentInference.AmbiguityLevel
import com.jarvis.app.body.MemoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CognitiveContextBuilder: compact, budgeted context. Covers the required
 * sections (frame, goals, attention, memory, self/user/world, uncertainty),
 * the "do NOT dump all history" cap, and the overload failure case.
 */
class CognitiveContextBuilderTest {

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun scope() = CoroutineScope(Dispatchers.Default).also { scopes.add(it) }

    private fun builder(maxTokens: Int = 1000) = CognitiveContextBuilder(
        scope = scope(),
        config = CognitiveContextBuilder.Config(maxContextTokens = maxTokens)
    )

    private fun wm(maxItems: Int = 20) = WorkingMemory(
        scope = scope(),
        config = WorkingMemory.Config(maxItems = maxItems)
    )

    private fun attention() = AttentionEngine(scope = scope())

    private fun mem(id: String, content: String, activation: Float = 0.5f) = ActiveMemory(
        id = id, sourceId = "s_$id", content = content, memoryType = MemoryType.FACT,
        activation = activation, relevance = 0.5f, goalAlignment = 0.5f, uncertainty = 0.1f
    )

    private fun intentResult(
        finalIntent: IntentState = IntentState.CONVERSATION,
        constraints: List<IntentInference.Constraint> = emptyList(),
        unknowns: List<IntentInference.Unknown> = emptyList()
    ) = IntentInferenceResult(
        explicitIntent = ExplicitIntent(finalIntent, 0.5f, "test", emptyList()),
        inferredIntent = InferredIntent(IntentState.UNKNOWN, 0.0f, emptyList(), ""),
        finalIntent = finalIntent,
        confidence = 0.5f,
        constraints = constraints,
        ambiguity = AmbiguityAssessment(false, AmbiguityLevel.NONE, emptyList(), 0.0f, null),
        unknowns = unknowns,
        timestamp = System.currentTimeMillis()
    )

    @Test
    fun `frame section is always first and included`() = runBlocking {
        val state = CognitiveState(turnIndex = 1, currentIntent = IntentState.QUESTION, intentConfidence = 0.8f)
        val context = builder().build(state, intentResult(), wm(), attention().getSpotlight())

        assertEquals("frame", context.sections.first().name)
        assertTrue(context.sections.first().included)
        assertTrue(context.text.contains("COGNITIVE FRAME"))
        assertTrue(context.text.contains("Turn: 1"))
        assertTrue(context.withinBudget)
    }

    @Test
    fun `goals section includes goal subgoals and next plan step`() = runBlocking {
        val state = CognitiveState().copyWith(
            currentGoal = Goal("g1", "Finish the project", GoalPriority.HIGH),
            activeSubgoals = listOf(
                Subgoal("sg1", "g1", "Write docs"),
                Subgoal("sg2", "g1", "Run tests", status = SubgoalStatus.IN_PROGRESS)
            ),
            currentPlan = Plan(
                id = "p1", goalId = "g1",
                steps = listOf(PlanStep("ps1", "Read docs", action = "Read", expectedOutcome = "Understand"))
            )
        )

        val context = builder().build(state, intentResult(), wm(), attention().getSpotlight())
        val goals = context.sections.find { it.name == "goals" }
        assertNotNull(goals)
        assertTrue(goals!!.content.contains("Finish the project"))
        assertTrue(goals.content.contains("HIGH"))
        assertTrue(goals.content.contains("Write docs"))
        assertTrue(goals.content.contains("Read → Understand"))
    }

    @Test
    fun `attention spotlight appears in context`() = runBlocking {
        val attn = attention()
        attn.submit(
            AttentionItem("a1", "User asked about weather", AttentionSource.USER_INPUT, 0.8f, 0.7f, 0.6f, 1.0f)
        )

        val context = builder().build(CognitiveState(), intentResult(), wm(), attn.getSpotlight())
        assertTrue(context.text.contains("ATTENTION SPOTLIGHT"))
        assertTrue(context.text.contains("User asked about weather"))
    }

    @Test
    fun `working memory section lists only the top activated memories`() = runBlocking {
        val memory = wm(maxItems = 30)
        (1..20).forEach { i -> memory.insert(mem("m$i", "Memory item number $i", activation = 1.0f - i * 0.02f)) }

        val context = builder().build(CognitiveState(), intentResult(), memory, attention().getSpotlight())
        val memSection = context.sections.find { it.name == "memory" }
        assertNotNull(memSection)
        // Top 15 of the 20 (maxActiveMemories cap) — not all of history
        assertTrue(context.text.contains("WORKING MEMORY (top 15)"))
        assertTrue(context.text.contains("Memory item number 1"))
        assertFalse(context.text.contains("Memory item number 16"))
    }

    @Test
    fun `constraints and unknowns appear when present`() = runBlocking {
        val result = intentResult(
            constraints = listOf(
                IntentInference.Constraint(IntentInference.ConstraintType.TIME, "Do it now", 0.9f)
            ),
            unknowns = listOf(
                IntentInference.Unknown(IntentInference.UnknownType.REFERENT, "Unknown referent for it", 0.6f)
            )
        )

        val context = builder().build(CognitiveState(), result, wm(), attention().getSpotlight())
        assertTrue(context.text.contains("Do it now"))
        assertTrue(context.text.contains("Unknown referent"))
    }

    @Test
    fun `self and user model sections are included`() = runBlocking {
        val state = CognitiveState().copyWith(
            selfState = SelfModel(identityName = "JARVIS", selfAssessment = SelfAssessment(cognitiveLoad = 0.3f)),
            userState = UserModel(
                knownName = "Alex",
                relationshipDepth = 0.7f,
                currentContext = UserContext(recentTopics = listOf("kotlin", "android")),
                predictedNeeds = listOf("code review")
            )
        )

        val context = builder().build(state, intentResult(), wm(), attention().getSpotlight())
        assertTrue(context.sections.any { it.name == "self" && it.included })
        assertTrue(context.sections.any { it.name == "user" && it.included })
        assertTrue(context.text.contains("Alex"))
        assertTrue(context.text.contains("kotlin"))
    }

    @Test
    fun `world model section is included`() = runBlocking {
        val state = CognitiveState().copyWith(
            worldState = WorldModel(
                activeEntities = listOf(Entity("e1", "Phone", EntityType.DEVICE)),
                temporalContext = TemporalContext(upcomingEvents = listOf("Meeting at 3pm"))
            )
        )

        val context = builder().build(state, intentResult(), wm(), attention().getSpotlight())
        assertTrue(context.sections.any { it.name == "world" && it.included })
        assertTrue(context.text.contains("Phone"))
    }

    @Test
    fun `uncertainty section appears only when uncertainty is present`() = runBlocking {
        val high = CognitiveState().copyWith(uncertainty = UncertaintyProfile(overall = 0.7f, intentAmbiguity = 0.5f))
        val low = CognitiveState()

        val highContext = builder().build(high, intentResult(), wm(), attention().getSpotlight())
        val lowContext = builder().build(low, intentResult(), wm(), attention().getSpotlight())

        assertTrue(highContext.sections.any { it.name == "uncertainty" })
        assertFalse(lowContext.sections.any { it.name == "uncertainty" })
    }

    @Test
    fun `token budget forces compression or exclusion on overload`() = runBlocking {
        val memory = wm(maxItems = 30)
        (1..15).forEach { i ->
            memory.insert(mem("m$i", "A fairly long memory item content number $i ".repeat(6), activation = 1.0f - i * 0.02f))
        }

        val context = builder(maxTokens = 300).build(CognitiveState(), intentResult(), memory, attention().getSpotlight())
        assertTrue(context.withinBudget)
        assertTrue(
            "expected compression or exclusion but everything fit",
            context.sections.any { !it.included } || context.sections.any { it.content.contains("compressed") }
        )
    }

    @Test
    fun `empty working memory produces no memory section`() = runBlocking {
        val context = builder().build(CognitiveState(), intentResult(), wm(), attention().getSpotlight())
        assertFalse(context.sections.any { it.name == "memory" })
        assertFalse(context.text.contains("WORKING MEMORY"))
    }

    @Test
    fun `models can be disabled to keep context minimal`() = runBlocking {
        val minimal = CognitiveContextBuilder(
            scope = scope(),
            config = CognitiveContextBuilder.Config(
                includeSelfModel = false,
                includeUserModel = false,
                includeWorldModel = false
            )
        )
        val context = minimal.build(CognitiveState(), intentResult(), wm(), attention().getSpotlight())
        assertEquals(listOf("frame"), context.sections.map { it.name })
    }
}
