package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryType
import com.jarvis.app.humancore.protocol.SessionContext
import java.util.TimeZone

/**
 * CognitiveState - The central runtime state representing JARVIS's current
 * cognitive frame. This is structured runtime state, not a markdown file.
 *
 * All fields are observable/readable by other subsystems. Mutations happen
 * through the CognitiveEngine which coordinates transitions.
 */
data class CognitiveState(
    // Intent tracking
    val currentIntent: IntentState = IntentState.UNKNOWN,
    val inferredIntent: IntentState = IntentState.UNKNOWN,
    val intentConfidence: Float = 0.0f,

    // Goal tracking
    val currentGoal: Goal? = null,
    val activeSubgoals: List<Subgoal> = emptyList(),

    // Working memory
    val activeMemories: List<ActiveMemory> = emptyList(),
    val attentionItems: List<AttentionItem> = emptyList(),

    // Uncertainty & ambiguity
    val uncertainty: UncertaintyProfile = UncertaintyProfile(),

    // Resource & capability awareness
    val resourceState: ResourceState = ResourceState(),
    val capabilityState: CapabilityState = CapabilityState(),

    // Self / User / World models
    val selfState: SelfModel = SelfModel(),
    val userState: UserModel = UserModel(),
    val worldState: WorldModel = WorldModel(),

    // Planning & decision
    val currentPlan: Plan? = null,
    val lastDecision: DecisionRecord? = null,

    // Metadata
    val turnIndex: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionContext: SessionContext? = null
) {
    /** Create a new state with updated fields (copy with builder pattern) */
    fun copyWith(
        currentIntent: IntentState? = null,
        inferredIntent: IntentState? = null,
        intentConfidence: Float? = null,
        currentGoal: Goal? = null,
        activeSubgoals: List<Subgoal>? = null,
        activeMemories: List<ActiveMemory>? = null,
        attentionItems: List<AttentionItem>? = null,
        uncertainty: UncertaintyProfile? = null,
        resourceState: ResourceState? = null,
        capabilityState: CapabilityState? = null,
        selfState: SelfModel? = null,
        userState: UserModel? = null,
        worldState: WorldModel? = null,
        currentPlan: Plan? = null,
        lastDecision: DecisionRecord? = null,
        turnIndex: Long? = null,
        timestamp: Long? = null,
        sessionContext: SessionContext? = null
    ): CognitiveState = copy(
        currentIntent = currentIntent ?: this.currentIntent,
        inferredIntent = inferredIntent ?: this.inferredIntent,
        intentConfidence = intentConfidence ?: this.intentConfidence,
        currentGoal = currentGoal ?: this.currentGoal,
        activeSubgoals = activeSubgoals ?: this.activeSubgoals,
        activeMemories = activeMemories ?: this.activeMemories,
        attentionItems = attentionItems ?: this.attentionItems,
        uncertainty = uncertainty ?: this.uncertainty,
        resourceState = resourceState ?: this.resourceState,
        capabilityState = capabilityState ?: this.capabilityState,
        selfState = selfState ?: this.selfState,
        userState = userState ?: this.userState,
        worldState = worldState ?: this.worldState,
        currentPlan = currentPlan ?: this.currentPlan,
        lastDecision = lastDecision ?: this.lastDecision,
        turnIndex = turnIndex ?: this.turnIndex,
        timestamp = timestamp ?: System.currentTimeMillis(),
        sessionContext = sessionContext ?: this.sessionContext
    )

    /** Check if cognitive state has meaningful content */
    fun isSubstantive(): Boolean =
        currentIntent != IntentState.UNKNOWN ||
        currentGoal != null ||
        activeMemories.isNotEmpty() ||
        attentionItems.isNotEmpty() ||
        currentPlan != null
}

/** Intent classification states */
enum class IntentState {
    UNKNOWN,
    QUESTION,
    COMMAND,
    CONVERSATION,
    TEACHING,
    CLARIFICATION,
    GREETING,
    FAREWELL,
    CORRECTION,
    META_COGNITIVE,  // "what are you thinking", "explain your reasoning"
    AMBIGUOUS
}

/** A goal JARVIS is pursuing */
data class Goal(
    val id: String,
    val description: String,
    val priority: GoalPriority = GoalPriority.NORMAL,
    val createdAt: Long = System.currentTimeMillis(),
    val deadline: Long? = null,
    val successCriteria: List<String> = emptyList(),
    val status: GoalStatus = GoalStatus.ACTIVE,
    val parentGoalId: String? = null
)

enum class GoalPriority { LOW, NORMAL, HIGH, CRITICAL }

/**
 * Goal lifecycle (Build 01B). SUSPENDED/ABANDONED are retained for Build 01A
 * callers; the planner drives CREATED -> ACTIVE -> BLOCKED/PAUSED -> COMPLETED
 * or FAILED/CANCELLED.
 */
enum class GoalStatus {
    CREATED, ACTIVE, BLOCKED, PAUSED,
    COMPLETED, FAILED, CANCELLED,
    SUSPENDED, ABANDONED
}

/** A subgoal decomposing a larger goal */
data class Subgoal(
    val id: String,
    val goalId: String,
    val description: String,
    val status: SubgoalStatus = SubgoalStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val estimatedEffort: Float = 1.0f,
    val dependencies: List<String> = emptyList()
)

enum class SubgoalStatus { PENDING, IN_PROGRESS, COMPLETED, BLOCKED, FAILED, PAUSED, CANCELLED }

/** An active memory item in working memory with cognitive metadata */
data class ActiveMemory(
    val id: String,
    val sourceId: String,  // Original memory store ID
    val content: String,
    val memoryType: MemoryType,
    val activation: Float,       // 0.0 - 1.0, current activation level
    val relevance: Float,        // 0.0 - 1.0, relevance to current context
    val goalAlignment: Float,    // 0.0 - 1.0, alignment with current goal
    val uncertainty: Float,      // 0.0 - 1.0, how uncertain this memory is
    val lastAccessed: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val tags: List<String> = emptyList(),
    val source: MemorySource = MemorySource.EPISODIC
) {
    fun withAccess(): ActiveMemory = copy(
        lastAccessed = System.currentTimeMillis(),
        accessCount = accessCount + 1,
        activation = minOf(activation + 0.1f, 1.0f)
    )

    fun decayed(decayRate: Float = 0.05f): ActiveMemory = copy(
        activation = maxOf(activation - decayRate, 0.0f)
    )
}

enum class MemorySource { FACT, EPISODIC, PREFERENCE, VOCABULARY, CONTEXT, INFERRED, EXTERNAL }

/** An item currently in the attention spotlight */
data class AttentionItem(
    val id: String,
    val content: String,
    val source: AttentionSource,
    val salience: Float,         // 0.0 - 1.0, how salient this item is
    val urgency: Float,          // 0.0 - 1.0, how urgent
    val goalRelevance: Float,    // 0.0 - 1.0, relevance to current goal
    val novelty: Float,          // 0.0 - 1.0, how novel/unexpected
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

enum class AttentionSource {
    USER_INPUT, MEMORY_RETRIEVAL, MODEL_OUTPUT, ENVIRONMENT, INTERNAL_REASONING, PREDICTION
}

/** Profile of current uncertainties */
data class UncertaintyProfile(
    val overall: Float = 0.0f,
    val intentAmbiguity: Float = 0.0f,
    val factualUncertainty: Float = 0.0f,
    val goalConflict: Float = 0.0f,
    val resourceUncertainty: Float = 0.0f,
    val unknowns: List<String> = emptyList(),
    val assumptions: List<String> = emptyList()
) {
    fun hasHighUncertainty(threshold: Float = 0.7f): Boolean = overall >= threshold
}

/** Current resource state awareness */
data class ResourceState(
    val cpuPressure: Float = 0.0f,       // 0.0 - 1.0
    val memoryPressure: Float = 0.0f,    // 0.0 - 1.0
    val batteryLevel: Float = 1.0f,      // 0.0 - 1.0
    val thermalState: ThermalState = ThermalState.NOMINAL,
    val networkAvailable: Boolean = false,
    val storageAvailable: Boolean = true
) {
    fun isUnderPressure(threshold: Float = 0.7f): Boolean =
        cpuPressure >= threshold || memoryPressure >= threshold ||
        thermalState != ThermalState.NOMINAL || batteryLevel < 0.2f
}

enum class ThermalState { NOMINAL, WARM, HOT, CRITICAL }

/** Current capability state awareness */
data class CapabilityState(
    val sttAvailable: Boolean = true,
    val ttsAvailable: Boolean = true,
    val modelLoaded: Boolean = false,
    val modelProvider: String? = null,
    val visionAvailable: Boolean = false,
    val toolsAvailable: List<String> = emptyList(),
    val degradedCapabilities: List<String> = emptyList()
) {
    fun isDegraded(): Boolean = degradedCapabilities.isNotEmpty()
}

/** Self-model: JARVIS's model of itself */
data class SelfModel(
    val identityName: String = "JARVIS",
    val identityVersion: String = "1.0",
    val personalityTraits: Map<String, Float> = emptyMap(),
    val currentMood: MoodState = MoodState(),
    val capabilities: List<String> = emptyList(),
    val limitations: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    val selfAssessment: SelfAssessment = SelfAssessment()
)

data class MoodState(
    val valence: Float = 0.0f,    // -1.0 to 1.0
    val arousal: Float = 0.0f,    // 0.0 to 1.0
    val dominantEmotion: String? = null
)

data class SelfAssessment(
    val confidence: Float = 0.5f,
    val cognitiveLoad: Float = 0.0f,
    val recentPerformance: Float = 0.5f
)

/** User-model: JARVIS's model of the user */
data class UserModel(
    val knownName: String? = null,
    val preferences: Map<String, String> = emptyMap(),
    val knownFacts: Map<String, String> = emptyMap(),
    val relationshipDepth: Float = 0.0f,    // 0.0 - 1.0
    val trustLevel: Float = 0.5f,           // 0.0 - 1.0
    val communicationStyle: CommunicationStyle = CommunicationStyle(),
    val currentContext: UserContext = UserContext(),
    val predictedNeeds: List<String> = emptyList()
)

data class CommunicationStyle(
    val formality: Float = 0.5f,      // 0.0 casual - 1.0 formal
    val verbosity: Float = 0.5f,      // 0.0 concise - 1.0 verbose
    val directness: Float = 0.5f,     // 0.0 indirect - 1.0 direct
    val humor: Float = 0.3f           // 0.0 serious - 1.0 playful
)

data class UserContext(
    val currentActivity: String? = null,
    val location: String? = null,
    val timeOfDay: String? = null,
    val recentTopics: List<String> = emptyList(),
    val emotionalState: String? = null
)

/** World-model: JARVIS's model of the world/environment */
data class WorldModel(
    val currentTime: Long = System.currentTimeMillis(),
    val timeZone: String = TimeZone.getDefault().id,
    val environment: EnvironmentState = EnvironmentState(),
    val activeEntities: List<Entity> = emptyList(),
    val knownFacts: Map<String, String> = emptyMap(),
    val temporalContext: TemporalContext = TemporalContext()
)

data class EnvironmentState(
    val noiseLevel: Float = 0.0f,
    val lighting: String? = null,
    val deviceState: String = "mobile",
    val connectivity: String = "unknown"
)

data class Entity(
    val id: String,
    val name: String,
    val type: EntityType,
    val properties: Map<String, String> = emptyMap(),
    val lastObserved: Long = System.currentTimeMillis()
)

enum class EntityType { PERSON, DEVICE, LOCATION, CONCEPT, TASK, UNKNOWN }

data class TemporalContext(
    val isWeekend: Boolean = false,
    val isWorkHours: Boolean = false,
    val season: String? = null,
    val upcomingEvents: List<String> = emptyList()
)

/** A plan consisting of steps toward a goal */
data class Plan(
    val id: String,
    val goalId: String,
    val steps: List<PlanStep> = emptyList(),
    val status: PlanStatus = PlanStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val estimatedDuration: Long = 0
)

enum class PlanStatus { PENDING, EXECUTING, BLOCKED, PAUSED, COMPLETED, FAILED, REPLANNING }

data class PlanStep(
    val id: String,
    val description: String,
    val action: String,
    val expectedOutcome: String,
    val dependencies: List<String> = emptyList(),
    val status: PlanStepStatus = PlanStepStatus.PENDING,
    val estimatedEffort: Float = 1.0f
)

enum class PlanStepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, BLOCKED, PAUSED, CANCELLED }

/** Record of a decision made */
data class DecisionRecord(
    val id: String,
    val context: String,
    val options: List<DecisionOption>,
    val chosen: DecisionOption,
    val rationale: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val outcome: DecisionOutcome? = null,
    /** Structured reasoning metadata — scores per factor, no prose required. */
    val reasonMetadata: DecisionReason? = null,
    /** Compact snapshot of the cognitive state the decision was made under. */
    val stateSnapshot: DecisionStateSnapshot? = null,
    /** The outcome the decision was expected to produce. */
    val expectedOutcome: String? = null
)

/**
 * Decision option (Build 01B). Existing Build 01A fields are retained and
 * new evaluation fields default so old constructor calls still compile.
 */
data class DecisionOption(
    val id: String,
    val description: String,
    val predictedOutcome: String,
    val risk: Float,
    val effort: Float,
    val alignment: Float,
    val confidence: Float = 0.5f,
    val resourceRequirements: Map<String, Float> = emptyMap(),
    val capabilityRequirements: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val reversibility: Float = 0.5f
)

/**
 * Structured decision reasoning — exposes WHY an option won without requiring
 * a prose rationale. Each factor carries its score and weight.
 */
data class DecisionReason(
    val totalScore: Float,
    val factorScores: Map<DecisionFactor, Float>,
    val appliedConstraints: List<String>,
    val uncertaintyAtDecision: Float = 0.0f,
    val resourcePressureAtDecision: Float = 0.0f,
    val degradedCapabilitiesAtDecision: List<String> = emptyList(),
    val rejectedOptions: List<String> = emptyList(),
    val evaluationType: DecisionEvaluationType = DecisionEvaluationType.WEIGHTED
)

enum class DecisionFactor { GOAL_ALIGNMENT, CONSTRAINT, UNCERTAINTY, RESOURCE, CAPABILITY, OUTCOME, RISK, REVERSIBILITY, EFFORT }

enum class DecisionEvaluationType { WEIGHTED, ADHOC }

/** Compact, deterministic snapshot of the state behind a decision. */
data class DecisionStateSnapshot(
    val intent: IntentState? = null,
    val intentConfidence: Float = 0.0f,
    val currentGoalDescription: String? = null,
    val activeSubgoalIds: List<String> = emptyList(),
    val uncertaintyOverall: Float = 0.0f,
    val uncertaintyUnknowns: List<String> = emptyList(),
    val activeMemoryCount: Int = 0,
    val attentionCount: Int = 0,
    val resourcePressure: Boolean = false,
    val capabilityDegraded: Boolean = false
) {
    companion object {
        fun from(state: CognitiveState): DecisionStateSnapshot = DecisionStateSnapshot(
            intent = state.currentIntent,
            intentConfidence = state.intentConfidence,
            currentGoalDescription = state.currentGoal?.description,
            activeSubgoalIds = state.activeSubgoals.map { it.id },
            uncertaintyOverall = state.uncertainty.overall,
            uncertaintyUnknowns = state.uncertainty.unknowns,
            activeMemoryCount = state.activeMemories.size,
            attentionCount = state.attentionItems.size,
            resourcePressure = state.resourceState.isUnderPressure(),
            capabilityDegraded = state.capabilityState.isDegraded()
        )
    }
}

enum class DecisionOutcome { SUCCESS, PARTIAL, FAILED, UNKNOWN, PENDING }