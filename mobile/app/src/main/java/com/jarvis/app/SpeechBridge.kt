package com.jarvis.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide diagnostics bridge for the platform STT recognizer
 * (android.speech.SpeechRecognizer, wrapped by JarvisSpeechRecognizer in
 * :app). Mirrors TtsBridge's pattern: the coordinator pushes status + the last
 * transcribed utterance here so the diagnostics screen and health text stay
 * live without holding a direct reference to the recognizer instance.
 */
object SpeechBridge {

    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastResult = MutableStateFlow("")
    val lastResult: StateFlow<String> = _lastResult.asStateFlow()

    fun updateStatus(value: String) {
        _status.value = value
    }

    fun recordResult(text: String) {
        if (text.isNotBlank()) _lastResult.value = text
    }
}