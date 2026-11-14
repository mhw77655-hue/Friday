package com.friday.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LAUNCH-WAKE-ORDER AC2: Engine readiness state.
 *
 * One readiness state for the engine:
 * - [NotStarted]: init has not yet been called.
 * - [Waking]: synchronous bootstrap started and async wiring block is running.
 * - [Ready]: wiring block completed successfully.
 * - [FailedToWake]: wiring block threw; failure is recorded, not thrown uncaught.
 */
sealed class EngineReadinessState {
    object NotStarted : EngineReadinessState() {
        override fun toString(): String = "NotStarted"
    }
    object Waking : EngineReadinessState() {
        override fun toString(): String = "Waking"
    }
    object Ready : EngineReadinessState() {
        override fun toString(): String = "Ready"
    }
    data class FailedToWake(val error: Throwable) : EngineReadinessState()
}

/**
 * Typed answer returned by readiness-gated accessors.
 *
 * Before [EngineReadinessState.Ready], returns [value] as null and surfaces the current [readiness].
 * Once [EngineReadinessState.Ready], returns the non-null [value]. None throws.
 */
data class EngineSlot<T : Any>(
    val readiness: EngineReadinessState,
    val value: T?
) {
    val isReady: Boolean get() = readiness is EngineReadinessState.Ready && value != null

    fun getOrNull(): T? = value

    companion object {
        fun <T : Any> notReady(readiness: EngineReadinessState): EngineSlot<T> =
            EngineSlot(readiness, null)

        fun <T : Any> ready(value: T): EngineSlot<T> =
            EngineSlot(EngineReadinessState.Ready, value)
    }
}

/**
 * Thread-safe gate coordinating engine readiness.
 */
class EngineReadinessGate {
    private val _state = MutableStateFlow<EngineReadinessState>(EngineReadinessState.NotStarted)
    val state: StateFlow<EngineReadinessState> = _state.asStateFlow()

    val current: EngineReadinessState get() = _state.value

    fun markWaking() {
        _state.value = EngineReadinessState.Waking
    }

    fun markReady() {
        _state.value = EngineReadinessState.Ready
    }

    fun markFailed(error: Throwable) {
        _state.value = EngineReadinessState.FailedToWake(error)
    }

    /**
     * Typed accessor returning an [EngineSlot]. Never throws.
     */
    fun <T : Any> slot(provider: () -> T?): EngineSlot<T> {
        val s = _state.value
        return if (s is EngineReadinessState.Ready) {
            val v = runCatching { provider() }.getOrNull()
            if (v != null) {
                EngineSlot(EngineReadinessState.Ready, v)
            } else {
                EngineSlot(s, null)
            }
        } else {
            EngineSlot(s, null)
        }
    }

    /**
     * Resets state for testing purposes.
     */
    internal fun resetForTesting() {
        _state.value = EngineReadinessState.NotStarted
    }
}
