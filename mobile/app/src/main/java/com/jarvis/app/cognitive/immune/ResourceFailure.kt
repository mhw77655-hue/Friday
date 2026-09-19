package com.jarvis.app.cognitive.immune

import com.jarvis.app.cognitive.ResourceState
import com.jarvis.app.cognitive.ThermalState
import com.jarvis.app.failure.DegradationLevel

/**
 * Response hierarchy for resource pressure — the organism reduces load in
 * steps instead of crashing. Each rung is more drastic; the mapping is
 * deterministic and derived purely from [ResourceState].
 */
enum class ResourceResponse {
    NORMAL,
    REDUCE_CONCURRENCY,
    STOP_OPTIONAL_WORK,
    UNLOAD_EXPENSIVE_CAPABILITY,
    SWITCH_TO_LOWER_COST,
    PAUSE,
    REJECT
}

/**
 * Maps cognitive resource state onto the response hierarchy and the explicit
 * degradation level. Cheap and stateless — callers snapshot their own state.
 */
object ResourceFailureHandler {

    fun respond(state: ResourceState): ResourceResponse {
        val memory = state.memoryPressure
        val cpu = state.cpuPressure
        val thermal = state.thermalState

        return when {
            thermal == ThermalState.CRITICAL || memory >= 0.9f ->
                ResourceResponse.REJECT
            thermal == ThermalState.HOT || memory >= 0.8f ->
                ResourceResponse.PAUSE
            memory >= 0.7f ->
                ResourceResponse.UNLOAD_EXPENSIVE_CAPABILITY
            cpu >= 0.8f ->
                ResourceResponse.STOP_OPTIONAL_WORK
            cpu >= 0.6f || state.batteryLevel < 0.35f ->
                ResourceResponse.REDUCE_CONCURRENCY
            else -> ResourceResponse.NORMAL
        }
    }

    fun degradationLevel(state: ResourceState): DegradationLevel {
        val memory = state.memoryPressure
        val cpu = state.cpuPressure
        val thermal = state.thermalState
        return when {
            thermal == ThermalState.CRITICAL || memory >= 0.9f -> DegradationLevel.UNAVAILABLE
            thermal == ThermalState.HOT || memory >= 0.8f -> DegradationLevel.OFFLINE
            memory >= 0.7f || cpu >= 0.8f -> DegradationLevel.LIMITED
            cpu >= 0.6f || state.batteryLevel < 0.35f || memory >= 0.5f -> DegradationLevel.DEGRADED
            else -> DegradationLevel.FULL
        }
    }

    /** Compact snapshot for a failure record's resourceState field. */
    fun snapshot(state: ResourceState): String =
        "memory=${"%.2f".format(state.memoryPressure)} cpu=${"%.2f".format(state.cpuPressure)} " +
            "battery=${"%.2f".format(state.batteryLevel)} thermal=${state.thermalState.name} " +
            "net=${state.networkAvailable} storage=${state.storageAvailable}"
}
