package com.jarvis.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.jarvis.app.JarvisTts
import com.jarvis.app.body.StreamingTts
import com.jarvis.app.body.VocabularyStore
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * TTSEngine - Abstraction for TTS engines
 */
sealed interface TTSEngine {
    val id: String
    val name: String
    val supportedLanguages: Set<String>
    val quality: Quality
    val isStreaming: Boolean
    val estimatedLatencyMs: Long
    val ramEstimateMb: Long

    enum class Quality { LOW, MEDIUM, HIGH, BEST }

    /** Initialize engine */
    suspend fun initialize(): Boolean

    /** Speak text (non-streaming) */
    suspend fun speak(text: String, language: String, params: SpeakParams): SpeakResult

    /** Stream text (for sentence-level streaming) */
    fun stream(text: String, language: String, params: SpeakParams): TtsStream

    /** Interrupt current speech */
    fun interrupt()

    /** Check if speaking */
    val isSpeaking: Boolean

    /** Get available voices for language */
    fun getVoices(language: String): List<VoiceInfo>

    /** Shutdown */
    fun shutdown()

    /** Health check */
    suspend fun healthCheck(): HealthStatus
}

/** TTS voice info */
data class VoiceInfo(
    val name: String,
    val locale: Locale,
    val quality: TTSEngine.Quality,
    val isNetworkRequired: Boolean,
    val features: Set<String> = emptySet()
)

/** Speak parameters */
data class SpeakParams(
    val pitch: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val volume: Float = 1.0f,
    val vocabularyHints: Map<String, String> = emptyMap(), // word -> pronunciation
    val useSsml: Boolean = false,
    val utteranceId: String = "tts_${System.currentTimeMillis()}"
)

/** Speak result */
sealed interface SpeakResult {
    data class Success(val audioFile: File?, val durationMs: Long) : SpeakResult
    data class Failure(val error: String) : SpeakResult
}

/** Streaming TTS interface */
interface TtsStream {
    /** Collect audio chunks */
    fun <T> fold(initial: T, operation: (T, AudioChunk) -> T): T

    /** Cancel stream */
    fun cancel()
}

/** Audio chunk for streaming */
data class AudioChunk(
    val audioData: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val isFinal: Boolean,
    val timestampMs: Long
)

/** Health status */
data class HealthStatus(
    val isHealthy: Boolean,
    val latencyMs: Long,
    val error: String? = null
)

/** System TTS Engine (Android TextToSpeech) */
class SystemTTSEngine(
    private val context: Context,
    private val onFailure: (FailureReport) -> Unit = {}
) : TTSEngine {

    override val id = "system_tts"
    override val name = "Android System TTS"
    override val supportedLanguages = setOf("en", "en-US", "en-GB", "ar", "ar-EG", "ar-SA")
    override val quality = TTSEngine.Quality.HIGH
    override val isStreaming = false
    override val estimatedLatencyMs = 300L
    override val ramEstimateMb = 20L

    private var tts: TextToSpeech? = null
    private var currentUtteranceId: String? = null
    private var _isSpeaking = false
    private val initLock = Any()

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        val alreadyInitialized = synchronized(initLock) {
            if (tts != null) true else {
                // TextToSpeech must be created on main thread for initialization
                // We'll use a callback approach
                val completer = kotlinx.coroutines.CompletableDeferred<Boolean>()

                val ttsInstance = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        Log.i("SystemTTSEngine", "TTS initialized successfully")
                        completer.complete(true)
                    } else {
                        Log.e("SystemTTSEngine", "TTS init failed: $status")
                        completer.complete(false)
                    }
                }

                tts = ttsInstance
                completer
            }
        }

        // Await outside the lock so the suspend point never holds the critical section.
        (alreadyInitialized as? kotlinx.coroutines.CompletableDeferred<Boolean>)?.await()
            ?: true
    }

    override suspend fun speak(text: String, language: String, params: SpeakParams): SpeakResult = withContext(Dispatchers.IO) {
        tts?.let { engine ->
            val locale = Locale.forLanguageTag(language)
            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                return@withContext SpeakResult.Failure("Language not supported: $language")
            }

            engine.setPitch(params.pitch)
            engine.setSpeechRate(params.speechRate)

            val utteranceId = params.utteranceId
            currentUtteranceId = utteranceId
            _isSpeaking = true

            val paramsBundle = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, params.volume)
            }

            // Apply vocabulary hints via SSML if provided
            val finalText = if (params.vocabularyHints.isNotEmpty() && params.useSsml) {
                applyVocabularyHints(text, params.vocabularyHints)
            } else text

            val resultCode = engine.speak(finalText, TextToSpeech.QUEUE_FLUSH, paramsBundle, utteranceId)

            if (resultCode != TextToSpeech.SUCCESS) {
                _isSpeaking = false
                return@withContext SpeakResult.Failure("Speak failed with code: $resultCode")
            }

            // Wait for completion (simplified - in production use UtteranceProgressListener)
            // For now, estimate duration
            val estimatedDuration = (text.length * 50L / params.speechRate.toLong()).coerceAtLeast(500)
            Thread.sleep(estimatedDuration)
            _isSpeaking = false

            SpeakResult.Success(null, estimatedDuration)
        } ?: SpeakResult.Failure("TTS not initialized")
    }

    override fun stream(text: String, language: String, params: SpeakParams): TtsStream {
        // System TTS doesn't support true streaming - fall back to speak
        return object : TtsStream {
            override fun <T> fold(initial: T, operation: (T, AudioChunk) -> T): T {
                // Not truly streaming
                return initial
            }

            override fun cancel() {
                interrupt()
            }
        }
    }

    override fun interrupt() {
        tts?.stop()
        _isSpeaking = false
    }

    override val isSpeaking: Boolean
        get() = _isSpeaking

    override fun getVoices(language: String): List<VoiceInfo> {
        return tts?.voices?.filter { it.locale.language == language.take(2) }
            ?.map { VoiceInfo(it.name, it.locale, TTSEngine.Quality.HIGH, it.isNetworkConnectionRequired) }
            ?.toList() ?: emptyList()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    override suspend fun healthCheck(): HealthStatus = withContext(Dispatchers.IO) {
        HealthStatus(
            isHealthy = tts != null,
            latencyMs = estimatedLatencyMs,
            error = if (tts == null) "Not initialized" else null
        )
    }

    private fun applyVocabularyHints(text: String, hints: Map<String, String>): String {
        var result = text
        for ((word, pronunciation) in hints) {
            // Wrap in SSML phoneme tag
            val pattern = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
            result = pattern.replace(result, "<phoneme alphabet=\"ipa\" ph=\"$pronunciation\">$word</phoneme>")
        }
        return "<speak>$result</speak>"
    }
}

/** Streaming TTS Engine (wraps existing StreamingTts) */
class StreamingTTSEngine(
    private val streamingTts: StreamingTts,
    private val vocabularyStore: VocabularyStore
) : TTSEngine {

    override val id = "streaming_tts"
    override val name = "Jarvis Streaming TTS"
    override val supportedLanguages = setOf("en", "en-US", "ar", "ar-EG")
    override val quality = TTSEngine.Quality.BEST
    override val isStreaming = true
    override val estimatedLatencyMs = 150L
    override val ramEstimateMb = 30L

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        // StreamingTts is initialized by BodyCoordinator
        true
    }

    override suspend fun speak(text: String, language: String, params: SpeakParams): SpeakResult = withContext(Dispatchers.IO) {
        // Apply vocabulary hints
        val finalText = vocabularyStore.applyPronunciationHints(text)
        streamingTts.speak(finalText, mapLanguage(language))
        // StreamingTts is async - return success immediately
        SpeakResult.Success(null, 0)
    }

    override fun stream(text: String, language: String, params: SpeakParams): TtsStream {
        val finalText = vocabularyStore.applyPronunciationHints(text)
        streamingTts.speakStreaming(finalText, vocabularyStore)
        return object : TtsStream {
            override fun <T> fold(initial: T, operation: (T, AudioChunk) -> T): T = initial
            override fun cancel() = interrupt()
        }
    }

    override fun interrupt() {
        streamingTts.interrupt()
    }

    override val isSpeaking: Boolean
        get() = streamingTts.isSpeaking.value

    /** Map a language code to the internal Language enum, or null if unsupported. */
    private fun mapLanguage(language: String): com.jarvis.app.body.LanguageRouter.Language? = when {
        language.startsWith("ar") -> com.jarvis.app.body.LanguageRouter.Language.EGYPTIAN_ARABIC
        else -> com.jarvis.app.body.LanguageRouter.Language.ENGLISH
    }

    override fun getVoices(language: String): List<VoiceInfo> = emptyList()

    override fun shutdown() {
        streamingTts.shutdown()
    }

    override suspend fun healthCheck(): HealthStatus = withContext(Dispatchers.IO) {
        HealthStatus(
            isHealthy = streamingTts.status.value != "error",
            latencyMs = estimatedLatencyMs,
            error = if (streamingTts.status.value == "error") streamingTts.status.value else null
        )
    }
}

/** TTSEngineRegistry - Manages available TTS engines */
class TTSEngineRegistry(
    private val context: Context,
    private val vocabularyStore: VocabularyStore,
    private val streamingTts: StreamingTts,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val engines = ConcurrentHashMap<String, TTSEngine>()
    private var currentEngineId: String = "system_tts"

    private val _currentEngine = MutableStateFlow<TTSEngine?>(null)
    val currentEngine: StateFlow<TTSEngine?> = _currentEngine.asStateFlow()

    init {
        registerEngine(SystemTTSEngine(context))
        registerEngine(StreamingTTSEngine(streamingTts, vocabularyStore))
    }

    fun registerEngine(engine: TTSEngine) {
        engines[engine.id] = engine
    }

    suspend fun initializeAll(): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        for (engine in engines.values) {
            val success = engine.initialize()
            if (!success) {
                Log.w("TTSEngineRegistry", "Failed to initialize ${engine.name}")
                allSuccess = false
            }
        }

        // Set default engine
        if (engines.containsKey(currentEngineId)) {
            _currentEngine.value = engines[currentEngineId]
        }

        return@withContext allSuccess
    }

    /** Select best engine for language and quality (non-suspend; health checked lazily). */
    fun selectBestEngine(language: String, minQuality: TTSEngine.Quality = TTSEngine.Quality.MEDIUM): TTSEngine? {
        return engines.values
            .filter { it.supportedLanguages.contains(language) }
            .filter { it.quality.ordinal >= minQuality.ordinal }
            .maxByOrNull { it.quality.ordinal }
    }

    /** Get current engine */
    fun getCurrentEngine(): TTSEngine? = engines[currentEngineId]

    /** Set current engine */
    fun setCurrentEngine(engineId: String): Boolean {
        return engines[engineId]?.also { _currentEngine.value = it } != null
    }

    /** Get engine by ID */
    fun getEngine(engineId: String): TTSEngine? = engines[engineId]

    /** Get all engines */
    fun getAllEngines(): List<TTSEngine> = engines.values.toList()

    /** Speak with automatic engine selection */
    suspend fun speak(text: String, language: String, params: SpeakParams = SpeakParams()): SpeakResult {
        val engine = selectBestEngine(language) ?: getCurrentEngine()
        return engine?.speak(text, language, params) ?: SpeakResult.Failure("No TTS engine available")
    }

    /** Stream with automatic engine selection */
    fun stream(text: String, language: String, params: SpeakParams = SpeakParams()): TtsStream? {
        val engine = selectBestEngine(language, TTSEngine.Quality.HIGH) ?: getCurrentEngine()
        return engine?.stream(text, language, params)
    }

    /** Interrupt current speech */
    fun interrupt() {
        engines.values.forEach { it.interrupt() }
    }

    /** Shutdown all engines */
    fun shutdown() {
        engines.values.forEach { it.shutdown() }
    }
}