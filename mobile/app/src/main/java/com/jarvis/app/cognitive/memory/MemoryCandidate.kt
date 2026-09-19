package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType

/**
 * MemoryLifecycleState - Explicit lifecycle states for memory items.
 *
 * The lifecycle represents the state of a memory in the consolidation pipeline.
 * Transitions must be deterministic and inspectable.
 */
enum class MemoryLifecycleState(
    val description: String,
    val isActive: Boolean,
    val isLongTerm: Boolean,
    val canBeQueried: Boolean
) {
    /**
     * Freshly created from an experience, not yet evaluated.
     * Not queryable by cognition.
     */
    CANDIDATE("Candidate for evaluation", false, false, false),

    /**
     * Promoted from candidate, actively held in working memory or
     * recently consolidated. Fully queryable.
     */
    ACTIVE("Active in working memory or recently consolidated", true, true, true),

    /**
     * Fully consolidated into long-term memory. Stable, queryable,
     * resistant to decay.
     */
    CONSOLIDATED("Fully consolidated in long-term memory", true, true, true),

    /**
     * Superseded by a newer memory. Historical truth preserved.
     * Queryable for historical context but marked as superseded.
     */
    SUPERSEDED("Superseded by newer information", false, true, true),

    /**
     * In conflict with another memory. Resolution pending.
     * Queryable but flagged as conflicting.
     */
    CONFLICTED("In conflict with another memory", true, true, true),

    /**
     * Archived - moved to cold storage. Queryable but with lower priority.
     * Retained for completeness but not actively maintained.
     */
    ARCHIVED("Archived to cold storage", false, true, true),

    /**
     * Explicitly rejected - not promoted. Retained for audit trail.
     * Not queryable by cognition.
     */
    REJECTED("Rejected during evaluation", false, false, false)
}

/**
 * Valid lifecycle transitions.
 * Each transition must have a deterministic reason.
 */
enum class LifecycleTransition(
    val from: MemoryLifecycleState,
    val to: MemoryLifecycleState,
    val requiresReason: Boolean = true
) {
    CANDIDATE_TO_ACTIVE(MemoryLifecycleState.CANDIDATE, MemoryLifecycleState.ACTIVE),
    CANDIDATE_TO_REJECTED(MemoryLifecycleState.CANDIDATE, MemoryLifecycleState.REJECTED),
    CANDIDATE_TO_DEFERRED(MemoryLifecycleState.CANDIDATE, MemoryLifecycleState.CANDIDATE), // Re-evaluate later

    ACTIVE_TO_CONSOLIDATED(MemoryLifecycleState.ACTIVE, MemoryLifecycleState.CONSOLIDATED),
    ACTIVE_TO_SUPERSEDED(MemoryLifecycleState.ACTIVE, MemoryLifecycleState.SUPERSEDED),
    ACTIVE_TO_CONFLICTED(MemoryLifecycleState.ACTIVE, MemoryLifecycleState.CONFLICTED),
    ACTIVE_TO_ARCHIVED(MemoryLifecycleState.ACTIVE, MemoryLifecycleState.ARCHIVED),

    CONSOLIDATED_TO_SUPERSEDED(MemoryLifecycleState.CONSOLIDATED, MemoryLifecycleState.SUPERSEDED),
    CONSOLIDATED_TO_CONFLICTED(MemoryLifecycleState.CONSOLIDATED, MemoryLifecycleState.CONFLICTED),
    CONSOLIDATED_TO_ARCHIVED(MemoryLifecycleState.CONSOLIDATED, MemoryLifecycleState.ARCHIVED),

    SUPERSEDED_TO_ARCHIVED(MemoryLifecycleState.SUPERSEDED, MemoryLifecycleState.ARCHIVED),
    SUPERSEDED_TO_CONFLICTED(MemoryLifecycleState.SUPERSEDED, MemoryLifecycleState.CONFLICTED),

    CONFLICTED_TO_RESOLVED_ACTIVE(MemoryLifecycleState.CONFLICTED, MemoryLifecycleState.ACTIVE, false),
    CONFLICTED_TO_RESOLVED_CONSOLIDATED(MemoryLifecycleState.CONFLICTED, MemoryLifecycleState.CONSOLIDATED, false),
    CONFLICTED_TO_RESOLVED_SUPERSEDED(MemoryLifecycleState.CONFLICTED, MemoryLifecycleState.SUPERSEDED, false),
    CONFLICTED_TO_ARCHIVED(MemoryLifecycleState.CONFLICTED, MemoryLifecycleState.ARCHIVED),

    ARCHIVED_TO_ACTIVE(MemoryLifecycleState.ARCHIVED, MemoryLifecycleState.ACTIVE), // Recalled
    ARCHIVED_TO_CONSOLIDATED(MemoryLifecycleState.ARCHIVED, MemoryLifecycleState.CONSOLIDATED),

    REJECTED_TO_CANDIDATE(MemoryLifecycleState.REJECTED, MemoryLifecycleState.CANDIDATE) // Re-evaluation
}

/**
 * MemoryCandidate - A memory item under evaluation for promotion.
 *
 * Wraps an ExperienceRecord with evaluation metadata.
 */
data class MemoryCandidate(
    /** The experience this candidate is based on */
    val experience: ExperienceRecord,

    /** Current lifecycle state */
    val lifecycleState: MemoryLifecycleState = MemoryLifecycleState.CANDIDATE,

    /** Evaluation score factors (0.0 - 1.0 each) */
    val promotionFactors: PromotionFactors = PromotionFactors(),

    /** Overall promotion score */
    val promotionScore: Float = 0.0f,

    /** Whether this candidate has been evaluated */
    val evaluated: Boolean = false,

    /** Evaluation timestamp */
    val evaluatedAt: Long = 0,

    /** Reason for promotion/rejection/deferral */
    val evaluationReason: String = "",

    /** If promoted, the ID of the resulting consolidated memory */
    val promotedMemoryId: String? = null,

    /** Source experience ID for traceability */
    val sourceExperienceId: String = experience.experienceId
) {
    /** Check if candidate meets promotion threshold */
    fun isPromotable(threshold: Float = 0.6f): Boolean = promotionScore >= threshold && evaluated

    /** Check if candidate should be rejected */
    fun isRejectable(threshold: Float = 0.3f): Boolean = promotionScore < threshold && evaluated

    /** Get promotion decision */
    fun getPromotionDecision(promoteThreshold: Float = 0.6f, rejectThreshold: Float = 0.3f): PromotionDecision {
        if (!evaluated) return PromotionDecision.DEFERRED
        return when {
            promotionScore >= promoteThreshold -> PromotionDecision.PROMOTE
            promotionScore < rejectThreshold -> PromotionDecision.REJECT
            else -> PromotionDecision.DEFERRED
        }
    }
}

/**
 * Factors that influence promotion from candidate to long-term memory.
 *
 * Policy: deterministic and inspectable. No arbitrary complexity.
 */
data class PromotionFactors(
    /** How often similar experiences occur (0.0 - 1.0) */
    val recurrence: Float = 0.0f,

    /** Intrinsic importance of this experience (0.0 - 1.0) */
    val importance: Float = 0.0f,

    /** Relevance to current user goals (0.0 - 1.0) */
    val userRelevance: Float = 0.0f,

    /** Relevance to active goals (0.0 - 1.0) */
    val goalRelevance: Float = 0.0f,

    /** Estimated future utility (0.0 - 1.0) */
    val futureUtility: Float = 0.0f,

    /** Novelty - how different from existing memories (0.0 - 1.0) */
    val novelty: Float = 0.0f,

    /** Reliability of the source/experience (0.0 - 1.0) */
    val reliability: Float = 0.0f,

    /** Explicit user instruction to remember (0.0 - 1.0) */
    val explicitInstruction: Float = 0.0f,

    /** Learning value from failure (0.0 - 1.0) */
    val failureLearningValue: Float = 0.0f
) {
    /**
     * Compute weighted promotion score.
     * Weights are fixed policy - not configurable to ensure determinism.
     */
    fun computeScore(): Float {
        // Weights sum to 1.0
        val weights = mapOf(
            "recurrence" to 0.15f,
            "importance" to 0.20f,
            "userRelevance" to 0.15f,
            "goalRelevance" to 0.15f,
            "futureUtility" to 0.10f,
            "novelty" to 0.05f,
            "reliability" to 0.10f,
            "explicitInstruction" to 0.05f,
            "failureLearningValue" to 0.05f
        )

        return (recurrence * weights["recurrence"]!! +
                importance * weights["importance"]!! +
                userRelevance * weights["userRelevance"]!! +
                goalRelevance * weights["goalRelevance"]!! +
                futureUtility * weights["futureUtility"]!! +
                novelty * weights["novelty"]!! +
                reliability * weights["reliability"]!! +
                explicitInstruction * weights["explicitInstruction"]!! +
                failureLearningValue * weights["failureLearningValue"]!!)
            .coerceIn(0.0f, 1.0f)
    }
}

/** Promotion decision outcome */
enum class PromotionDecision {
    PROMOTE,      // Promote to ACTIVE/CONSOLIDATED
    REJECT,       // Reject - move to REJECTED
    DEFERRED      // Defer - keep as CANDIDATE for re-evaluation
}