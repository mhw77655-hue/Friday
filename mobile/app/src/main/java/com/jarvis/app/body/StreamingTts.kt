package com.jarvis.app.body

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.jarvis.app.body.VocabularyStore
import com.jarvis.app.failure.FailureReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "StreamingTts"
private const val CACHE_DIR = "jarvis_tts_stream_cache"
private const val MAX_CACHE_SIZE = 50

/**
 * Streaming TTS with interruption support and sentence-level generation.
 *
 * Features:
 * - Sentence-level streaming (don't wait for full response)
 * - Immediate interruption (barge-in)
 * - Pre-synthesis cache for common phrases
 * - Arabic + English voice routing
 * - Per-segment language switching
 */
class StreamingTts(
    private val context: Context,
    private val languageRouter: LanguageRouter,
    private val scope: CoroutineScope,
    private val onFailure: (com.jarvis.app.failure.FailureReport) -> Unit = {}
) {

    private var engine: TextToSpeech? = null
    private val _status = MutableStateFlow("not started")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentLanguage = MutableStateFlow(languageRouter.currentLanguage.value)
    val currentLanguage: StateFlow<LanguageRouter.Language> = _currentLanguage.asStateFlow()

    private val pendingSynthesis = Channel<TtsSegment>(capacity = 32)
    private var synthesisJob: Job? = null
    private var playbackJob: Job? = null

    private val cacheDir = File(context.cacheDir, CACHE_DIR)
    private val synthesisCache = ConcurrentHashMap<String, File>()
    private val activePlayer = AtomicBoolean(false)
    private var currentPlayer: MediaPlayer? = null
    private var currentUtteranceId = 0

    init {
        cacheDir.mkdirs()
        // Engine is created lazily on first speak() — the coordinator owns the
        // primary JarvisTts; this subsystem must not spin up a second Google
        // TTS engine at boot.
    }

    /** Lazily initialize the engine; no-op if already ready. */
    private fun ensureEngine() {
        if (engine != null) return
        initializeEngine()
        startSynthesisLoop()
        startPlaybackLoop()
    }

    private fun initializeEngine() {
        _status.value = "initializing"

        val initListener = TextToSpeech.OnInitListener { initStatus ->
            if (initStatus != TextToSpeech.SUCCESS) {
                _status.value = "init failed: $initStatus"
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "STREAMING_TTS",
                        operation = "init",
                        severity = com.jarvis.app.failure.FailureSeverity.DEGRADED,
                        category = com.jarvis.app.failure.FailureCategory.TTS,
                        message = "Streaming TTS engine failed to initialize",
                        source = "StreamingTts",
                        cause = "engine status $initStatus",
                        dependency = "com.google.android.tts",
                        recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY_WITH_BACKOFF
                    )
                )
                return@OnInitListener
            }

            // Set default language
            val result = engine?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                _status.value = "init failed: language unavailable"
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "STREAMING_TTS",
                        operation = "init",
                        severity = com.jarvis.app.failure.FailureSeverity.DEGRADED,
                        category = com.jarvis.app.failure.FailureCategory.TTS,
                        message = "Streaming TTS language unavailable",
                        source = "StreamingTts",
                        cause = "language unavailable (en-US)",
                        dependency = "en-US",
                        recoverability = com.jarvis.app.failure.Recoverability.PERMANENT,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.USER_ACTION_REQUIRED
                    )
                )
                return@OnInitListener
            }

            selectBestVoiceForLanguage(languageRouter.currentLanguage.value)
            _status.value = "ready"
        }

        engine = TextToSpeech(context, initListener, "com.google.android.tts")

        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null && !utteranceId.startsWith("preload_")) {
                    activePlayer.set(true)
                    _isSpeaking.value = true
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && !utteranceId.startsWith("preload_")) {
                    activePlayer.set(false)
                    _isSpeaking.value = false
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId != null && !utteranceId.startsWith("preload_")) {
                    activePlayer.set(false)
                    _isSpeaking.value = false
                    onFailure(
                        com.jarvis.app.failure.FailureReport(
                            subsystem = "STREAMING_TTS",
                            operation = "speak",
                            severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                            category = com.jarvis.app.failure.FailureCategory.TTS,
                            message = "Streaming TTS utterance error",
                            source = "StreamingTts",
                            cause = "utteranceId=$utteranceId",
                            recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                            recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                        )
                    )
                }
            }
        })
    }

    private fun selectBestVoiceForLanguage(language: LanguageRouter.Language) {
        val eng = engine ?: return
        val voices = try { eng.voices } catch (e: Exception) { return }

        val targetLang = language.locale.language
        val best = voices?.filter { it.locale.language == targetLang }
            ?.filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            ?.maxByOrNull { it.quality }

        if (best != null) {
            eng.setVoice(best)
            _currentLanguage.value = language
            Log.i(TAG, "Selected voice for ${language.code}: ${best.name}")
        }
    }

    /** Speak text with streaming - processes sentence by sentence. */
    fun speak(text: String, language: LanguageRouter.Language? = null) {
        ensureEngine()
        flushPending()
        val lang = language ?: languageRouter.detectLanguage(text)
        val sentences = splitSentences(text)
        val segments = sentences.flatMap { sentence ->
            languageRouter.detectSegments(sentence).map { it }
        }

        for (segment in segments) {
            pendingSynthesis.trySend(TtsSegment(
                text = segment.text,
                language = segment.language.code,
                isFinal = segment == segments.last()
            ))
        }
    }

    /**
     * Split a reply into speakable sentences so the first sentence starts as
     * soon as it is synthesized instead of waiting for the whole reply.
     * Keeps sentence-final punctuation attached; Arabic + Latin both split.
     */
    private fun splitSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        // Split on sentence-ending punctuation, keep the punctuation with the
        // sentence, and ignore empty fragments.
        val parts = text.split(Regex("(?<=[.!?؟])\\s+"))
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Drop any queued-but-unspoken segments (new reply preempts the old one). */
    private fun flushPending() {
        while (pendingSynthesis.tryReceive().isSuccess) { }
        currentPlayer?.stop()
        currentPlayer?.release()
        currentPlayer = null
        activePlayer.set(false)
    }

    /**
     * Speak text with vocabulary pronunciation hints.
     * Replaces known words with their learned pronunciations using SSML phoneme tags.
     */
    fun speakStreaming(text: String, vocabularyStore: VocabularyStore) {
        val enrichedText = vocabularyStore.applyPronunciationHints(text)
        speak(enrichedText)
    }

    /** Speak a single segment immediately (for ack/interrupt responses). */
    fun speakImmediate(text: String, language: LanguageRouter.Language) {
        ensureEngine()
        val ssml = toSsml(text)
        val id = "immediate_${currentUtteranceId++}"
        engine?.speak(ssml, TextToSpeech.QUEUE_FLUSH, null, id)
        activePlayer.set(true)
        _isSpeaking.value = true
    }

    /** Interrupt current speech immediately. */
    fun interrupt() {
        engine?.stop()
        activePlayer.set(false)
        _isSpeaking.value = false
        // Clear pending synthesis
        while (!pendingSynthesis.isClosedForSend) {
            pendingSynthesis.tryReceive()
        }
        currentPlayer?.stop()
        currentPlayer?.release()
        currentPlayer = null
    }

    /** Pre-synthesize common phrases to cache. */
    fun preloadPhrases(phrases: List<String>, language: LanguageRouter.Language) {
        scope.launch(Dispatchers.IO) {
            for (phrase in phrases) {
                synthesizeToCache(phrase, language)
            }
        }
    }

    private fun startSynthesisLoop() {
        scope.launch(Dispatchers.IO) {
            for (segment in pendingSynthesis) {
                synthesizeSegment(segment)
            }
        }
    }

    private fun startPlaybackLoop() {
        scope.launch {
            // Playback is handled by TextToSpeech callbacks
        }
    }

    private fun synthesizeSegment(segment: TtsSegment) {
        val lang = LanguageRouter.Language.valueOf(segment.language.uppercase())
        val ssml = toSsml(segment.text)

        // Check cache first
        val cacheKey = "${segment.text.hashCode()}_${segment.language}"
        val cachedFile = synthesisCache[cacheKey]

        if (cachedFile != null && cachedFile.exists()) {
            playCached(cachedFile, segment.isFinal)
        } else {
            // Synthesize and play
            synthesizeAndPlay(ssml, lang, segment.isFinal, cacheKey)
        }
    }

    private fun synthesizeAndPlay(ssml: String, language: LanguageRouter.Language, isFinal: Boolean, cacheKey: String) {
        selectBestVoiceForLanguage(language)

        val id = "tts_${currentUtteranceId++}"
        val cacheFile = File(cacheDir, "$cacheKey.wav")

        // Use synthesizeToFile for caching
        val result = engine?.synthesizeToFile(ssml, null, cacheFile, id)
        if (result == TextToSpeech.SUCCESS) {
            synthesisCache[cacheKey] = cacheFile
            trimCache()
            // Play via MediaPlayer for better control
            playCached(cacheFile, isFinal)
        } else {
            // Fallback to direct speak
            val speakResult = engine?.speak(ssml, TextToSpeech.QUEUE_ADD, null, id)
            if (result == null || speakResult != TextToSpeech.SUCCESS) {
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "STREAMING_TTS",
                        operation = "synthesize",
                        severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                        category = com.jarvis.app.failure.FailureCategory.TTS,
                        message = "Streaming TTS synthesis + speak fallback failed",
                        source = "StreamingTts",
                        cause = "synthesize=$result speak=$speakResult",
                        dependency = language.name,
                        recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.REINITIALIZE
                    )
                )
            }
        }
    }

    private fun playCached(file: File, isFinal: Boolean) {
        try {
            currentPlayer?.stop()
            currentPlayer?.release()

            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                activePlayer.set(false)
                if (isFinal) _isSpeaking.value = false
                it.release()
                currentPlayer = null
            }
            player.setOnErrorListener { _, _, _ ->
                activePlayer.set(false)
                _isSpeaking.value = false
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "STREAMING_TTS",
                        operation = "playback",
                        severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                        category = com.jarvis.app.failure.FailureCategory.AUDIO,
                        message = "Streaming TTS cached playback error",
                        source = "StreamingTts",
                        dependency = file.name,
                        recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                    )
                )
                true
            }
            player.prepare()
            currentPlayer = player
            activePlayer.set(true)
            _isSpeaking.value = true
            player.start()
        } catch (e: Exception) {
            Log.e(TAG, "Cached playback failed: ${e.message}")
            onFailure(
                com.jarvis.app.failure.FailureReport(
                    subsystem = "STREAMING_TTS",
                    operation = "playback",
                    severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                    category = com.jarvis.app.failure.FailureCategory.AUDIO,
                    message = "Streaming TTS cached playback failed",
                    source = "StreamingTts",
                    cause = e.message,
                    dependency = file.name,
                    recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                    recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                )
            )
        }
    }

    private fun toSsml(text: String): String {
        val escaped = text
            .replace("&", "&")
            .replace("<", "<")
            .replace(">", ">")
        val paced = escaped
            .replace(Regex("([.!?])(\\s+)"), "$1<break time=\"350ms\"/>$2")
            .replace(Regex(",(\\s+)"), ",<break time=\"150ms\"/>$1")
        return "<speak>$paced</speak>"
    }

    private fun synthesizeToCache(text: String, language: LanguageRouter.Language) {
        selectBestVoiceForLanguage(language)
        val ssml = toSsml(text)
        val cacheKey = "${text.hashCode()}_${language.code}"
        val cacheFile = File(cacheDir, "$cacheKey.wav")

        if (cacheFile.exists()) {
            synthesisCache[cacheKey] = cacheFile
            return
        }

        engine?.synthesizeToFile(ssml, null, cacheFile, "preload_$cacheKey")
        synthesisCache[cacheKey] = cacheFile
        trimCache()
    }

    private fun trimCache() {
        if (synthesisCache.size > MAX_CACHE_SIZE) {
            val oldest = synthesisCache.entries.minByOrNull { it.value.lastModified() }
            oldest?.let { synthesisCache.remove(it.key); it.value.delete() }
        }
    }

    fun shutdown() {
        interrupt()
        engine?.shutdown()
        engine = null
        pendingSynthesis.close()
    }
}