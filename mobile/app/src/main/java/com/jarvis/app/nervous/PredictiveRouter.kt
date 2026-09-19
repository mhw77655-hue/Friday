package com.jarvis.app.nervous

import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.microsystem.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Predictive Router (§9 predictive layer): predicts likely next actions and
 * *prepares* resources without performing the work.
 *
 * The router learns only from observed events (never runs continuously —
 * §18). Given a trigger capability, it predicts the capability that usually
 * follows, and — if a provider exists for it — reports it as ready. A
 * prediction is a preference, not an execution; the routing layer still makes
 * the real call when the event actually arrives. The model is deliberately a
 * small counting table, not a permanent resident model.
 */
class PredictiveRouter(private val registry: MicroSystemRegistry) {

    private val transitions = MutableStateFlow<Map<Pair<String, String>, Int>>(emptyMap())

    /** A capability that was ready when a prediction was emitted. */
    data class Prediction(val predictedCapability: String, val confidence: Double, val providerReady: Boolean)

    private val _lastPrediction = MutableStateFlow<Prediction?>(null)
    val lastPrediction: StateFlow<Prediction?> = _lastPrediction.asStateFlow()

    /** Observe an event and learn the transition. */
    fun observe(trigger: String, next: String) {
        val key = trigger to next
        transitions.value = transitions.value + (key to (transitions.value[key] ?: 0) + 1)
    }

    /** Predict the next capability after [trigger]. */
    fun predict(trigger: String): Prediction {
        val table = transitions.value
        val nexts = table.filterKeys { it.first == trigger }
            .map { (key, count) -> key.second to count }
        if (nexts.isEmpty()) return Prediction(trigger, 0.0, registry.provides(trigger).isNotEmpty())
        val total = nexts.sumOf { it.second }
        val (best, count) = nexts.maxByOrNull { it.second } ?: return Prediction(trigger, 0.0, false)
        return Prediction(
            predictedCapability = best,
            confidence = count.toDouble() / total,
            providerReady = registry.provides(best).isNotEmpty()
        )
    }

    /** Prepare resources for the predicted capability (no execution). */
    fun prepare(trigger: String): OperationResult {
        val prediction = predict(trigger)
        _lastPrediction.value = prediction
        return OperationResult(
            success = prediction.providerReady,
            data = mapOf("predictedCapability" to prediction.predictedCapability),
            confidence = prediction.confidence,
            error = if (prediction.providerReady) null else "no provider ready for ${prediction.predictedCapability}"
        )
    }
}
