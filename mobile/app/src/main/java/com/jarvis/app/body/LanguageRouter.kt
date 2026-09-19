package com.jarvis.app.body

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "LanguageRouter"

/**
 * Language router for English + Egyptian Arabic + code-switching.
 *
 * Handles:
 * - Script detection (Latin vs Arabic)
 * - STT language selection
 * - TTS language/voice routing per segment
 * - Code-switching within a single utterance
 *
 * Honest about missing Arabic STT asset - falls back to platform SpeechRecognizer if available.
 */
class LanguageRouter(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    // Supported languages
    enum class Language(val code: String, val locale: Locale, val ttsLanguage: String) {
        ENGLISH("en", Locale.US, "en-US"),
        EGYPTIAN_ARABIC("ar", Locale("ar", "EG"), "ar-EG")
    }

    private val _currentLanguage = MutableStateFlow(Language.ENGLISH)
    val currentLanguage: StateFlow<Language> = _currentLanguage.asStateFlow()

    /** Detect language from text (script-based). Blank text keeps the current language. */
    fun detectLanguage(text: String): Language {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return _currentLanguage.value
        return LanguageDetection.detectLanguage(trimmed)
    }

    /** Detect language segments for code-switching. */
    fun detectSegments(text: String): List<LanguageSegment> = LanguageDetection.detectSegments(text)

    /** Get TTS voice for a language (selects best available). */
    fun getTtsVoice(language: Language, tts: TextToSpeech): android.speech.tts.Voice? {
        val voices = try { tts.voices } catch (e: Exception) { return null }
        return voices?.filter { it.locale.language == language.locale.language }
            ?.maxByOrNull { it.quality }
    }

    /** Set current language explicitly. */
    fun setLanguage(language: Language) {
        _currentLanguage.value = language
    }

    /** Get TTS locale for a language. */
    fun getTtsLocale(language: Language): Locale = language.locale
}

/** STT engine options. */
enum class SttEngine { VOSK, SHERPA_WHISPER, PLATFORM_SPEECH_RECOGNIZER }

/** STT configuration. */
data class SttConfig(
    val engine: SttEngine,
    val modelPath: String,
    val sampleRate: Int,
    val locale: Locale = Locale.US
)

/** Language segment for code-switching. */
data class LanguageSegment(
    val text: String,
    val language: LanguageRouter.Language
)