package com.jarvis.app.cognitive.model

/**
 * UserModel - Durable, structured representation of the current user.
 *
 * NOT a transcript archive: it holds the things Jarvis has actually learned,
 * each backed by evidence with explicit epistemic status (user said vs. Jarvis
 * inferred vs. repeatedly observed vs. confirmed vs. possibly outdated).
 *
 * Deliberate boundaries:
 * - Identity is never assumed — only recorded when evidence supports it.
 * - Goals REFERENCE the goal/task system (GoalPlanner); no duplicate task list.
 * - Relationship facts are owned by HumanCore; this model exposes a structured
 *   REFERENCE to them, not a recreation.
 * - Sensitive attributes carry [Sensitivity]; secrets are never persisted raw.
 *
 * Immutable snapshot produced by [ModelUpdateEngine].
 */
data class UserModel(
    /** Identity — only what evidence supports. */
    val identity: UserIdentity = UserIdentity(),

    /** Structured preferences, each with source/confidence/recency/confirmation. */
    val preferences: List<UserPreference> = emptyList(),

    /** Active/recurring user goals (references to the goal system). */
    val goals: List<UserGoalReference> = emptyList(),

    /** Observed working style (evidence-backed patterns). */
    val workingStyle: UserWorkingStyle = UserWorkingStyle(),

    /** User-associated project context. */
    val projectContext: UserProjectContext = UserProjectContext(),

    /** Communication expectations. */
    val communicationExpectations: UserCommunicationExpectations = UserCommunicationExpectations(),

    /** Structured references to relationship facts owned by HumanCore. */
    val relationshipContext: UserRelationshipContext = UserRelationshipContext(),

    /** Evidence-backed facts (by stable fact key — the current candidate). */
    val facts: Map<String, ModelFact> = emptyMap(),

    /** Non-current facts preserved in deterministic order (history kept). */
    val factHistory: List<ModelFact> = emptyList()
) {
    fun fact(factKey: String): ModelFact? = facts[factKey]

    fun currentFacts(): List<ModelFact> = facts.values.filter { it.isCurrent() }

    fun summary(): String =
        "UserModel[id=${identity.userId ?: "unknown"} prefs=${preferences.size} " +
            "goals=${goals.size} facts=${facts.size}]"
}

/** User identity. Deliberately all-nullable: never assumed. */
data class UserIdentity(
    val userId: String? = null,
    val displayIdentity: String? = null,
    val knownNames: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val preferredAddress: String? = null
)

/**
 * UserPreference - A structured preference with full epistemic metadata.
 */
data class UserPreference(
    /** Stable preference key (e.g. "communication.style"). */
    val key: String,

    /** The preference value. */
    val value: String,

    /** Source of the preference. */
    val source: ModelEvidenceSource,

    /** Confidence in the preference (0.0 - 1.0). */
    val confidence: Float,

    /** When it was last observed. */
    val lastObservedAt: Long = System.currentTimeMillis(),

    /** Explicit confirmation state. */
    val confirmationState: ConfirmationState = ConfirmationState.UNCONFIRMED,

    /** Sensitivity classification. */
    val sensitivity: Sensitivity = Sensitivity.NONE
)

/** A reference to a user goal owned by the goal/task system. */
data class UserGoalReference(
    val goalId: String,
    val description: String,
    val isRecurring: Boolean = false,
    val isActive: Boolean = false
)

/** Observed working patterns — always evidence-backed, never invented. */
data class UserWorkingStyle(
    /** Observed lean: concise vs detailed (-1.0 concise .. +1.0 detailed). */
    val conciseVsDetailed: Float = 0.0f,

    /** Preferred workflow description, if observed. */
    val preferredWorkflow: String? = null,

    /** Frequent project context. */
    val frequentProjectContext: List<String> = emptyList(),

    /** Common shorthand the user uses. */
    val commonShorthand: List<String> = emptyList(),

    /** Preferred execution behavior, if observed. */
    val preferredExecutionBehavior: String? = null
)

/** User-associated projects/domains/interests/artifacts (reference WorldModel). */
data class UserProjectContext(
    val projects: List<String> = emptyList(),
    val recurringDomains: List<String> = emptyList(),
    val activeInterests: List<String> = emptyList(),
    /** WorldModel entity ids for known artifacts. */
    val knownArtifactEntityIds: List<String> = emptyList()
)

/** Communication expectations. */
data class UserCommunicationExpectations(
    val expectedResponseStyle: String? = null,
    val interruptionPreference: String? = null,
    val explanationPreference: String? = null,
    val confirmationExpectations: String? = null,
    val preferredInteractionMode: String? = null
)

/**
 * UserRelationshipContext - Structured reference to relationship facts owned
 * by HumanCore. HumanCore remains authoritative for identity/relationship
 * behavior; this model never recreates relationship intelligence.
 */
data class UserRelationshipContext(
    /** HumanCore relationship store id this context references. */
    val relationshipStoreId: String? = null,

    /** Coarse relationship labels (e.g. trust level) mirrored from HumanCore. */
    val mirroredFacts: Map<String, String> = emptyMap(),

    val lastSyncedAt: Long = 0
)
