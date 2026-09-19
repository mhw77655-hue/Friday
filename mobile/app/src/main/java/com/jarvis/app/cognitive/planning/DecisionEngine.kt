package com.jarvis.app.cognitive.planning

import com.jarvis.app.cognitive.CapabilityState
import com.jarvis.app.cognitive.CognitiveState
import com.jarvis.app.cognitive.DecisionFactor
import com.jarvis.app.cognitive.DecisionOption
import com.jarvis.app.cognitive.DecisionReason
import com.jarvis.app.cognitive.DecisionRecord
import com.jarvis.app.cognitive.DecisionStateSnapshot
import com.jarvis.app.cognitive.DecisionEvaluationType
import com.jarvis.app.cognitive.DecisionOutcome
import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.ResourceState
import com.jarvis.app.cognitive.UncertaintyProfile

/** A declarative constraint a decision option must satisfy. */
data class DecisionConstraint(
    val name: String,
    val kind: ConstraintKind,
    /** RESOURCE: which resource (cpu / memory / battery). */
    val resource: String? = null,
    /** RESOURCE: maximum allowed usage (0..1) for that resource. */
    val maxUsage: Float? = null,
    /** CAPABILITY: the capability in question (stt / tts / vision / model / tool). */
    val capability: String? = null,
    /** CAPABILITY: require the capability to be ABSENT (e.g. "must not use model"). */
    val requireAbsent: Boolean = false,
    /** TAG: option.constraints must not contain this tag (privacy/safety). */
    val forbidTag: String? = null
)

enum class ConstraintKind { RESOURCE, CAPABILITY, TAG }

/** Knowledge source that proposes candidate [DecisionOption]s for a request. */
fun interface OptionGenerator {
    fun generate(request: DecisionRequest): List<DecisionOption>
}

data class DecisionWeights(
    val goalAlignment: Float = 0.30f,
    val outcome: Float = 0.20f,
    val risk: Float = 0.20f,
    val uncertainty: Float = 0.10f,
    val reversibility: Float = 0.10f,
    val effort: Float = 0.10f
) {
    init {
        require(
            goalAlignment + outcome + risk + uncertainty + reversibility + effort > 0.99f &&
                goalAlignment + outcome + risk + uncertainty + reversibility + effort <= 1.01f
        ) { "decision weights must sum to 1.0" }
    }
}

data class DecisionRequest(
    val context: String,
    val goal: Goal? = null,
    val options: List<DecisionOption> = emptyList(),
    val constraints: List<DecisionConstraint> = emptyList(),
    val uncertainty: UncertaintyProfile = UncertaintyProfile(),
    val resourceState: ResourceState = ResourceState(),
    val capabilityState: CapabilityState = CapabilityState(),
    val cognitiveState: CognitiveState? = null,
    val weights: DecisionWeights = DecisionWeights()
)

/** A single option with its computed score and feasibility verdict. */
data class ScoredOption(
    val option: DecisionOption,
    val score: Float,
    val factorScores: Map<DecisionFactor, Float>,
    val feasible: Boolean,
    val rejectionReason: String? = null
)

data class DecisionResult(
    val record: DecisionRecord?,
    val ranked: List<ScoredOption>,
    val alternatives: List<DecisionOption>,
    val reasoning: DecisionReason?
) {
    val made: Boolean get() = record != null
    /** The chosen option, when a decision was made. */
    val chosen: DecisionOption? get() = record?.chosen
}

/**
 * DecisionEngine — evaluates decision options against goal alignment,
 * constraints, uncertainty, available resources, capability availability,
 * expected outcome, risk, effort, and reversibility; ranks them; selects the
 * best feasible option; and records a [DecisionRecord] carrying structured
 * reason metadata (no prose required).
 *
 * Deterministic: the same request always yields the same ranking and choice.
 * Options are never invented — an [OptionGenerator] supplies candidates.
 */
class DecisionEngine(
    private val generator: OptionGenerator? = null,
    private val weights: DecisionWeights = DecisionWeights()
) {

    /** Evaluate and decide. When no feasible option exists, no decision is made. */
    fun decide(request: DecisionRequest): DecisionResult {
        val candidates = (generator?.generate(request) ?: emptyList()) + request.options
        val evaluated = candidates.map { score(it, request) }
        val ranked = evaluated.sortedWith(
            compareByDescending<ScoredOption> { it.feasible }
                .thenByDescending { it.score }
                .thenBy { it.option.id }
        )

        val feasible = ranked.filter { it.feasible }
        val best = feasible.firstOrNull()
        if (best == null) {
            return DecisionResult(
                record = null,
                ranked = ranked,
                alternatives = emptyList(),
                reasoning = null
            )
        }

        val rejectedIds = ranked.filter { !it.feasible }.map { it.option.id }
        val reason = DecisionReason(
            totalScore = best.score,
            factorScores = best.factorScores,
            appliedConstraints = request.constraints.map { it.name },
            uncertaintyAtDecision = request.uncertainty.overall,
            resourcePressureAtDecision = request.resourceState.isUnderPressure().let { if (it) 1f else 0f },
            degradedCapabilitiesAtDecision = request.capabilityState.degradedCapabilities,
            rejectedOptions = rejectedIds,
            evaluationType = DecisionEvaluationType.WEIGHTED
        )

        val record = DecisionRecord(
            id = "decision_${request.context.hashCode().toUInt()}_${best.option.id}",
            context = request.context,
            options = ranked.map { it.option },
            chosen = best.option,
            rationale = summarize(best, reason, request),
            confidence = best.score * (1f - request.uncertainty.overall * 0.5f),
            outcome = null,
            reasonMetadata = reason,
            stateSnapshot = request.cognitiveState?.let { DecisionStateSnapshot.from(it) },
            expectedOutcome = best.option.predictedOutcome
        )

        val alternatives = ranked.filter { it.option.id != best.option.id }.map { it.option }
        return DecisionResult(record = record, ranked = ranked, alternatives = alternatives, reasoning = reason)
    }

    /** Rank only (no selection) — deterministic, same as decide(). */
    fun rank(request: DecisionRequest): List<ScoredOption> {
        val candidates = (generator?.generate(request) ?: emptyList()) + request.options
        return candidates.map { score(it, request) }
            .sortedWith(
                compareByDescending<ScoredOption> { it.feasible }
                    .thenByDescending { it.score }
                    .thenBy { it.option.id }
            )
    }

    // ------------------------------------------------------------------

    private fun score(option: DecisionOption, request: DecisionRequest): ScoredOption {
        val factorScores = mutableMapOf<DecisionFactor, Float>()
        var reasons = mutableListOf<String>()

        factorScores[DecisionFactor.GOAL_ALIGNMENT] = option.alignment.coerceIn(0f, 1f)
        factorScores[DecisionFactor.OUTCOME] = option.confidence.coerceIn(0f, 1f)
        factorScores[DecisionFactor.RISK] = (1f - option.risk).coerceIn(0f, 1f)
        factorScores[DecisionFactor.UNCERTAINTY] = (1f - request.uncertainty.overall).coerceIn(0f, 1f)
        factorScores[DecisionFactor.REVERSIBILITY] = option.reversibility.coerceIn(0f, 1f)
        factorScores[DecisionFactor.EFFORT] = (1f - option.effort.coerceIn(0f, 1f))

        // Feasibility: constraints + capability availability + resources.
        for (constraint in request.constraints) {
            val violation = violationReason(option, constraint, request)
            if (violation != null) {
                reasons.add(violation)
            }
        }
        val capabilityGap = missingCapabilities(option, request.capabilityState)
        if (capabilityGap.isNotEmpty()) {
            reasons.add("missing capability: ${capabilityGap.joinToString()}")
        }

        if (reasons.isEmpty()) {
            val score =
                factorScores[DecisionFactor.GOAL_ALIGNMENT]!! * weights.goalAlignment +
                    factorScores[DecisionFactor.OUTCOME]!! * weights.outcome +
                    factorScores[DecisionFactor.RISK]!! * weights.risk +
                    factorScores[DecisionFactor.UNCERTAINTY]!! * weights.uncertainty +
                    factorScores[DecisionFactor.REVERSIBILITY]!! * weights.reversibility +
                    factorScores[DecisionFactor.EFFORT]!! * weights.effort
            return ScoredOption(option, score, factorScores, feasible = true)
        }
        return ScoredOption(option, 0f, factorScores, feasible = false, rejectionReason = reasons.joinToString("; "))
    }

    private fun violationReason(option: DecisionOption, constraint: DecisionConstraint, request: DecisionRequest): String? {
        return when (constraint.kind) {
            ConstraintKind.RESOURCE -> {
                val resource = constraint.resource ?: return null
                val required = option.resourceRequirements[resource] ?: 0f
                val ceiling = constraint.maxUsage ?: 1f
                if (required > ceiling) "violates $resource budget (needs $required > $ceiling)" else null
            }
            ConstraintKind.CAPABILITY -> {
                val cap = constraint.capability ?: return null
                val requires = option.capabilityRequirements.contains(cap)
                if (constraint.requireAbsent && requires) "must not use $cap" else null
            }
            ConstraintKind.TAG -> {
                val tag = constraint.forbidTag ?: return null
                if (option.constraints.contains(tag)) "forbidden tag $tag" else null
            }
        }
    }

    private fun missingCapabilities(option: DecisionOption, capabilityState: CapabilityState): List<String> {
        val available = capabilitiesAvailable(capabilityState)
        return option.capabilityRequirements.filterNot { it in available }
    }

    /** Capabilities currently available, derived from the capability state. */
    fun capabilitiesAvailable(capabilityState: CapabilityState): Set<String> {
        val degraded = capabilityState.degradedCapabilities.toSet()
        val available = mutableSetOf<String>()
        if (capabilityState.sttAvailable && "stt" !in degraded) available.add("stt")
        if (capabilityState.ttsAvailable && "tts" !in degraded) available.add("tts")
        if (capabilityState.visionAvailable && "vision" !in degraded) available.add("vision")
        if (capabilityState.modelLoaded && "model" !in degraded) available.add("model")
        capabilityState.toolsAvailable.filterNot { it in degraded }.forEach { available.add(it) }
        return available
    }

    private fun summarize(
        best: ScoredOption,
        reason: DecisionReason,
        request: DecisionRequest
    ): String {
        val factors = reason.factorScores.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { (k, v) -> "${k.name}=${"%.2f".format(v)}" }
        return "chose ${best.option.id} (score ${"%.2f".format(best.score)}) " +
            "under uncertainty ${"%.2f".format(request.uncertainty.overall)}; top factors: $factors"
    }
}
