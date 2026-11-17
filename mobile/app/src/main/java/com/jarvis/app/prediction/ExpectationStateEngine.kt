package com.jarvis.app.prediction

import kotlin.math.exp
import kotlin.math.max

/**
 * A small, replaceable predictive-state experiment.
 *
 * It learns person-scoped situation -> successor-situation transitions from
 * observed experience. It is not a JEPA neural network; it is a cheap local
 * baseline that supplies a state/prediction contract for later Forge-trained
 * JEPA/successor-representation candidates to compete against.
 */
data class SituationState(
    val personKey: String,
    val fields: Map<String, String>
)

data class ExpectationTransition(
    val personKey: String,
    val before: Map<String, String>,
    val after: Map<String, String>,
    val observedAtMs: Long
)

data class PredictionCalibration(
    val settledSamples: Int = 0,
    val predictorAccuracyEma: Double = 0.0,
    val baselineAccuracyEma: Double = 0.0
)

data class SituationPrediction(
    val personKey: String,
    val likelyNext: Map<String, String>,
    val baselineNext: Map<String, String>,
    val confidence: Double,
    val baselineConfidence: Double,
    val evidenceCount: Int,
    val lookupCostNanos: Long,
    val reflexEligible: Boolean
)

interface ExpectationStore {
    fun loadTransitions(): List<ExpectationTransition>
    fun saveTransitions(transitions: List<ExpectationTransition>)
    fun loadCalibration(personKey: String): PredictionCalibration
    fun saveCalibration(personKey: String, calibration: PredictionCalibration)
    fun clearCalibration(personKey: String) {}
}

/**
 * Successor-state predictor with a deliberately cheap sparse feature lookup.
 * Person identity is supplied by the existing Person/Relationship Model;
 * this experiment never guesses identity or mixes people's histories.
 */
class ExpectationStateEngine(
    private val store: ExpectationStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxTransitions: Int = 512,
    private val minimumEvidence: Int = 4,
    private val reflexEvidence: Int = 12,
    private val reflexConfidence: Double = 0.82,
    private val reflexMargin: Double = 0.12
 ) {
    private val lastSituationByPerson = mutableMapOf<String, SituationState>()
    private val pendingPredictionByPerson = mutableMapOf<String, SituationPrediction>()

    /** Update the local predictive state once per real owner turn. */
    @Synchronized
    fun observeTurn(current: SituationState): SituationPrediction? {
        pendingPredictionByPerson.remove(current.personKey)?.let { settle(it, current) }
        lastSituationByPerson[current.personKey]?.let { recordTransition(it, current) }
        lastSituationByPerson[current.personKey] = current
        val prediction = predict(current)
        if (prediction != null) pendingPredictionByPerson[current.personKey] = prediction
        return prediction
    }

    /** Clear this person's predictive history as part of a broad forget/reset operation. */
    @Synchronized
    fun clearPerson(personKey: String) {
        store.saveTransitions(store.loadTransitions().filterNot { it.personKey == personKey })
        store.clearCalibration(personKey)
        lastSituationByPerson.remove(personKey)
        pendingPredictionByPerson.remove(personKey)
    }

    @Synchronized
    fun recordTransition(before: SituationState, after: SituationState) {
        require(before.personKey.isNotBlank()) { "A verified person key is required." }
        require(before.personKey == after.personKey) { "A transition cannot cross relationship boundaries." }
        val safeBefore = sanitize(before.fields)
        val safeAfter = sanitize(after.fields)
        if (safeBefore.isEmpty() || safeAfter.isEmpty()) return
        val next = (store.loadTransitions() + ExpectationTransition(
            personKey = before.personKey,
            before = safeBefore,
            after = safeAfter,
            observedAtMs = clock()
        )).takeLast(maxTransitions.coerceAtLeast(1))
        store.saveTransitions(next)
    }

    @Synchronized
    fun predict(current: SituationState): SituationPrediction? {
        val started = System.nanoTime()
        if (current.personKey.isBlank()) return null
        val now = clock()
        val features = sanitize(current.fields)
        if (features.isEmpty()) return null

        val transitions = store.loadTransitions().filter { it.personKey == current.personKey }
        if (transitions.size < minimumEvidence) return null

        data class Candidate(var weight: Double = 0.0, var evidence: Int = 0, val fields: Map<String, String>)
        val candidates = linkedMapOf<String, Candidate>()
        val baselineCounts = linkedMapOf<String, Pair<Int, Map<String, String>>>()

        transitions.forEach { transition ->
            val similarity = similarity(features, transition.before)
            val ageDays = ((now - transition.observedAtMs).coerceAtLeast(0L) / DAY_MS).toDouble()
            val recency = exp(-ageDays / RECENCY_HALF_LIFE_DAYS)
            val weight = similarity * recency
            val signature = signature(transition.after)
            val existing = candidates[signature] ?: Candidate(fields = transition.after).also {
                candidates[signature] = it
            }
            if (weight > 0.0) {
                existing.weight += weight
                existing.evidence += 1
            }
            val baseline = baselineCounts[signature]
            baselineCounts[signature] = ((baseline?.first ?: 0) + 1) to transition.after
        }

        val best = candidates.values
            .filter { it.evidence >= minimumEvidence }
            .maxByOrNull { it.weight } ?: return null
        val totalWeight = candidates.values.sumOf { it.weight }.coerceAtLeast(0.000001)
        val confidence = (best.weight / totalWeight).coerceIn(0.0, 1.0)
        val totalBaseline = baselineCounts.values.sumOf { it.first }.coerceAtLeast(1)
        val baselineEntry = baselineCounts.values.maxByOrNull { it.first }
        val baselineConfidence = (baselineEntry?.first ?: 0).toDouble() / totalBaseline
        val baselineNext = baselineEntry?.second ?: emptyMap()
        val calibration = store.loadCalibration(current.personKey)
        val cost = System.nanoTime() - started
        val eligible = calibration.settledSamples >= reflexEvidence &&
            best.evidence >= reflexEvidence &&
            confidence >= reflexConfidence &&
            confidence - baselineConfidence >= reflexMargin &&
            calibration.predictorAccuracyEma > calibration.baselineAccuracyEma + reflexMargin

        return SituationPrediction(
            personKey = current.personKey,
            likelyNext = best.fields,
            baselineNext = baselineNext,
            confidence = confidence,
            baselineConfidence = baselineConfidence,
            evidenceCount = best.evidence,
            lookupCostNanos = cost,
            reflexEligible = eligible
        )
    }

    /** Settle a prediction against what actually followed; wrong guesses count. */
    @Synchronized
    fun settle(prediction: SituationPrediction, actualNext: SituationState) {
        require(prediction.personKey == actualNext.personKey) {
            "Prediction settlement cannot cross relationship boundaries."
        }
        val old = store.loadCalibration(prediction.personKey)
        val alpha = if (old.settledSamples < 8) 0.25 else 0.08
        val predictedScore = similarity(prediction.likelyNext, sanitize(actualNext.fields))
        val baselineScore = similarity(prediction.baselineNext, sanitize(actualNext.fields))
        val next = PredictionCalibration(
            settledSamples = old.settledSamples + 1,
            predictorAccuracyEma = if (old.settledSamples == 0) predictedScore
                else old.predictorAccuracyEma * (1.0 - alpha) + predictedScore * alpha,
            baselineAccuracyEma = if (old.settledSamples == 0) baselineScore
                else old.baselineAccuracyEma * (1.0 - alpha) + baselineScore * alpha
        )
        store.saveCalibration(prediction.personKey, next)
    }

    fun calibration(personKey: String): PredictionCalibration =
        store.loadCalibration(personKey)

    private fun sanitize(fields: Map<String, String>): Map<String, String> =
        fields.entries.asSequence()
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(MAX_FEATURES)
            .associate { it.key.take(64) to it.value.take(128) }

    private fun similarity(a: Map<String, String>, b: Map<String, String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val keys = a.keys union b.keys
        val matched = keys.count { key -> a[key] != null && a[key] == b[key] }
        return matched.toDouble() / max(1, keys.size)
    }

    private fun signature(fields: Map<String, String>): String =
        fields.toSortedMap().entries.joinToString("|") {
            "${it.key.length}:${it.key}=${it.value.length}:${it.value}"
        }

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val RECENCY_HALF_LIFE_DAYS = 14.0
        private const val MAX_FEATURES = 24
    }
}
