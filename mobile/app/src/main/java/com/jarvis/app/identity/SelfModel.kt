package com.jarvis.app.identity

import com.jarvis.app.capability.CapabilityRegistry

/**
 * Persistent, evidence-linked self-model of JARVIS's own identity, goals,
 * capabilities, limitations, uncertainty, and developmental history.
 *
 * SARSI principle held throughout: every claim the SelfModel exposes is
 * evidence-linked to a real, checkable source. It NEVER asserts something about
 * itself that isn't traceable to a system fact:
 *  - [identity]            <- the owner-supplied Friday identity record (read-only on the turn path)
 *  - [capabilities]        <- live [CapabilityRegistry] (the source of truth)
 *  - [limitations]         <- derived from real registry health/state
 *  - [confidence]          <- a computed signal from live registry state
 *  - [history]             <- real stage-completion facts from a [StageHistorySource]
 *  - [goals]               <- active task goals via a [GoalSource]
 *
 * Nothing here is a hardcoded, remembered, or self-asserted list.
 */
class SelfModel(
    private val identitySource: IdentitySource,
    private val capabilityRegistry: CapabilityRegistry,
    private val stageHistory: StageHistorySource,
    private val goalSource: GoalSource = GoalSource { emptyList() },
    private val experienceStats: () -> ExperienceContinuityStats = { ExperienceContinuityStats() }
) {

    /** Friday's current identity, read live from the supplied [IdentitySource]. */
    fun identity(): SelfIdentity {
        val name = identitySource.name() ?: "unknown"
        val version = identitySource.version() ?: 0
        val pronouns = identitySource.pronouns()
        val persistentId = identitySource.persistentId()
        return SelfIdentity(name = name, version = version, pronouns = pronouns, persistentId = persistentId)
    }

    /** Exactly what the live [CapabilityRegistry] currently reports. */
    fun capabilities(): List<CapabilityRegistry.Capability> = capabilityRegistry.getAll()

    /**
     * Limitations derived from the real registry state — capabilities that are
     * unavailable (not in a loadable/active state) or degraded/unavailable by
     * health. This is derived, never a hardcoded string list.
     */
    fun limitations(): List<Limitation> {
        return capabilityRegistry.getAll().map { cap ->
            val declaredHealthy = cap.health == CapabilityRegistry.Health.HEALTHY
            val runtimeReady = cap.currentState in setOf(
                CapabilityRegistry.State.LOADED,
                CapabilityRegistry.State.IDLE,
                CapabilityRegistry.State.ACTIVE
            )
            val unavailable = !runtimeReady
            val degraded = !declaredHealthy
            Limitation(
                capabilityId = cap.id,
                unavailable = unavailable,
                degraded = degraded,
                health = cap.health,
                state = cap.currentState
            )
        }.filter { it.unavailable || it.degraded }
    }

    /**
     * A real, computed confidence/uncertainty signal per domain, derived from
     * the live registry (available vs total, adjusted by health), never a
     * hardcoded constant.
     */
    fun confidencePerDomain(category: CapabilityRegistry.Category): ConfidenceSignal {
        val all = capabilityRegistry.getByCategory(category)
        val total = all.size
        if (total == 0) return ConfidenceSignal(category, confidence = 0.0, unavailable = 0, total = 0)
        var unavailable = 0
        var degraded = 0
        for (c in all) {
            val ready = c.currentState in setOf(
                CapabilityRegistry.State.LOADED,
                CapabilityRegistry.State.IDLE,
                CapabilityRegistry.State.ACTIVE
            )
            if (!ready) unavailable++
            if (c.health != CapabilityRegistry.Health.HEALTHY) degraded++
        }
        val ready = total - unavailable
        // Confidence = fraction that is ready AND healthy, with a small health penalty.
        val healthyOfReady = (ready - degraded).coerceAtLeast(0)
        val ratio = healthyOfReady.toDouble() / total
        return ConfidenceSignal(category, confidence = ratio, unavailable = unavailable, total = total)
    }

    /**
     * Which stages/stories have actually closed — sourced from real
     * stage-completion facts (a [StageHistorySource] reading prd.json
     * states), not authored prose.
     */
    fun history(): List<StageMilestone> = stageHistory.closedMilestones()

    /** Current active goals, via [GoalSource] (production: CognitiveEngine state). */
    fun goals(): List<String> = goalSource.currentGoals()

    /**
     * A live continuity projection over existing identity, goal and experience
     * authorities. It is a read-only view, not a second memory or goal store.
     */
    fun continuitySnapshot(): SelfContinuitySnapshot = SelfContinuitySnapshot(
        identity = identity(),
        experience = experienceStats(),
        activeGoals = goals()
    )
}

/** Bounded aggregate statistics from Friday's existing model-free experience journal. */
data class ExperienceContinuityStats(
    val experienceCount: Int = 0,
    val associationCount: Int = 0,
    val latestExperienceAtMs: Long? = null
)

/** One read-only snapshot connecting Friday's stable identity to her lived history. */
data class SelfContinuitySnapshot(
    val identity: SelfIdentity,
    val experience: ExperienceContinuityStats,
    val activeGoals: List<String>
)

/** Friday's current self-identity as reported by [IdentitySource]. */
data class SelfIdentity(
    val name: String,
    val version: Int,
    val pronouns: String? = null,
    /** The canonical app-local individual ID; null only for isolated legacy test sources. */
    val persistentId: String? = null
)

/** A real limitation derived from registry state - not a fabricated string. */
data class Limitation(
    val capabilityId: String,
    val unavailable: Boolean,
    val degraded: Boolean,
    val health: CapabilityRegistry.Health,
    val state: CapabilityRegistry.State
)

/** A real computed confidence signal for one capability domain. */
data class ConfidenceSignal(
    val domain: CapabilityRegistry.Category,
    val confidence: Double,
    val unavailable: Int,
    val total: Int
)

/** A real developmental milestone: a story that reports passes:true. */
data class StageMilestone(
    val stage: String,
    val storyId: String,
    val title: String,
    val closed: Boolean
)

/**
 * Read-only source of Friday's current identity record. Revision must only be
 * changed through the owner-authorized identity administration path.
 */
interface IdentitySource {
    /** Stable identifier from FridayIndividual's durable identity record. */
    fun persistentId(): String? = null
    fun name(): String?
    fun version(): Int?
    fun pronouns(): String? = null
}

/** Initial owner-supplied identity seed; it does not expose a mutation path. */
object SeededFridayIdentitySource : IdentitySource {
    override fun name(): String = "Friday"
    override fun version(): Int = 1
    override fun pronouns(): String = "she/her"
}

/** Source of current active goals (production: CognitiveEngine TaskWorkingMemory). */
fun interface GoalSource {
    fun currentGoals(): List<String>
}

/**
 * Source of real, verifiable stage-completion facts. Production reads actual
 * prd.json states (which user stories report passes:true).
 */
fun interface StageHistorySource {
    fun closedMilestones(): List<StageMilestone>
}
