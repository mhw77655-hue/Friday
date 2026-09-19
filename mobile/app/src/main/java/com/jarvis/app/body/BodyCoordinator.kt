package com.jarvis.app.body

import android.content.Context
import android.util.Log
import com.jarvis.app.JarvisEngine
import com.jarvis.app.JarvisMic
import com.jarvis.app.JarvisTts
import com.jarvis.app.SpeechBridge
import com.jarvis.app.TtsBridge
import com.jarvis.app.voice.HotwordAvailability
import com.jarvis.app.voice.HotwordAvailabilityProbe
import com.jarvis.app.voice.JarvisSpeechRecognizer
import com.jarvis.app.voice.PlatformHotwordSupportPort
import com.jarvis.app.voice.PlatformSpeechRecognizerPort
import com.jarvis.app.latency.LatencyLayer
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.humancore.protocol.Exchange
import com.jarvis.app.humancore.protocol.IntegrationContext
import com.jarvis.app.humancore.mod.MemoryInterface
import com.jarvis.app.companioncore.engine.HumanCoreIntegration
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OrganRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BodyCoordinator"

/**
 * The Nervous System Coordinator — central state machine for JARVIS communication body.
 *
 * Single-threaded via a dedicated executor; deterministic behavior.
 */
class BodyCoordinator(
    private val context: Context,
    private val modelManager: ModelManager,
    private val humanCore: HumanCore,
    private val companionIntegration: HumanCoreIntegration,
    private val languageRouter: LanguageRouter,
    private val memoryStore: MemoryStore,
    private val failureSurface: com.jarvis.app.failure.FailureSurface,
    private val recoveryController: com.jarvis.app.failure.RecoveryController,
    private val scope: CoroutineScope,
    private val cognitiveEngine: com.jarvis.app.cognitive.CognitiveEngine? = null
) {

    // ── State ──
    private val _state = MutableStateFlow(BodyState.IDLE)
    val state: StateFlow<BodyState> = _state.asStateFlow()

    private val _visualState = MutableStateFlow(VisualState(bodyState = BodyState.IDLE))
    val visualState: StateFlow<VisualState> = _visualState.asStateFlow()

    private val _conversationContext = MutableStateFlow(ConversationContext())
    val conversationContext: StateFlow<ConversationContext> = _conversationContext.asStateFlow()

    private val _lastRetrievedMemory = MutableStateFlow<List<MemoryItem>>(emptyList())
    private val _lastUserText = MutableStateFlow<String>("")
    private val _lastStyled = MutableStateFlow<StyledResponse?>(null)
    private val _turnIndex = AtomicLong(0)
    private val _sessionStartMs = System.currentTimeMillis()

    // ── Event bus ──
    private val eventChannel = Channel<BodyEvent>(capacity = 64)
    private val _events = MutableSharedFlow<BodyEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<BodyEvent> = _events.asSharedFlow()

    // ── Subsystem references ──
    private var micJob: Job? = null
    private var speechRecognizer: JarvisSpeechRecognizer? = null
    private var tts: JarvisTts? = null
    private var ttsRetryCount = 0
    private var modelRetryCount = 0

    /** Result of the always-on-hotword availability probe (AC1); drives the
     *  wake strategy. [HotwordAvailability.UNKNOWN]/[UNSUPPORTED] → the manual
     *  tap-to-trigger STT path is used. */
    @Volatile var hotwordAvailability: HotwordAvailability = HotwordAvailability.UNKNOWN
        private set

    /** Get the vocabulary store for pronunciation hints. */
    val vocabularyStore: VocabularyStore
        get() = _vocabularyStore

    // ── Memory subsystem ──
    // memoryStore is injected (JarvisEngine owns the single process-wide
    // instance); vocabulary store is internal to the body. The platform STT
    // (Arabic-capable push-to-talk) surfaces failures to the nervous system.
    private val _vocabularyStore = VocabularyStore(context, scope,
        onFailure = { r -> failureSurface.report(r) })

    // ── Model lifecycle ──
    private val modelIdleTimer = AtomicLong(0)
    private val isModelWarm = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "jarvis-body-coordinator").apply { isDaemon = true }
    }

    // ── Presence ──
    private var presenceMonitor: PresenceMonitor? = null

    // ── Current utterance buffer for interruption ──
    private var currentUtteranceAudio: ShortArray? = null

    init {
        // Start the event loop
        scope.launch { eventLoop() }

        // Initialize subsystems
        initializeSubsystems()
    }

    /** Initialize all voice subsystems and wire them. */
    private fun initializeSubsystems() {
        // Always-on wake-word / SoundTrigger availability probe (AC1). The probe
        // reports the device's REAL support (never assumed); the result drives
        // the wake strategy below. UNKNOWN/UNSUPPORTED collapses to manual
        // tap-to-trigger STT so the phone always has a voice-input path.
        hotwordAvailability = HotwordAvailabilityProbe.queryAndLog(PlatformHotwordSupportPort(context))
        Log.i(TAG, "Hotword availability: $hotwordAvailability")

        // Platform STT (android.speech.SpeechRecognizer) — this replaces the
        // deleted Vosk + sherpa-onnx native STT stack. Pure platform service,
        // no JNI, no native object lifecycle, so it cannot carry the
        // "destroyed mutex" native-crash class that disabled voice at d9ca295.
        speechRecognizer = JarvisSpeechRecognizer(
            PlatformSpeechRecognizerPort(context) { r -> failureSurface.report(r) },
            onStatusChanged = { s -> SpeechBridge.updateStatus(s) }
        )
        val sttAvailable = speechRecognizer?.isAvailable() ?: false
        Log.i(TAG, "Platform STT: ${if (sttAvailable) "available" else "UNAVAILABLE"}")
        if (!sttAvailable) {
            // Honest degradation: no platform recognizer service installed, so
            // voice input is unavailable and surfaced as a degraded capability.
            failureSurface.report(
                com.jarvis.app.failure.FailureReport(
                    subsystem = "STT_PLATFORM",
                    operation = "recognize",
                    severity = com.jarvis.app.failure.FailureSeverity.DEGRADED,
                    category = com.jarvis.app.failure.FailureCategory.LANGUAGE,
                    message = "Platform speech recognizer unavailable — voice input degraded",
                    source = "BodyCoordinator",
                    recoverability = com.jarvis.app.failure.Recoverability.PERMANENT,
                    recoveryAction = com.jarvis.app.failure.RecoveryAction.FALLBACK
                )
            )
        }

        // TTS (re-enabled, unchanged) — pure android.speech.tts.TextToSpeech
        // with zero native/JNI code. Never implicated in the crash; speech
        // output is safe to enable and does actually fire on a real turn.
        tts = JarvisTts(context, onStatusChanged = { status -> TtsBridge.updateStatus(status) },
            onFailure = { r -> recoveryController.handle(r) })
        val ttsStarted = tts?.start() ?: false
        if (ttsStarted) TtsBridge.attach(tts!!)
        Log.i(TAG, "TTS: ${if (ttsStarted) "started" else "FAILED"}")
        recoveryController.register(
            com.jarvis.app.failure.RecoveryController.RetrySpec(
                subsystem = "TTS",
                operation = "init",
                action = { tts?.start() ?: false }
            )
        )

        // Start mic capture
        startMic()

        // Presence monitor
        presenceMonitor = PresenceMonitor(context, scope) { present ->
            emitEvent(BodyEvent.UserPresenceChanged(present))
        }
        presenceMonitor?.start()

        // Warm the model
        warmModel()

        // Manual push-to-talk endpoint closure: when the user finishes speaking
        // during a listening/wake turn, route the closure through the platform
        // STT path (which owns live mic recognition during LISTENING). Speech
        // input is handled on the LISTENING transition via the platform
        // recognizer — there is no Vosk/sherpa continuous decode to bridge.
        scope.launch {
            JarvisMic.speechEnded.collect {
                // Only act when we were actively listening to a push-to-talk turn.
                if (_state.value == BodyState.LISTENING || _state.value == BodyState.WAKE) {
                    emitEvent(BodyEvent.SpeechEnd(JarvisMic.utteranceSamples()))
                }
            }
        }

        // Emit ready
        emitEvent(BodyEvent.Reset)
    }

    private fun startMic() {
        micJob = scope.launch(Dispatchers.Default) {
            try {
                JarvisMic.start(scope)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.e(TAG, "Mic start failed: ${t.message}")
                failureSurface.report(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "MIC",
                        operation = "start",
                        severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                        category = com.jarvis.app.failure.FailureCategory.VOICE_INPUT,
                        message = "Mic capture failed to start — voice input unavailable",
                        source = "BodyCoordinator",
                        cause = t.message,
                        recoverability = com.jarvis.app.failure.Recoverability.PERMANENT,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.USER_ACTION_REQUIRED
                    )
                )
            }
        }
    }

    /** Main event loop — single-threaded coordination. */
    private suspend fun eventLoop() {
        for (event in eventChannel) {
            _events.tryEmit(event)
            handleEvent(event)
        }
    }

    /** Emit an event to the coordinator. */
    fun emitEvent(event: BodyEvent) {
        eventChannel.trySend(event)
    }

    /** Tag the in-flight turn so failures during it correlate to one TURN-id. */
    private fun beginTurn(text: String) {
        failureSurface.currentTurnId = failureSurface.newTurnId()
    }

    /** Core state transition logic — delegates to the pure table + side effects. */
    private fun handleEvent(event: BodyEvent) {
        val currentState = _state.value
        val newState = BodyStateMachine.next(currentState, event)

        // Side effects that the pure table can't express: re-emissions that
        // continue the turn once a new state is committed.
        when (event) {
            is BodyEvent.SttResult ->
                if (event.text.isNotBlank() && newState == BodyState.THINKING) {
                    beginTurn(event.text)
                    emitEvent(BodyEvent.UserInput(event.text))
                }
            is BodyEvent.UserInput ->
                if (currentState == BodyState.SPEAKING) {
                    beginTurn(event.text)
                    emitEvent(BodyEvent.BargeIn(event.text))
                }
            is BodyEvent.MemoryRetrieved -> {
                // Memory retrieved - store it and continue to RESPONDING
                _lastRetrievedMemory.value = event.items
                if (_state.value == BodyState.RETRIEVING) {
                    emitEvent(BodyEvent.ResponseGenerated(""))
                }
            }
            else -> {}
        }

        if (newState != currentState) {
            transitionTo(newState, event)
        }
    }

    /** Execute state transition with side effects. */
    private fun transitionTo(newState: BodyState, trigger: BodyEvent) {
        val oldState = _state.value
        _state.value = newState
        updateVisualState(newState)
        Log.d(TAG, "State: $oldState → $newState (trigger: ${trigger::class.simpleName})")

        // Side effects per transition
        when (newState) {
            BodyState.WAKE -> {
                // Visual: listening pulse
                companionIntegration.setAlertOverride(com.jarvis.app.companioncore.contract.AlertLevel.NONE)
            }
            BodyState.LISTENING -> {
                companionIntegration.onUserSubmitted()
                // Push-to-talk / tap-to-trigger: route live speech through the
                // platform recognizer. Self-endpointing; the result arrives via
                // the SttResult event once recognized.
                startPlatformRecognition()
            }
            BodyState.HEARING -> {
                // Speech captured while a turn was in flight — with platform
                // STT there is no separate Vosk/sherpa transcript to fetch, so
                // HEARING simply holds until the autonomous result (or the
                // timeout) closes the recognition session.
            }
            BodyState.THINKING -> {
                companionIntegration.onFastPathAck()
                val userText = (trigger as? BodyEvent.UserInput)?.text
                    ?: (trigger as? BodyEvent.SttResult)?.text
                if (!userText.isNullOrBlank()) {
                    triggerMemoryRetrieval(userText)
                    generateResponse(userText)
                }
            }
            BodyState.RETRIEVING -> {
                // Memory retrieval in progress - wait for MemoryRetrieved event
            }
            BodyState.RESPONDING -> {
                companionIntegration.onFirstSegmentReady()
                val userText = _lastUserText.value
                val styled = _lastStyled.value
                if (!userText.isNullOrBlank() && styled != null) {
                    completeExchangeWithMemory(userText, styled)
                }
            }
            BodyState.SPEAKING -> {
                companionIntegration.onUtteranceCompleted()
            }
            BodyState.INTERRUPTED -> {
                handleInterruption(trigger)
            }
            BodyState.LEARNING -> {
                // Vocabulary teaching mode
            }
            BodyState.ERROR -> {
                handleError(trigger as? BodyEvent.ErrorOccurred)
            }
            BodyState.IDLE -> {
                resetIdleTimer()
            }
            BodyState.USER_PRESENT -> {
                companionIntegration.sendUserPresenceEvent(
                    com.jarvis.app.companioncore.contract.UserPresenceEvent(
                        eventType = com.jarvis.app.companioncore.contract.UserPresenceEvent.EventType.USER_PRESENT,
                        timestamp = System.currentTimeMillis(),
                        confidence = 0.9
                    )
                )
            }
            BodyState.USER_ABSENT -> {
                companionIntegration.sendUserPresenceEvent(
                    com.jarvis.app.companioncore.contract.UserPresenceEvent(
                        eventType = com.jarvis.app.companioncore.contract.UserPresenceEvent.EventType.USER_ABSENT,
                        timestamp = System.currentTimeMillis(),
                        confidence = 0.9
                    )
                )
            }
        }
    }

    /** Trigger memory retrieval before model generation. */
    private fun triggerMemoryRetrieval(query: String) {
        _state.value = BodyState.RETRIEVING
        executor.execute {
            val context = buildMemoryQueryContext()
            val items = memoryStore.retrieve(query, context)
            scope.launch {
                emitEvent(BodyEvent.MemoryRetrieved(items))
            }
        }
    }

    /**
     * Route the voice utterance into the live turn pipeline. LatencyLayer owns
     * ack speech, the deep brain call, reply completion and the Companion Core
     * reactor seams — the coordinator stays a thin nervous-system front-end.
     */
    private fun generateResponse(text: String) {
        _lastUserText.value = text
        executor.execute {
            // Get memory context for the model
            val memoryItems = _lastRetrievedMemory.value

            // Build session context
            val sessionContext = SessionContext(
                channel = SessionContext.Channel.MOBILE_APP,
                localHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                secondsSinceLastContact = null,
                wasAway = false,
                prosody = null
            )

            // Run HumanCore perception
            val perception = humanCore.beginExchange(text, sessionContext)

            // Route turn through CognitiveEngine -> ModelManager
            val engine = cognitiveEngine
            val modelResponse = if (engine != null) {
                runBlocking {
                    val turnResult = engine.process(text, sessionContext, memoryItems)
                    turnResult.responseText ?: modelManager.sendWithContext(text, memoryItems)
                }
            } else {
                modelManager.sendWithContext(text, memoryItems)
            }

            // Run expression pass with memory context
            val styled = humanCore.express(modelResponse, sessionContext)
            _lastStyled.value = styled

            // Complete exchange through HumanCore
            if (perception != null) {
                humanCore.completeExchange(text, modelResponse, styled, System.currentTimeMillis(), perception)
            }

            // Transition to RESPONDING state
            scope.launch {
                emitEvent(BodyEvent.ResponseGenerated(styled.outboundText))
            }
        }
    }

    /**
     * Tap-to-trigger / push-to-talk recognition: start the platform STT
     * recognizer with the current language's locale. It self-endpoints on the
     * user's silence and delivers one result; the result routes straight into
     * the event loop. This is the SAME production voice-input path used for
     * the (deleted) Vosk/sherpa stack — now pure android.speech.SpeechRecognizer.
     */
    private fun startPlatformRecognition() {
        val recognizer = speechRecognizer
        if (recognizer == null || !recognizer.isAvailable()) {
            failureSurface.report(
                com.jarvis.app.failure.FailureReport(
                    subsystem = "STT_PLATFORM",
                    operation = "recognize",
                    severity = com.jarvis.app.failure.FailureSeverity.DEGRADED,
                    category = com.jarvis.app.failure.FailureCategory.LANGUAGE,
                    message = "Platform recognizer unavailable — tap-to-trigger degraded",
                    source = "BodyCoordinator",
                    dependency = languageRouter.currentLanguage.value.code,
                    recoverability = com.jarvis.app.failure.Recoverability.PERMANENT,
                    recoveryAction = com.jarvis.app.failure.RecoveryAction.FALLBACK
                )
            )
            return
        }
        val locale = when (languageRouter.currentLanguage.value) {
            LanguageRouter.Language.EGYPTIAN_ARABIC -> java.util.Locale("ar", "EG")
            LanguageRouter.Language.ENGLISH -> java.util.Locale.US
        }
        recognizer.recognize(locale) { text ->
            val clean = text?.trim().orEmpty()
            if (clean.isNotBlank()) {
                SpeechBridge.recordResult(clean)
                emitEvent(
                    BodyEvent.SttResult(
                        clean,
                        0.9f,
                        languageRouter.currentLanguage.value.code
                    )
                )
            }
        }
    }

    /** Build context for memory query. */
    private fun buildMemoryQueryContext(): List<MemoryItem> {
        val ctx = _conversationContext.value
        val items = mutableListOf<MemoryItem>()
        // Add recent conversation as context
        ctx.recentUserTexts.forEachIndexed { i, text ->
            items.add(MemoryItem("ctx_$i", MemoryType.CONTEXT, text, System.currentTimeMillis(), emptyList(), 1.0f))
        }
        return items
    }

    /**
     * Complete the exchange when a deep reply lands - wire MemoryRetrieved
     * into the LatencyLayer and HumanCore flow.
     */
    private fun completeExchangeWithMemory(userText: String, styled: StyledResponse) {
        // Get the memory items from the last retrieval
        val memoryItems = _lastRetrievedMemory.value

        // Build session context with memory
        val sessionContext = SessionContext(
            channel = SessionContext.Channel.MOBILE_APP,
            localHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            secondsSinceLastContact = null,
            wasAway = false,
            prosody = null
        )

        // Run expression pass with memory context
        val finalStyled = humanCore.express(styled.outboundText, sessionContext)

        // Annotate for memory persistence
        humanCore.memoryInterface?.annotateExchange(
            com.jarvis.app.humancore.protocol.Exchange(
                userText = userText,
                reasoningReply = styled.outboundText,
                styled = finalStyled,
                ts = System.currentTimeMillis()
            ),
            com.jarvis.app.humancore.protocol.IntegrationContext(
                affect = null,
                deviation = null,
                vulnerabilityShared = false,
                mentionsJarvis = false
            )
        )

        // Store memory items if relevant
        if (memoryItems.isNotEmpty()) {
            memoryStore.storeConversation(
                userText = userText,
                assistantText = finalStyled.outboundText,
                memoryItems = memoryItems
            )
        }

        // Speak the final response with vocabulary-aware TTS
        speakWithVocabulary(finalStyled.outboundText)

        // Signal utterance completion
        companionIntegration.onUtteranceCompleted()

        // Return to IDLE
        emitEvent(BodyEvent.Reset)
    }

    /**
     * Speak text with vocabulary pronunciation hints.
     */
    private fun speakWithVocabulary(text: String) {
        // R2: speak through the isolated `:voice` habitat first (Kokoro via the
        // capability contract); the legacy streaming path remains the fallback
        // when the habitat isn't ready. The body always answers.
        val voiceOutput = try { JarvisEngine.getVoiceOutputPath() } catch (e: Exception) { null }
        if (voiceOutput != null) {
            voiceOutput.speak(text, vocabulary = vocabularyStore)
            return
        }
        val streamingTts = try { JarvisEngine.getStreamingTts() } catch (e: Exception) { null }
        if (streamingTts != null) {
            streamingTts.speakStreaming(text, vocabularyStore)
        } else {
            // Fallback to legacy TTS
            tts?.speak(text)
        }
    }

    /** Handle interruption (barge-in). */
    private fun handleInterruption(trigger: BodyEvent) {
        // Stop TTS immediately (habitat output path, ack/full path, streaming path)
        runCatching { com.jarvis.app.JarvisEngine.getVoiceOutputPath()?.interrupt() }
        tts?.interrupt()
        TtsBridge.stopSpeaking()
        try {
            com.jarvis.app.JarvisEngine.getStreamingTts().interrupt()
        } catch (e: IllegalStateException) {
            // Streaming TTS not wired yet — nothing to cut. This is expected at
            // boot only; report it so repeated occurrences surface as a real gap.
            failureSurface.report(
                com.jarvis.app.failure.FailureReport(
                    subsystem = "STREAMING_TTS",
                    operation = "interrupt",
                    severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                    category = com.jarvis.app.failure.FailureCategory.TTS,
                    message = "Streaming TTS not available during interrupt",
                    source = "BodyCoordinator",
                    cause = e.message,
                    recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                    recoveryAction = com.jarvis.app.failure.RecoveryAction.REINITIALIZE
                )
            )
        }

        // Stop any live tap-to-trigger recognition
        speechRecognizer?.cancel()

        // Re-route the interrupting utterance as the new turn. From INTERRUPTED,
        // the re-emitted UserInput transitions straight to THINKING, so the
        // barge-in text is never lost.
        val interruptingText = when (trigger) {
            is BodyEvent.UserInput -> trigger.text
            is BodyEvent.BargeIn -> trigger.userText
            else -> null
        }
        if (!interruptingText.isNullOrBlank()) {
            emitEvent(BodyEvent.UserInput(interruptingText))
        }
    }

    /** Handle error with recovery. */
    private fun handleError(error: BodyEvent.ErrorOccurred?) {
        Log.e(TAG, "Error: ${error?.message}")
        failureSurface.report(
            com.jarvis.app.failure.FailureReport(
                subsystem = "BODY",
                operation = "turn",
                severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                category = com.jarvis.app.failure.FailureCategory.LATENCY,
                message = error?.message ?: "Body error state entered",
                source = "BodyCoordinator",
                recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
            )
        )
        scope.launch {
            delay(2000)
            emitEvent(BodyEvent.Reset)
        }
    }

    /** Update visual state for Orb renderer. */
    private fun updateVisualState(bodyState: BodyState) {
        val snapshot = humanCore.snapshot()
        val emotion = (snapshot?.moodValence ?: 0.0).toFloat()
        val arousal = (snapshot?.moodArousal ?: 0.0).toFloat()
        val alert = companionIntegration.hcAlertLevel()
        val userPresent = bodyState in setOf(BodyState.USER_PRESENT, BodyState.WAKE, BodyState.LISTENING)

        _visualState.value = VisualState(
            bodyState = bodyState,
            emotionValence = emotion,
            emotionArousal = arousal,
            speaking = bodyState == BodyState.SPEAKING,
            listening = bodyState in setOf(BodyState.LISTENING, BodyState.WAKE, BodyState.HEARING),
            thinking = bodyState in setOf(BodyState.THINKING, BodyState.RETRIEVING, BodyState.RESPONDING),
            alertLevel = alert,
            userPresent = userPresent,
            // Failure overlay: active WARNING+ failures ride on top of the
            // body state so a degraded system reads as degraded in the orb.
            failureLevel = failureSurface.worstSeverity.value
        )
    }

    /** Warm up the model for fast first response. */
    private fun warmModel() {
        executor.execute {
            try {
                modelManager.send("") // Warm ping
                isModelWarm.set(true)
                modelIdleTimer.set(System.currentTimeMillis())
                Log.i(TAG, "Model warmed")
            } catch (e: Exception) {
                Log.w(TAG, "Model warm failed: ${e.message}")
                failureSurface.report(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "MODEL",
                        operation = "warm",
                        severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                        category = com.jarvis.app.failure.FailureCategory.MODEL,
                        message = "Model warm-up ping failed",
                        source = "BodyCoordinator",
                        cause = e.message,
                        dependency = modelManager.activeProvider.value.providerType.name,
                        recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY_WITH_BACKOFF
                    )
                )
            }
        }
    }

    /** Reset idle timer for model unload. */
    private fun resetIdleTimer() {
        modelIdleTimer.set(System.currentTimeMillis())
        scope.launch {
            delay(BodyConfig.MODEL_IDLE_UNLOAD_MS)
            checkModelIdle()
        }
    }

    /**
     * Idle conversational organ: release the reasoning tier so ModelManager's
     * cooldown sweep unloads it from memory (RAM strategy: idle models leave
     * memory). Model lifecycle is owned by ModelManager — this is a release,
     * not a direct unload.
     */
    private fun checkModelIdle() {
        if (System.currentTimeMillis() - modelIdleTimer.get() >= BodyConfig.MODEL_IDLE_UNLOAD_MS) {
            if (isModelWarm.getAndSet(false)) {
                modelManager.release(OrganRole.REASONING)
                Log.i(TAG, "Model idle timeout - released reasoning tier")
            }
        }
    }

    /** Shutdown all subsystems. */
    fun shutdown() {
        micJob?.cancel()
        JarvisMic.stop()
        speechRecognizer?.cancel()
        tts?.stop()
        presenceMonitor?.stop()
        executor.shutdown()
    }
}