package com.jarvis.app.cognitive.model

/**
 * SelfModel - Structured, evidence-backed description of Jarvis as an
 * operating entity: who he is, what he is, what he can do, what he cannot,
 * what he knows/doesn't know, and his active state.
 *
 * Deliberate boundaries:
 * - Capabilities are a PROJECTION of the existing CapabilityRegistry (id +
 *   purpose + availability + confidence + known limitations). No duplicate
 *   registry.
 * - Active state REFERENCES CognitiveState / the goal system. No duplicate
 *   runtime state.
 * - Every fact in [facts] carries evidence + confidence + epistemic status.
 *
 * The model is an immutable snapshot produced by [ModelUpdateEngine]. All
 * mutation goes through the update engine — nothing here is mutated in place.
 */
data class SelfModel(
    /** Identity — who Jarvis is. */
    val identity: SelfIdentity = SelfIdentity(),

    /** Nature — what Jarvis is and what he operates on. */
    val nature: SelfNature = SelfNature(),

    /** Capability projection (references CapabilityRegistry ids). */
    val capabilities: List<SelfCapability> = emptyList(),

    /** Explicit limitations. */
    val limitations: List<SelfLimitation> = emptyList(),

    /** Known resources (references; not a duplicate of ResourceGovernor). */
    val resources: List<SelfResource> = emptyList(),

    /** Knowledge state — known / unknown / uncertain / stale. */
    val knowledgeState: SelfKnowledgeState = SelfKnowledgeState(),

    /** Active state — references to runtime, not duplicated state. */
    val activeState: SelfActiveState = SelfActiveState(),

    /** Evidence-backed facts (by stable fact key — the current candidate). */
    val facts: Map<String, ModelFact> = emptyMap(),

    /** Non-current facts (superseded / contradicted / outdated) preserved in
     *  deterministic order — history is never overwritten silently. */
    val factHistory: List<ModelFact> = emptyList()
) {
    /** Look up a fact by its stable key. */
    fun fact(factKey: String): ModelFact? = facts[factKey]

    /** Facts that are current truth. */
    fun currentFacts(): List<ModelFact> = facts.values.filter { it.isCurrent() }

    /** Summary line for logging. */
    fun summary(): String =
        "SelfModel[${identity.canonicalName} v${identity.version} gen=${identity.systemGeneration}] " +
            "caps=${capabilities.size} limitations=${limitations.size} " +
            "known=${knowledgeState.knownCount} unknown=${knowledgeState.unknownCount} " +
            "uncertain=${knowledgeState.uncertainCount} stale=${knowledgeState.staleCount}"
}

/** Self identity. No identity information is assumed — declared or defaulted. */
data class SelfIdentity(
    val identityId: String = "jarvis",
    val canonicalName: String = "JARVIS",
    val aliases: List<String> = emptyList(),
    val version: String = "0.1.0",
    val systemGeneration: String = "01H",
    val identityState: String = "BOOTED"
)

/** What Jarvis is, and the constraints under which he operates. */
data class SelfNature(
    val what: String = "an offline cognitive assistant subsystem",
    val architectureIdentity: String = "com.jarvis.app.cognitive.model",
    val operatingConstraints: List<String> = emptyList(),
    val supportedEnvironments: List<String> = emptyList()
)

/**
 * SelfCapability - A projection of a registered capability.
 *
 * References [com.jarvis.app.cognitive.capability.CapabilityRegistry] by id;
 * it does NOT duplicate the registry. `availability` mirrors the capability's
 * lifecycle state name (AVAILABLE/DEGRADED/UNAVAILABLE/…) at observation time.
 */
data class SelfCapability(
    /** Registry capability id. */
    val capabilityId: String,

    /** Short purpose statement. */
    val purpose: String,

    /** Lifecycle availability name from the registry (projection). */
    val availability: String,

    /** Confidence that the projection is accurate (0.0 - 1.0). */
    val confidence: Float,

    /** Known limitations of this capability. */
    val knownLimitations: List<String> = emptyList(),

    val observedAt: Long = System.currentTimeMillis()
)

/** Types of explicit self limitations. */
enum class SelfLimitationType {
    UNAVAILABLE_MODEL,
    UNAVAILABLE_ENVIRONMENT,
    MISSING_DEPENDENCY,
    UNSUPPORTED_OPERATION,
    RESOURCE_CONSTRAINT
}

data class SelfLimitation(
    val limitationType: SelfLimitationType,
    val description: String,
    val source: String,
    val observedAt: Long = System.currentTimeMillis()
)

/** Kinds of resources the self model may reference. */
enum class SelfResourceKind {
    COMPUTE,
    MEMORY,
    STORAGE,
    MODEL,
    ENVIRONMENT,
    RUNTIME_ATTACHMENT
}

data class SelfResource(
    val kind: SelfResourceKind,
    val name: String,
    val state: String,
    val confidence: Float,
    val observedAt: Long = System.currentTimeMillis()
)

/**
 * SelfKnowledgeState - The explicit "I know / I don't know" posture.
 *
 * The goal is for Jarvis to say «"I don't know."» instead of fabricating.
 */
data class SelfKnowledgeState(
    val knownCount: Int = 0,
    val unknownCount: Int = 0,
    val uncertainCount: Int = 0,
    val staleCount: Int = 0
)

/** Active state — references, not duplicated runtime state. */
data class SelfActiveState(
    val activeGoalIds: List<String> = emptyList(),
    val currentTask: String? = null,
    val currentEnvironment: String? = null,
    val currentMode: String? = null,
    /** Reference to the authoritative CognitiveState (not a copy). */
    val cognitiveStateId: String? = null
)
