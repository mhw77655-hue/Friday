package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType

/**
 * ExperienceRecord - Structured representation of an experience for memory consolidation.
 *
 * An experience is a candidate for promotion to long-term memory. It captures
 * what happened, when, where, with what outcome, and supporting evidence.
 *
 * An experience is NOT automatically a permanent memory - it must be evaluated
 * and promoted through the consolidation pipeline.
 */
data class ExperienceRecord(
    /** Unique identifier for this experience */
    val experienceId: String = "exp_${System.currentTimeMillis()}_${(0..9).map { (Math.random() * 36).toInt().toChar() }.joinToString("")}",

    /** When the experience occurred */
    val timestamp: Long = System.currentTimeMillis(),

    /** Source of the experience */
    val source: ExperienceSource = ExperienceSource.COGNITIVE_CYCLE,

    /** Cognitive context at the time of the experience */
    val context: ExperienceContext = ExperienceContext(),

    /** Related entities (people, devices, concepts, tasks) */
    val relatedEntities: List<RelatedEntity> = emptyList(),

    /** Related goal if this experience was goal-directed */
    val relatedGoalId: String? = null,

    /** The action taken or observed */
    val action: ExperienceAction = ExperienceAction(),

    /** The result/outcome of the action */
    val result: ExperienceResult = ExperienceResult(),

    /** Observations made during the experience */
    val observations: List<String> = emptyList(),

    /** Failures encountered (if any) */
    val failures: List<ExperienceFailure> = emptyList(),

    /** Supporting evidence for this experience */
    val evidence: List<Evidence> = emptyList(),

    /** Confidence in this experience's accuracy (0.0 - 1.0) */
    val confidence: Float = 0.8f,

    /** Importance for potential promotion (0.0 - 1.0) */
    val importance: Float = 0.5f,

    /** Tags for categorization and retrieval */
    val tags: List<String> = emptyList(),

    /** Memory type this experience would map to if promoted */
    val candidateMemoryType: MemoryType = MemoryType.EPISODIC
) {
    /** Get a summary string for logging/debugging */
    fun summary(): String = "ExperienceRecord(id=$experienceId, type=${candidateMemoryType.name}, " +
        "importance=${String.format("%.2f", importance)}, confidence=${String.format("%.2f", confidence)}, " +
        "action=${action.actionType.name}, result=${result.outcome.name})"
}

/** Source of an experience */
enum class ExperienceSource {
    COGNITIVE_CYCLE,      // From normal cognitive processing
    USER_INTERACTION,     // Direct user interaction
    EXECUTION,            // From plan execution
    OBSERVATION,          // Passive observation
    REFLECTION,           // Internal reflection/reasoning
    EXTERNAL_EVENT,       // System/external event
    CORRECTION,           // User correction
    FEEDBACK              // Explicit feedback
}

/** Cognitive context at time of experience */
data class ExperienceContext(
    val turnIndex: Long = 0,
    val currentIntent: String? = null,
    val activeGoalId: String? = null,
    val activeSubgoalIds: List<String> = emptyList(),
    val resourcePressure: Boolean = false,
    val capabilityDegraded: Boolean = false,
    val sessionId: String? = null
)

/** Related entity in an experience */
data class RelatedEntity(
    val entityId: String,
    val name: String,
    val type: EntityType,
    val role: EntityRole = EntityRole.PARTICIPANT,
    val properties: Map<String, String> = emptyMap()
)

enum class EntityType { PERSON, DEVICE, LOCATION, CONCEPT, TASK, UNKNOWN }

enum class EntityRole { ACTOR, TARGET, OBSERVER, PARTICIPANT, CONTEXT }

/** Action taken during the experience */
data class ExperienceAction(
    val actionType: ActionType = ActionType.UNKNOWN,
    val description: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val durationMs: Long = 0,
    val initiatedBy: ActionInitiator = ActionInitiator.SELF
)

enum class ActionType {
    UNKNOWN, SPEAK, LISTEN, THINK, EXECUTE, OBSERVE, LEARN, DECIDE, PLAN, RETRIEVE, COMMUNICATE
}

enum class ActionInitiator { SELF, USER, SYSTEM, EXTERNAL }

/** Result of an action */
data class ExperienceResult(
    val outcome: Outcome = Outcome.UNKNOWN,
    val output: String? = null,
    val successCriteriaMet: List<String> = emptyList(),
    val successCriteriaFailed: List<String> = emptyList(),
    val sideEffects: List<String> = emptyList(),
    val metrics: Map<String, Float> = emptyMap()
)

enum class Outcome { UNKNOWN, SUCCESS, PARTIAL, FAILED, INTERRUPTED, DEFERRED, BLOCKED }

/** Failure encountered during experience */
data class ExperienceFailure(
    val failureType: String,
    val description: String,
    val severity: FailureSeverity = FailureSeverity.RECOVERABLE,
    val recovered: Boolean = false,
    val recoveryAction: String? = null
)

enum class FailureSeverity { INFO, RECOVERABLE, DEGRADED, CRITICAL, FATAL }

/** Evidence supporting the experience */
data class Evidence(
    val evidenceType: EvidenceType = EvidenceType.OBSERVATION,
    val description: String,
    val source: String,
    val strength: Float = 0.5f,  // 0.0 - 1.0
    val timestamp: Long = System.currentTimeMillis()
)

enum class EvidenceType {
    OBSERVATION,        // Direct observation
    USER_CONFIRMATION,  // User explicitly confirmed
    SYSTEM_LOG,         // System log/telemetry
    INFERENCE,          // Derived inference
    EXTERNAL_SOURCE,    // External source
    PRIOR_MEMORY        // Consistent with prior memory
}