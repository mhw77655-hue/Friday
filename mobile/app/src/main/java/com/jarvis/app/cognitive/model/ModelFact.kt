package com.jarvis.app.cognitive.model

/**
 * ModelFact - The atomic, evidence-backed knowledge unit of the 01H
 * Self/User/World model layer.
 *
 * Every meaningful model fact carries an explicit epistemic status (RULE 5 —
 * never silently turn an assumption into a fact), a confidence representation,
 * evidence, and sensitivity classification. Facts are keyed by a stable
 * `factKey` within their [ModelDomain]; the fact ID is the unique instance id.
 *
 * Determinism: facts are immutable. The [ModelUpdateEngine] produces new fact
 * instances; it never mutates an existing one in place.
 */
data class ModelFact(
    /** Unique instance id for this fact. */
    val factId: String = "mf_${System.currentTimeMillis()}_${(0..9).map { (Math.random() * 36).toInt().toChar() }.joinToString("")}",

    /** Which model this fact belongs to. */
    val domain: ModelDomain,

    /** Stable semantic key within the domain (e.g. "identity.preferredName"). */
    val factKey: String,

    /** The current value. Structured values live on [WorldEntity] attributes;
     *  a fact value is the canonical serialized form. */
    val value: String,

    /** Explicit epistemic status. */
    val status: ModelFactStatus,

    /** Confidence representation (score, source reliability, confirmation…). */
    val confidence: ModelConfidence,

    /** Sensitivity classification (privacy guard). */
    val sensitivity: Sensitivity = Sensitivity.NONE,

    /** Evidence chain that supports this fact. */
    val evidence: List<ModelEvidence> = emptyList(),

    /** When the underlying truth was first observed. */
    val observedAt: Long = System.currentTimeMillis(),

    /** When this fact instance was last updated. */
    val updatedAt: Long = System.currentTimeMillis(),

    /** Id of the fact that superseded this one (temporal preservation). */
    val supersededBy: String? = null,

    /** Ids of conflicting facts (structured contradiction, never silent). */
    val conflictingFactIds: List<String> = emptyList(),

    /** Link to the consolidated memory this fact maps to (01G lifecycle). */
    val memoryId: String? = null,

    /** Bounded staleness metadata. */
    val staleness: StalenessMetadata = StalenessMetadata()
) {
    /**
     * Whether this fact is the current candidate truth: it is positively
     * established (CONFIRMED / OBSERVED / INFERRED), not superseded, not
     * stale, and not contested. Contradicted facts are exposed as conflicting
     * alternatives, not current truth.
     */
    fun isCurrent(): Boolean =
        (status == ModelFactStatus.CONFIRMED ||
            status == ModelFactStatus.OBSERVED ||
            status == ModelFactStatus.INFERRED) &&
            supersededBy == null && !staleness.isStale

    /** Short summary for logging/debugging. */
    fun summary(): String =
        "ModelFact[${domain.name}:$factKey]='$value' status=${status.name} " +
            "conf=${"%.2f".format(confidence.score)} ${if (isCurrent()) "CURRENT" else "non-current"}"
}

/** The three coordinated models (kept distinct — never one giant database). */
enum class ModelDomain { SELF, USER, WORLD }

/**
 * Epistemic status of a model fact (RULE 5).
 *
 * A fact moves through these states deterministically. A fact is never
 * silently promoted from an inference to a certainty.
 */
enum class ModelFactStatus {
    /** Not established — the model explicitly does not know. */
    UNKNOWN,

    /** Directly observed (registry read, system config, environment). */
    OBSERVED,

    /** Derived from other facts/evidence, not directly observed. */
    INFERRED,

    /** Explicitly confirmed (user statement, repeated observation). */
    CONFIRMED,

    /** Conflicting evidence exists; resolution pending. */
    CONTRADICTED,

    /** Was true, now stale/superseded — historical truth preserved. */
    OUTDATED
}

/**
 * ModelEvidence - A single piece of evidence supporting a model fact.
 *
 * This build creates the contract for all sources. Only the sources that
 * exist today are actually produced; research/sensor/vision are declared for
 * future builds without being implemented.
 */
data class ModelEvidence(
    /** The source category. */
    val source: ModelEvidenceSource,

    /** Human-readable description of what was observed. */
    val description: String,

    /** Stable id of the source (capability id, memory id, config key…). */
    val sourceId: String,

    /** Strength of this piece of evidence (0.0 - 1.0). */
    val strength: Float = 0.5f,

    /** When the evidence was gathered. */
    val timestamp: Long = System.currentTimeMillis()
)

/** Categories of evidence sources. Future categories are declared as contracts. */
enum class ModelEvidenceSource {
    /** User explicitly said something. */
    EXPLICIT_USER_STATEMENT,

    /** Read from system configuration. */
    SYSTEM_CONFIGURATION,

    /** Read from the capability registry. */
    CAPABILITY_REGISTRY,

    /** Result of an execution. */
    EXECUTION_RESULT,

    /** Retrieved from the 01G memory store. */
    MEMORY,

    /** Continuity reconstruction of current truth. */
    CONTINUITY_RECONSTRUCTION,

    /** Direct environment observation. */
    ENVIRONMENT_OBSERVATION,

    /** Result of a research/information-gathering step. */
    RESEARCH_RESULT,

    /** Future sensor/vision observation (contract only). */
    SENSOR_OBSERVATION,

    /** Explicit system declaration (boot facts, manifest). */
    SYSTEM_DECLARATION
}

/**
 * ModelConfidence - Consistent confidence representation.
 *
 * Confidence is not decoration: it determines whether a fact may be treated
 * as current truth. Combines a scalar score with source reliability,
 * confirmation state, validation time and evidence/contradiction counts.
 */
data class ModelConfidence(
    /** Aggregate score (0.0 - 1.0). */
    val score: Float = 0.5f,

    /** Reliability of the underlying source (0.0 - 1.0). */
    val sourceReliability: Float = 0.5f,

    /** Confirmation state. */
    val confirmationState: ConfirmationState = ConfirmationState.UNCONFIRMED,

    /** When this fact was last independently validated. */
    val lastValidation: Long = 0,

    /** Number of supporting evidence items. */
    val evidenceCount: Int = 0,

    /** Number of contradicting evidence items. */
    val contradictionCount: Int = 0
) {
    /** Whether this confidence is strong enough to treat as current truth. */
    fun isCredible(threshold: Float = 0.6f): Boolean =
        score >= threshold && contradictionCount == 0 && confirmationState != ConfirmationState.CONTRADICTED
}

/** Confirmation state of a fact. */
enum class ConfirmationState {
    UNCONFIRMED,
    PARTIALLY_CONFIRMED,
    CONFIRMED,
    CONTRADICTED
}

/**
 * Sensitivity - Privacy classification for user-associated data.
 *
 * CRITICAL covers secrets, passwords, API keys, tokens and credentials.
 * Sensitive facts are redactable and deletable by explicit user request.
 */
enum class Sensitivity {
    NONE,
    LOW,
    HIGH,
    CRITICAL
}

/** Expected rate of change for a fact — used (with other factors) for staleness. */
enum class ExpectedChangeRate {
    STABLE,
    SLOW,
    MODERATE,
    RAPID
}

/**
 * StalenessMetadata - Bounded staleness inputs.
 *
 * Staleness is deliberately NOT decided by age alone: a stable fact (e.g. a
 * user's birth region) stays valid far longer than a rapidly changing device
 * state. The [ModelUpdateEngine] computes the stale flag from these inputs.
 */
data class StalenessMetadata(
    /** When this fact was last verified. */
    val lastVerifiedAt: Long = System.currentTimeMillis(),

    /** Expected change rate of the underlying truth. */
    val expectedChangeRate: ExpectedChangeRate = ExpectedChangeRate.STABLE,

    /** World-state volatility in this domain (0.0 stable - 1.0 volatile). */
    val worldVolatility: Float = 0.0f,

    /** Computed staleness flag (set by the update engine). */
    val isStale: Boolean = false
)

/** Result of classifying new evidence against existing truth. */
enum class UpdateClassification {
    /** Confirms an existing fact (strengthens confidence). */
    CONFIRMS,

    /** Contradicts an existing fact → structured conflict. */
    CONTRADICTS,

    /** Newer truth replaces the old one (supersession, history preserved). */
    SUPERSEDES,

    /** Adds previously-missing information. */
    ADDS,

    /** Evidence too weak to change the existing fact. */
    TOO_WEAK,

    /** Evidence is obsolete (older than current truth). */
    OBSOLETE
}
