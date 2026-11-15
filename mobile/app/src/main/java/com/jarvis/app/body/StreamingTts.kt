package com.jarvis.app.body

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.jarvis.app.body.VocabularyStore
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.tts.CompletedAudioCache
import com.jarvis.app.tts.SynthesisCompletionGate
import com.jarvis.app.tts.SynthesisOutcome
import com.jarvis.app.tts.SynthesisReservation
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
    private val completedAudioCache = CompletedAudioCache(cacheDir)
    private val synthesisCompletionGate = SynthesisCompletionGate()
    private val synthesisCache = ConcurrentHashMap<String, File>()
    private val activePlayer = AtomicBoolean(false)
    private var currentPlayer: MediaPlayer? = null
    private val currentUtteranceId = java.util.concurrent.atomic.AtomicInteger(0)

    init {
        cacheDir.mkdirs()
        // Engine is created lazily on first speak() — the coordinator owns the
        // primary JarvisTts; this subsystem must not spin up a second Google
        // TTS engine at boot.
    }

    /** Lazily initialize the engine; no-op if already ready. */
    private fun ensureEngine() {
        if (engine != null) return
        val existingEngine = com.jarvis.app.tts.ProcessTtsHolder.getRawEngine<TextToSpeech>()
        if (existingEngine != null) {
            engine = existingEngine
            _status.value = if (com.jarvis.app.tts.ProcessTtsHolder.currentReadiness is com.jarvis.app.tts.TtsReadinessState.Ready) "ready" else "initializing"
            attachProgressListener()
            startSynthesisLoop()
            startPlaybackLoop()
            return
        }

        val registered = com.jarvis.app.tts.ProcessTtsHolder.registerOwner("com.jarvis.app.body.StreamingTts", null)
        if (!registered) {
            // Another owner is registered in this process; await or poll rawEngine
            _status.value = "reusing process tts owner"
            val raw = com.jarvis.app.tts.ProcessTtsHolder.getRawEngine<TextToSpeech>()
            if (raw != null) {
                engine = raw
                attachProgressListener()
            }
            startSynthesisLoop()
            startPlaybackLoop()
            return
        }

        initializeEngine()
        startSynthesisLoop()
        startPlaybackLoop()
    }

    private fun initializeEngine() {
        _status.value = "initializing"
        com.jarvis.app.tts.ProcessTtsHolder.gate.markInitializing()

        val initListener = TextToSpeech.OnInitListener { initStatus ->
            if (initStatus != TextToSpeech.SUCCESS) {
                _status.value = "init failed: $initStatus"
                val report = com.jarvis.app.failure.FailureReport(
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
                com.jarvis.app.tts.ProcessTtsHolder.gate.markFailed(IllegalStateException("Streaming TTS init failed: $initStatus"))
                onFailure(report)
                return@OnInitListener
            }

            // Set default language
            val result = engine?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                _status.value = "init failed: language unavailable"
                val report = com.jarvis.app.failure.FailureReport(
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
                com.jarvis.app.tts.ProcessTtsHolder.gate.markFailed(IllegalStateException("Streaming TTS language unavailable"))
                onFailure(report)
                return@OnInitListener
            }

            selectBestVoiceForLanguage(languageRouter.currentLanguage.value)
            _status.value = "ready"
            com.jarvis.app.tts.ProcessTtsHolder.gate.markReady()
        }

        // Check if an existing owner was already registered in this process
        val shared = com.jarvis.app.tts.ProcessTtsHolder.getRawEngine<TextToSpeech>()
        if (shared != null) {
            engine = shared
        } else {
            val tts = TextToSpeech(context, initListener, "com.google.android.tts")
            engine = tts
            com.jarvis.app.tts.ProcessTtsHolder.setEngine("com.jarvis.app.body.StreamingTts", tts)
        }

        attachProgressListener()
    }

    private fun attachProgressListener() {
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null) return
                if (synthesisCompletionGate.isPending(utteranceId) || isSynthesisUtterance(utteranceId)) return
                activePlayer.set(true)
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                val wasSynthesis = synthesisCompletionGate.isPending(utteranceId) || isSynthesisUtterance(utteranceId)
                val outcome = synthesisCompletionGate.markCompleted(utteranceId)
                if (outcome != null) {
                    handleSynthesisOutcome(outcome)
                } else if (!wasSynthesis) {
                    activePlayer.set(false)
                    _isSpeaking.value = false
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId == null) return
                val wasSynthesis = synthesisCompletionGate.isPending(utteranceId) || isSynthesisUtterance(utteranceId)
                val outcome = synthesisCompletionGate.markFailed(
                    utteranceId,
                    "Android TextToSpeech reported an utterance error"
                )
                if (outcome != null) {
                    handleSynthesisOutcome(outcome)
                } else if (!wasSynthesis) {
                    activePlayer.set(false)
                    _isSpeaking.value = false
                    onFailure(
                        FailureReport(
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

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == null) return
                val wasSynthesis = synthesisCompletionGate.isPending(utteranceId) || isSynthesisUtterance(utteranceId)
                val reason = if (interrupted) {
                    "TTS synthesis was interrupted before its output completed"
                } else {
                    "TTS synthesis was flushed before it started"
                }
                val outcome = synthesisCompletionGate.markFailed(utteranceId, reason)
                if (outcome != null) {
                    handleSynthesisOutcome(outcome)
                } else if (!wasSynthesis) {
                    activePlayer.set(false)
                    _isSpeaking.value = false
                }
            }
        })
    }

    private fun isSynthesisUtterance(utteranceId: String): Boolean =
        utteranceId.startsWith("tts_") || utteranceId.startsWith("preload_")

    private fun handleSynthesisOutcome(outcome: SynthesisOutcome) {
        when (outcome) {
            is SynthesisOutcome.Completed -> {
                val ticket = outcome.ticket
                if (!completedAudioCache.promote(ticket)) {
                    completedAudioCache.discard(ticket)
                    onFailure(
                        FailureReport(
                            subsystem = "STREAMING_TTS",
                            operation = "synthesize-completion",
                            severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                            category = com.jarvis.app.failure.FailureCategory.TTS,
                            message = "TTS completion callback arrived without a complete audio file",
                            source = "StreamingTts",
                            cause = "onDone did not produce a promotable file for ${ticket.cacheKey}",
                            dependency = ticket.cacheKey,
                            recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                            recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                        )
                    )
                    if (outcome.shouldPlay) {
                        activePlayer.set(false)
                        _isSpeaking.value = false
                    }
                    return
                }

                val completedFile = completedAudioCache.completedFile(ticket.cacheKey)
                synthesisCache[ticket.cacheKey] = completedFile
                trimCache()
                if (outcome.shouldPlay) playCached(completedFile, outcome.isFinal)
            }

            is SynthesisOutcome.Failed -> {
                completedAudioCache.discard(outcome.ticket)
                onFailure(
                    FailureReport(
                        subsystem = "STREAMING_TTS",
                        operation = "synthesize-completion",
                        severity = if (outcome.reason.contains("interrupted") ||
                            outcome.reason.contains("flushed") ||
                            outcome.reason.contains("preempted")
                        ) com.jarvis.app.failure.FailureSeverity.WARNING
                        else com.jarvis.app.failure.FailureSeverity.ERROR,
                        category = com.jarvis.app.failure.FailureCategory.TTS,
                        message = "TTS synthesis did not complete; partial output was discarded",
                        source = "StreamingTts",
                        cause = outcome.reason,
                        dependency = outcome.ticket.cacheKey,
                        recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                    )
                )
                if (outcome.shouldPlay) {
                    activePlayer.set(false)
                    _isSpeaking.value = false
                }
            }
        }
    }

    private fun cancelPendingSynthesis(reason: String) {
        synthesisCompletionGate.cancelAll(reason).forEach(::handleSynthesisOutcome)
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
        engine?.stop()
        cancelPendingSynthesis("preempted by a newer reply")
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
        val slot = com.jarvis.app.tts.ProcessTtsHolder.gate.slot { engine }
        if (!slot.isReady || slot.value == null) {
            onFailure(
                com.jarvis.app.failure.FailureReport(
                    subsystem = "STREAMING_TTS",
                    operation = "speak-immediate",
                    severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                    category = com.jarvis.app.failure.FailureCategory.TTS,
                    message = "Streaming TTS speakImmediate called before engine ready",
                    source = "StreamingTts",
                    recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                    recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                )
            )
            return
        }
        val ssml = toSsml(text)
        val id = "immediate_${currentUtteranceId.incrementAndGet()}"
        slot.value.speak(ssml, TextToSpeech.QUEUE_FLUSH, null, id)
        activePlayer.set(true)
        _isSpeaking.value = true
    }

    /** Interrupt current speech immediately. */
    fun interrupt() {
        engine?.stop()
        cancelPendingSynthesis("interrupted by the user")
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
                // LAUNCH-SURVIVAL AC4: a failing synthesis must not kill the
                // body. The loop is the app-level worker that serves the NEXT
                // typed turn; an exception escaping here used to take the whole
                // process down on a Dispatchers.IO worker. Every failure is
                // reported through the existing FailureSurface with its stack,
                // and the loop keeps serving.
                try {
                    synthesizeSegment(segment)
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    onFailure(
                        com.jarvis.app.failure.FailureReport(
                            subsystem = "STREAMING_TTS",
                            operation = "synthesize-segment",
                            severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                            category = com.jarvis.app.failure.FailureCategory.TTS,
                            message = "Streaming TTS segment synthesis threw and was contained",
                            source = "StreamingTts",
                            cause = "${t.javaClass.simpleName}: ${t.message}",
                            dependency = segment.language,
                            recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                            recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                        )
                    )
                }
            }
        }
    }

    private fun startPlaybackLoop() {
        scope.launch {
            // Playback is handled by TextToSpeech callbacks
        }
    }

    private fun synthesizeSegment(segment: TtsSegment) {
        // LAUNCH-SURVIVAL AC2: the tag is external input — resolve it openly,
        // never `valueOf` it. An unknown tag degrades to the default voice and
        // is recorded as unknown; the resolution can never throw.
        val reading = LanguageRouter.resolveLanguageTag(segment.language)
        if (reading.unknown) {
            Log.w(TAG, "Unknown TTS language tag '${segment.language}' — degrading to ${reading.language}")
        }
        val lang = reading.language
        val ssml = toSsml(segment.text)

        // Check cache first
        val cacheKey = "${segment.text.hashCode()}_${segment.language}"
        val cachedFile = synthesisCache[cacheKey]

        if (cachedFile != null && completedAudioCache.isComplete(cacheKey)) {
            playCached(cachedFile, segment.isFinal)
        } else {
            // A leftover file without the completion marker is never a cache hit.
            synthesisCache.remove(cacheKey)
            completedAudioCache.invalidate(cacheKey)
            synthesizeAndPlay(ssml, lang, segment.isFinal, cacheKey)
        }
    }

    private fun synthesizeAndPlay(
        ssml: String,
        language: LanguageRouter.Language,
        isFinal: Boolean,
        cacheKey: String
    ) {
        requestSynthesis(ssml, language, cacheKey, shouldPlay = true, isFinal = isFinal)
    }

    private fun requestSynthesis(
        ssml: String,
        language: LanguageRouter.Language,
        cacheKey: String,
        shouldPlay: Boolean,
        isFinal: Boolean
    ) {
        val slot = com.jarvis.app.tts.ProcessTtsHolder.gate.slot { engine }
        if (!slot.isReady || slot.value == null) {
            onFailure(
                FailureReport(
                    subsystem = "STREAMING_TTS",
                    operation = "synthesize",
                    severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                    category = com.jarvis.app.failure.FailureCategory.TTS,
                    message = "Streaming TTS synthesis requested before engine ready",
                    source = "StreamingTts",
                    cause = "engine not ready: ${slot.readiness}",
                    dependency = language.name,
                    recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                    recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                )
            )
            return
        }

        val activeEng = slot.value
        selectBestVoiceForLanguage(language)

        if (completedAudioCache.isComplete(cacheKey)) {
            val completeFile = completedAudioCache.completedFile(cacheKey)
            synthesisCache[cacheKey] = completeFile
            if (shouldPlay) playCached(completeFile, isFinal)
            return
        }

        synthesisCache.remove(cacheKey)
        completedAudioCache.invalidate(cacheKey)

        val id = (if (shouldPlay) "tts_" else "preload_") +
            currentUtteranceId.incrementAndGet()
        val ticket = completedAudioCache.ticket(cacheKey, id)
        val reservation = synthesisCompletionGate.reserve(ticket, shouldPlay, isFinal)
        if (reservation is SynthesisReservation.Joined) return

        try {
            // SUCCESS means only that Android accepted the queued request. The
            // completion gate waits for onDone before cache promotion or playback.
            val result = activeEng.synthesizeToFile(ssml, null, ticket.temporaryFile, id)
            val outcome = synthesisCompletionGate.markAccepted(
                id,
                accepted = result == TextToSpeech.SUCCESS,
                failureReason = "synthesizeToFile returned $result"
            )
            if (outcome != null) handleSynthesisOutcome(outcome)

            // Preserve direct TTS as a fallback when the queue rejects the
            // synthesis request synchronously. Async onError/onStop is reported
            // and cleaned separately; it must never expose a partial cache file.
            if (result != TextToSpeech.SUCCESS && shouldPlay) {
                val fallbackId = "fallback_" + currentUtteranceId.incrementAndGet()
                val speakResult = activeEng.speak(ssml, TextToSpeech.QUEUE_ADD, null, fallbackId)
                if (speakResult != TextToSpeech.SUCCESS) {
                    onFailure(
                        FailureReport(
                            subsystem = "STREAMING_TTS",
                            operation = "synthesize",
                            severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                            category = com.jarvis.app.failure.FailureCategory.TTS,
                            message = "Streaming TTS synthesis and direct-speak fallback both failed",
                            source = "StreamingTts",
                            cause = "synthesize=$result speak=$speakResult",
                            dependency = language.name,
                            recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                            recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                        )
                    )
                }
            }
        } catch (e: Exception) {
            val outcome = synthesisCompletionGate.markFailed(
                id,
                "${e.javaClass.simpleName}: ${e.message}"
            )
            if (outcome != null) handleSynthesisOutcome(outcome)
        }
    }

    private fun playCached(file: File, isFinal: Boolean) {
        if (!file.exists() || file.length() < 44) {
            // Cold start or synthesis failed to output a complete audio file.
            // Do not call player.setDataSource on an empty/missing file.
            activePlayer.set(false)
            if (isFinal) _isSpeaking.value = false
            return
        }
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
        val cacheKey = "${text.hashCode()}_${language.code}"
        if (completedAudioCache.isComplete(cacheKey)) {
            synthesisCache[cacheKey] = completedAudioCache.completedFile(cacheKey)
            return
        }
        val ssml = toSsml(text)
        requestSynthesis(ssml, language, cacheKey, shouldPlay = false, isFinal = false)
    }

    private fun trimCache() {
        if (synthesisCache.size > MAX_CACHE_SIZE) {
            val oldest = synthesisCache.entries.minByOrNull { it.value.lastModified() }
            oldest?.let {
                synthesisCache.remove(it.key)
                completedAudioCache.remove(it.key)
            }
        }
    }

    fun shutdown() {
        interrupt()
        engine?.shutdown()
        engine = null
        pendingSynthesis.close()
    }
}