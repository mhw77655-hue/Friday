package com.jarvis.app.identity

import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.humancore.HumanCore

/**
 * Persistent, evidence-linked self-model of JARVIS's own identity, goals,
 * capabilities, limitations, uncertainty, and developmental history.
 *
 * SARSI principle held throughout: every claim the SelfModel exposes is
 * evidence-linked to a real, checkable source. It NEVER asserts something about
 * itself that isn't traceable to a system fact:
 *  - [identity]            <- live [IdentitySource] (production: [HumanCore] fields)
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
    private val goalSource: GoalSource = GoalSource { emptyList() }
) {

    /** JARVIS's fixed identity, read live from [IdentitySource] (HumanCore). */
    fun identity(): SelfIdentity {
        val name = identitySource.name() ?: "unknown"
        val version = identitySource.version() ?: 0
        return SelfIdentity(name = name, version = version)
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

    /** Current active goals, via [GoalSource] (production: CognitiveEngine tasks). */
    fun goals(): List<String> = goalSource.currentGoals()
}

/** JARVIS's stable identity as reported by [IdentitySource]. */
data class SelfIdentity(
    val name: String,
    val version: Int
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
 * Source of JARVIS's identity. Production reads real [HumanCore] fields, so
 * the SelfModel never keeps a duplicate copy.
 */
interface IdentitySource {
    /** Identity name, or null when HumanCore identity is unavailable. */
    fun name(): String?
    /** Identity version, or null when unknown/unavailable. */
    fun version(): Int?
}

/** Production [IdentitySource] that reads Live [HumanCore] snapshot fields. */
class HumanCoreIdentitySource : IdentitySource {
    override fun name(): String? = HumanCore.snapshot()?.identityName
    override fun version(): Int? = HumanCore.snapshot()?.identityVersion
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
