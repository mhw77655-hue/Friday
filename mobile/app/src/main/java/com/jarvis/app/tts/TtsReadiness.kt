package com.jarvis.app.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Readiness states for a TTS engine instance.
 *
 * One readiness state per process TTS owner:
 * - [NotStarted]: TTS initialization has not been requested yet.
 * - [Initializing]: TextToSpeech connection is being bound/initialized.
 * - [Ready]: TextToSpeech initialization succeeded (OnInitListener returned SUCCESS) and language/voice is configured.
 * - [Failed]: Initialization failed (OnInitListener returned non-SUCCESS or language unsupported), or engine threw.
 */
sealed class TtsReadinessState {
    object NotStarted : TtsReadinessState() {
        override fun toString(): String = "NotStarted"
    }
    object Initializing : TtsReadinessState() {
        override fun toString(): String = "Initializing"
    }
    object Ready : TtsReadinessState() {
        override fun toString(): String = "Ready"
    }
    data class Failed(val error: Throwable) : TtsReadinessState()
}

/**
 * Typed answer returned by TTS readiness-gated accessors.
 *
 * Before [TtsReadinessState.Ready], returns [value] as null and surfaces the current [readiness].
 * Once [TtsReadinessState.Ready], returns the non-null [value]. None throws.
 */
data class TtsSlot<T : Any>(
    val readiness: TtsReadinessState,
    val value: T?
) {
    val isReady: Boolean get() = readiness is TtsReadinessState.Ready && value != null

    fun getOrNull(): T? = value

    companion object {
        fun <T : Any> notReady(readiness: TtsReadinessState): TtsSlot<T> =
            TtsSlot(readiness, null)

        fun <T : Any> ready(value: T): TtsSlot<T> =
            TtsSlot(TtsReadinessState.Ready, value)
    }
}

/**
 * Thread-safe gate coordinating TTS readiness.
 */
class TtsReadinessGate {
    private val _state = MutableStateFlow<TtsReadinessState>(TtsReadinessState.NotStarted)
    val state: StateFlow<TtsReadinessState> = _state.asStateFlow()

    val current: TtsReadinessState get() = _state.value

    fun markInitializing() {
        _state.value = TtsReadinessState.Initializing
    }

    fun markReady() {
        _state.value = TtsReadinessState.Ready
    }

    fun markFailed(error: Throwable) {
        _state.value = TtsReadinessState.Failed(error)
    }

    /**
     * Typed accessor returning a [TtsSlot]. Never throws.
     */
    fun <T : Any> slot(provider: () -> T?): TtsSlot<T> {
        val s = _state.value
        return if (s is TtsReadinessState.Ready) {
            val v = runCatching { provider() }.getOrNull()
            if (v != null) {
                TtsSlot(TtsReadinessState.Ready, v)
            } else {
                TtsSlot(s, null)
            }
        } else {
            TtsSlot(s, null)
        }
    }

    /**
     * Resets state for testing purposes.
     */
    internal fun resetForTesting() {
        _state.value = TtsReadinessState.NotStarted
    }
}
