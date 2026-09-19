package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for CognitiveState
 */
@RunWith(JUnit4::class)
class CognitiveStateTest {

    @Test
    fun testDefaultState() {
        val state = CognitiveState()
        assertEquals(IntentState.UNKNOWN, state.currentIntent)
        assertEquals(IntentState.UNKNOWN, state.inferredIntent)
        assertEquals(0.0f, state.intentConfidence, 0.001f)
        assertNull(state.currentGoal)
        assertTrue(state.activeSubgoals.isEmpty())
        assertTrue(state.activeMemories.isEmpty())
        assertTrue(state.attentionItems.isEmpty())
        assertEquals(0, state.turnIndex)
        assertFalse(state.isSubstantive())
    }

    @Test
    fun testCopyWithUpdates() {
        val state = CognitiveState()
        val goal = Goal("goal_1", "Test goal", GoalPriority.HIGH)
        val updated = state.copyWith(
            currentIntent = IntentState.COMMAND,
            intentConfidence = 0.9f,
            currentGoal = goal,
            turnIndex = 5
        )

        assertEquals(IntentState.COMMAND, updated.currentIntent)
        assertEquals(0.9f, updated.intentConfidence, 0.001f)
        assertEquals(goal, updated.currentGoal)
        assertEquals(5, updated.turnIndex)
        // Unchanged fields preserved
        assertEquals(IntentState.UNKNOWN, updated.inferredIntent)
        assertTrue(updated.activeSubgoals.isEmpty())
    }

    @Test
    fun testIsSubstantive() {
        var state = CognitiveState()
        assertFalse(state.isSubstantive())

        state = state.copyWith(currentIntent = IntentState.QUESTION)
        assertTrue(state.isSubstantive())

        state = CognitiveState().copyWith(currentGoal = Goal("g", "desc"))
        assertTrue(state.isSubstantive())

        state = CognitiveState().copyWith(activeMemories = listOf(ActiveMemory(
            id = "m1", sourceId = "s1", content = "test", memoryType = MemoryType.FACT,
            activation = 0.5f, relevance = 0.5f, goalAlignment = 0.5f, uncertainty = 0.1f
        )))
        assertTrue(state.isSubstantive())
    }

    @Test
    fun testGoalAndSubgoal() {
        val goal = Goal(
            id = "goal_1",
            description = "Learn Kotlin",
            priority = GoalPriority.HIGH,
            successCriteria = listOf("Complete basics", "Build project")
        )
        assertEquals(GoalStatus.ACTIVE, goal.status)
        assertEquals(GoalPriority.HIGH, goal.priority)

        val subgoal = Subgoal(
            id = "sub_1",
            goalId = "goal_1",
            description = "Complete basics",
            status = SubgoalStatus.IN_PROGRESS
        )
        assertEquals(SubgoalStatus.IN_PROGRESS, subgoal.status)
    }

    @Test
    fun testActiveMemoryAccessAndDecay() {
        val memory = ActiveMemory(
            id = "m1", sourceId = "s1", content = "test memory",
            memoryType = MemoryType.EPISODIC, activation = 0.5f,
            relevance = 0.7f, goalAlignment = 0.6f, uncertainty = 0.2f
        )

        val accessed = memory.withAccess()
        assertEquals(0.6f, accessed.activation, 0.001f) // Boosted by 0.1
        assertEquals(1, accessed.accessCount)
        assertTrue(accessed.lastAccessed >= memory.lastAccessed)

        val decayed = memory.decayed(0.1f)
        assertEquals(0.4f, decayed.activation, 0.001f)

        // Test floor at 0
        val heavilyDecayed = memory.decayed(1.0f)
        assertEquals(0.0f, heavilyDecayed.activation, 0.001f)
    }

    @Test
    fun testUncertaintyProfile() {
        val profile = UncertaintyProfile(
            overall = 0.8f,
            intentAmbiguity = 0.6f,
            factualUncertainty = 0.3f,
            unknowns = listOf("missing referent", "vague action")
        )
        assertTrue(profile.hasHighUncertainty(0.7f))
        assertFalse(profile.hasHighUncertainty(0.9f))
    }

    @Test
    fun testResourceStatePressure() {
        val normal = ResourceState()
        assertFalse(normal.isUnderPressure())

        val highCpu = ResourceState(cpuPressure = 0.8f)
        assertTrue(highCpu.isUnderPressure())

        val lowBattery = ResourceState(batteryLevel = 0.1f)
        assertTrue(lowBattery.isUnderPressure())

        val thermal = ResourceState(thermalState = ThermalState.HOT)
        assertTrue(thermal.isUnderPressure())
    }

    @Test
    fun testCapabilityState() {
        val full = CapabilityState(sttAvailable = true, ttsAvailable = true, modelLoaded = true)
        assertFalse(full.isDegraded())

        val degraded = CapabilityState(degradedCapabilities = listOf("STT"))
        assertTrue(degraded.isDegraded())
    }

    @Test
    fun testSelfModel() {
        val self = SelfModel(
            identityName = "JARVIS",
            personalityTraits = mapOf("openness" to 0.8f, "conscientiousness" to 0.7f),
            currentMood = MoodState(valence = 0.5f, arousal = 0.3f, dominantEmotion = "curious"),
            selfAssessment = SelfAssessment(confidence = 0.8f, cognitiveLoad = 0.2f)
        )
        assertEquals("JARVIS", self.identityName)
        assertEquals(0.5f, self.currentMood.valence, 0.001f)
    }

    @Test
    fun testUserModel() {
        val user = UserModel(
            knownName = "Ahmed",
            communicationStyle = CommunicationStyle(formality = 0.3f, directness = 0.8f),
            currentContext = UserContext(currentActivity = "working", recentTopics = listOf("kotlin", "android"))
        )
        assertEquals("Ahmed", user.knownName)
        assertEquals(0.3f, user.communicationStyle.formality, 0.001f)
    }

    @Test
    fun testWorldModel() {
        val world = WorldModel(
            environment = EnvironmentState(deviceState = "mobile", connectivity = "wifi"),
            activeEntities = listOf(Entity("e1", "Phone", EntityType.DEVICE)),
            temporalContext = TemporalContext(isWorkHours = true, upcomingEvents = listOf("Meeting at 3pm"))
        )
        assertEquals("mobile", world.environment.deviceState)
        assertEquals(1, world.activeEntities.size)
    }

    @Test
    fun testPlanAndSteps() {
        val plan = Plan(
            id = "plan_1",
            goalId = "goal_1",
            steps = listOf(
                PlanStep("step_1", "Read docs", "read", "Understand basics"),
                PlanStep("step_2", "Write code", "code", "Working example")
            )
        )
        assertEquals(2, plan.steps.size)
        assertEquals(PlanStatus.PENDING, plan.status)
    }

    @Test
    fun testDecisionRecord() {
        val decision = DecisionRecord(
            id = "dec_1",
            context = "User asked for weather",
            options = listOf(
                DecisionOption("opt_1", "Use API", "Get accurate data", 0.1f, 0.3f, 0.9f),
                DecisionOption("opt_2", "Guess", "Fast but wrong", 0.8f, 0.1f, 0.2f)
            ),
            chosen = DecisionOption("opt_1", "Use API", "Get accurate data", 0.1f, 0.3f, 0.9f),
            rationale = "Accuracy matters for weather",
            confidence = 0.9f
        )
        assertEquals("opt_1", decision.chosen.id)
        assertEquals(0.9f, decision.confidence, 0.001f)
    }
}