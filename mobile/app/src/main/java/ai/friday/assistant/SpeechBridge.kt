package ai.friday.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ephemeral process-wide STT state shared by the body coordinator and conversation UI. */
object SpeechBridge {

    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastResult = MutableStateFlow("")
    val lastResult: StateFlow<String> = _lastResult.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    fun updateStatus(value: String) {
        _status.value = value
    }

    fun recordResult(text: String) {
        if (text.isNotBlank()) _lastResult.value = text
    }

    /** Partial hypotheses are transient and are never written to persistent storage. */
    fun updatePartialTranscript(text: String?) {
        _partialTranscript.value = text.orEmpty().take(500)
    }

    fun clearPartialTranscript() {
        _partialTranscript.value = ""
    }
}
