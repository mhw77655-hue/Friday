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
import com.jarvis.app.companioncore.engine.FridayRuntimeIntegration
import com.jarvis.app.model.ModelManager
import com.jarvis.app.body.LanguageRouter
import com.jarvis.app.voice.VoiceOutputPath
import com.jarvis.app.model.OrganRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BodyCoordinator"

/**
 * Android interaction coordinator for microphone capture, platform speech
 * recognition, playback interruption and presence events. Its finite
 * transport lifecycle is an adapter for Android I/O only; it is not Friday's
 * identity, cognition, personality or model of inner life.
 *
 * State events and routed user inputs each have a single consumer. Events remain
 * responsive to interruption while routed commands execute sequentially.
 */
class BodyCoordinator(
    private val context: Context,
    private val modelManager: ModelManager,
    private val languageRouter: LanguageRouter,
    private val companionIntegration: FridayRuntimeIntegration,
    private val failureSurface: com.jarvis.app.failure.FailureSurface,
    private val recoveryController: com.jarvis.app.failure.RecoveryController,
    private val scope: CoroutineScope,
    private val phoneDecisionTool: com.jarvis.app.voice.FridayPhoneDecisionTool? = null
) {

    // Child scopes keep coordinator-owned collectors from leaking into the
    // application scope. Queue consumers use a separate job so shutdown can
    // close and drain their mailboxes after background producers stop.
    private val backgroundJob = SupervisorJob(scope.coroutineContext[Job])
    private val backgroundScope = CoroutineScope(scope.coroutineContext + backgroundJob)
    private val queueJob = SupervisorJob(scope.coroutineContext[Job])
    private val queueScope = CoroutineScope(scope.coroutineContext + queueJob)

    // ── State ──
    private val _state = MutableStateFlow(BodyState.IDLE)
    val state: StateFlow<BodyState> = _state.asStateFlow()

    private val _visualState = MutableStateFlow(VisualState(bodyState = BodyState.IDLE))
    val visualState: StateFlow<VisualState> = _visualState.asStateFlow()

    private val _conversationContext = MutableStateFlow(ConversationContext())
    val conversationContext: StateFlow<ConversationContext> = _conversationContext.asStateFlow()

    private val _turnIndex = AtomicLong(0)
    private val _sessionStartMs = System.currentTimeMillis()

    // ── Event bus ──
    private val eventQueue = SerializedMailbox<BodyEvent> { event, cause ->
        reportQueueFailure("event", event::class.simpleName ?: "unknown", cause)
    }
    private val inputQueue = SerializedMailbox<String> { text, cause ->
        reportQueueFailure("input", text.take(80), cause)
    }
    private val _events = MutableSharedFlow<BodyEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<BodyEvent> = _events.asSharedFlow()

    // ── Subsystem references ──
    private var micJob: Job? = null
    private var speechRecognizer: JarvisSpeechRecognizer? = null
    private var tts: JarvisTts? = null
    private var ttsRetryCount = 0
    private var modelRetryCount = 0
    private val phoneActionPlanner by lazy {
        com.jarvis.app.voice.PhoneActionPlanner(modelManager)
    }
    private val phoneCommandOrchestrator by lazy {
        com.jarvis.app.voice.PhoneCommandOrchestrator(
            decisionTool = {
                phoneDecisionTool ?: com.jarvis.app.voice.FridayPhoneDecisionTool { utterance, description, risk ->
                    val key = com.jarvis.app.voice.JevApiKeyStore(context).read()
                    if (key == null) null else {
                        com.jarvis.app.voice.JevDecisionClient(
                            com.jarvis.app.voice.JevApiKeyProvider { key }
                        ).judgePhoneAction(utterance, description, risk)
                    }
                }
            },
            remoteConfigured = { com.jarvis.app.voice.JevApiKeyStore(context).read() != null },
            execute = { command ->
                withContext(Dispatchers.Main.immediate) {
                    com.jarvis.app.voice.PhoneCommandInterpreter.dispatch(command)
                }
            }
        )
    }

    /** Result of the always-on-hotword availability probe (AC1); drives the
     *  wake strategy. [HotwordAvailability.UNKNOWN]/[UNSUPPORTED] → the manual
     *  tap-to-trigger STT path is used. */
    @Volatile var hotwordAvailability: HotwordAvailability = HotwordAvailability.UNKNOWN
        private set

    /** Get the vocabulary store for pronunciation hints. */
    val vocabularyStore: VocabularyStore
        get() = _vocabularyStore

    // Vocabulary store is internal to the body; STT failures surface through
    // the shared nervous-system failure surface.
    private val _vocabularyStore = VocabularyStore(context, backgroundScope,
        onFailure = { r -> failureSurface.report(r) })

    // ── Model lifecycle ──
    private val reasoningLastUsedAtMs = AtomicLong(0)
    private val isReasoningWarm = AtomicBoolean(false)
    private val warmupExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "jarvis-model-warmup").apply { isDaemon = true }
    }

    // ── Presence ──
    private var presenceMonitor: PresenceMonitor? = null

    // ── Current utterance buffer for interruption ──
    private var currentUtteranceAudio: ShortArray? = null

    init {
        // One consumer per queue preserves event and command ordering. Platform
        // callbacks never suspend, and queued inputs are not discarded under load.
        queueScope.launch { eventLoop() }
        queueScope.launch { inputLoop() }
        observeFridayReplies()

        // Initialize subsystems
        initializeSubsystems()
    }

    /**
     * Reflect replies from Friday's one turn pipeline into the body activity
     * state. The body never starts a second model request or post-processes
     * reply content.
     */
    private fun observeFridayReplies() {
        backgroundScope.launch {
            var observedAssistantTurns = LatencyLayer.turns.value.count { it.fromJarvis }
            LatencyLayer.turns.collect { turns ->
                val assistantTurns = turns.filter { it.fromJarvis }
                if (assistantTurns.size < observedAssistantTurns) {
                    observedAssistantTurns = assistantTurns.size
                }
                if (assistantTurns.size > observedAssistantTurns) {
                    assistantTurns.drop(observedAssistantTurns).forEach { turn ->
                        if (turn.text.isNotBlank()) {
                            _conversationContext.value = _conversationContext.value.copy(
                                recentJarvisTexts = (_conversationContext.value.recentJarvisTexts + turn.text).takeLast(10)
                            )
                            emitEvent(BodyEvent.ResponseGenerated(turn.text))
                            emitEvent(BodyEvent.ResponseChunk(turn.text, isFinal = true))
                        }
                    }
                    observedAssistantTurns = assistantTurns.size
                }
            }
        }
    }

    /** Attach the isolated voice output state to the body activity machine. */
    fun attachVoiceOutput(output: VoiceOutputPath) {
        backgroundScope.launch {
            output.isSpeaking.collect { speaking ->
                if (!speaking && _state.value == BodyState.SPEAKING) {
                    emitEvent(BodyEvent.TtsEnd)
                }
            }
        }
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
        SpeechBridge.updateStatus(if (sttAvailable) "ready; waiting for an explicit listening request" else "unavailable")
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
        presenceMonitor = PresenceMonitor(context, backgroundScope) { present ->
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
        backgroundScope.launch {
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
        micJob = backgroundScope.launch(Dispatchers.Default) {
            try {
                JarvisMic.start(backgroundScope)
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
        try {
            eventQueue.consume { event ->
                _events.tryEmit(event)
                try {
                    handleEvent(event)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    runCatching {
                        failureSurface.report(
                            com.jarvis.app.failure.FailureReport(
                                subsystem = "BODY_EVENT_LOOP",
                                operation = "handle",
                                severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                                category = com.jarvis.app.failure.FailureCategory.LATENCY,
                                message = "Body event could not be handled; the event was recorded as a failure.",
                                source = "BodyCoordinator",
                                cause = failure.message,
                                recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                                recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                            )
                        )
                    }
                }
            }
        } finally {
            // Even a broken reporter must not leave the downstream input queue open.
            inputQueue.close()
        }
    }

    /** Emit an event to the coordinator without silently dropping it. */
    fun emitEvent(event: BodyEvent) {
        eventQueue.enqueue(event)
    }

    /** User commands are processed in arrival order through one route owner. */
    private suspend fun inputLoop() {
        inputQueue.consume { text ->
            try {
                routeInputText(text)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                runCatching { failureSurface.report(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "BODY_INPUT_ROUTER",
                        operation = "route",
                        severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                        category = com.jarvis.app.failure.FailureCategory.LATENCY,
                        message = "Friday could not route an input; the failure was recorded.",
                        source = "BodyCoordinator",
                        cause = failure.message,
                        recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                    )
                ) }
                runCatching { LatencyLayer.ackOnly(text, "I couldn't complete that request. The failure has been recorded.") }
            }
        }
    }

    private fun reportQueueFailure(queue: String, item: String, cause: Throwable?) {
        runCatching { failureSurface.report(
            com.jarvis.app.failure.FailureReport(
                subsystem = "BODY_${queue.uppercase()}_QUEUE",
                operation = "enqueue",
                severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                category = com.jarvis.app.failure.FailureCategory.LATENCY,
                message = "A body $queue item was rejected because its consumer is closed.",
                source = "BodyCoordinator",
                cause = cause?.message,
                recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY,
                metadata = mapOf("item" to item.take(120))
            )
        ) }
    }

    /**
     * Typed conversation input enters the same body-owned routing path as
     * recognized speech. This keeps phone actions, confirmation, and ordinary
     * cognition consistent across input modalities.
     */
    fun submitText(text: String) {
        val normalized = text.trim()
        if (normalized.isNotEmpty()) emitEvent(BodyEvent.UserInput(normalized))
    }

    /**
     * Toggle the Android microphone interaction without exposing the body's
     * transport lifecycle as Friday's identity or inner state.
     *
     * A second tap ends recognition. A tap during output/processing interrupts
     * the current interaction, resets the transport, and begins a fresh capture.
     */
    fun toggleListening() {
        when (_state.value) {
            BodyState.LISTENING, BodyState.WAKE, BodyState.HEARING -> {
                speechRecognizer?.cancel()
                emitEvent(BodyEvent.Reset)
            }
            BodyState.SPEAKING, BodyState.RESPONDING, BodyState.THINKING, BodyState.RETRIEVING -> {
                emitEvent(BodyEvent.BargeIn(""))
                emitEvent(BodyEvent.Reset)
                emitEvent(BodyEvent.SpeechStart)
            }
            BodyState.IDLE -> emitEvent(BodyEvent.SpeechStart)
            else -> {
                emitEvent(BodyEvent.Reset)
                emitEvent(BodyEvent.SpeechStart)
            }
        }
    }

    /** Tag the in-flight turn so failures during it correlate to one TURN-id. */
    private fun beginTurn(text: String) {
        failureSurface.currentTurnId = failureSurface.newTurnId()
    }

    /** Core state transition logic — delegates to the pure table + side effects. */
    private fun handleEvent(event: BodyEvent) {
        val currentState = _state.value
        val newState = BodyStateMachine.next(currentState, event)
        var inputTextToRoute: String? = null

        // All text modalities enter one router. Phone actions are intercepted
        // before the cognitive/chat path; ordinary text enters LatencyLayer.
        when (event) {
            is BodyEvent.SttResult -> {
                if (event.text.isNotBlank() && newState == BodyState.THINKING) {
                    inputTextToRoute = event.text.trim()
                }
            }
            is BodyEvent.UserInput -> {
                if (currentState == BodyState.SPEAKING) {
                    emitEvent(BodyEvent.BargeIn(event.text))
                } else if (event.text.isNotBlank()) {
                    if (currentState == BodyState.LISTENING || currentState == BodyState.HEARING ||
                        currentState == BodyState.WAKE
                    ) {
                        speechRecognizer?.cancel()
                    }
                    // Also route while already THINKING: a second typed turn
                    // must not disappear merely because the transport state
                    // did not change.
                    inputTextToRoute = event.text.trim()
                }
            }
            else -> {}
        }

        if (newState != currentState) {
            transitionTo(newState, event)
        }
        // Route after committing the transport transition so an immediate
        // action acknowledgment cannot race the state/visual update.
        inputTextToRoute?.let(inputQueue::enqueue)
    }

    private suspend fun routeInputText(text: String) {
        if (text.isBlank()) return
        beginTurn(text)

        val hasPhoneIntent =
            com.jarvis.app.voice.PhoneCommandInterpreter.parse(text) != null ||
                phoneCommandOrchestrator.hasPendingConfirmation()
        if (hasPhoneIntent) {
            handlePhoneOutcome(phoneCommandOrchestrator.handle(text), text)
            return
        }

        if (com.jarvis.app.voice.PhoneActionPlanner.isLikelyDeviceRequest(text)) {
            val screen = withContext(Dispatchers.Main.immediate) {
                com.jarvis.app.voice.FridayAccessibilityService.activeScreenSummary()
            }
            when (val plan = phoneActionPlanner.plan(text, screen)) {
                is com.jarvis.app.voice.PhoneActionPlanner.Result.Action ->
                    handlePhoneOutcome(phoneCommandOrchestrator.handlePlanned(text, plan.command), text)
                is com.jarvis.app.voice.PhoneActionPlanner.Result.Clarification ->
                    handlePhoneFeedback(text, plan.question)
                com.jarvis.app.voice.PhoneActionPlanner.Result.NotPhoneAction ->
                    generateResponse(text)
                is com.jarvis.app.voice.PhoneActionPlanner.Result.Unavailable ->
                    handlePhoneFeedback(text, plan.reason)
            }
            return
        }

        generateResponse(text)
    }

    private fun handlePhoneFeedback(commandText: String, feedback: String) {
        LatencyLayer.ackOnly(commandText, feedback)
        emitEvent(BodyEvent.Reset)
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
            // Input is routed once in handleEvent so phone actions can be
            // intercepted before the ordinary cognition path.
            BodyState.THINKING -> Unit
            BodyState.RETRIEVING -> {
                // Memory retrieval in progress - wait for MemoryRetrieved event
            }
            BodyState.RESPONDING -> {
                // LatencyLayer already owns reply delivery and speech.
            }
            BodyState.SPEAKING -> Unit
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
                scheduleReasoningRelease()
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

    /** Start the Android platform speech recognizer using Friday's current language route. */
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

    private fun handlePhoneOutcome(
        outcome: com.jarvis.app.voice.PhoneCommandOrchestrator.Outcome?,
        spoken: String
    ) {
        val feedback = when (outcome) {
            is com.jarvis.app.voice.PhoneCommandOrchestrator.Outcome.ActionResult ->
                phoneActionFeedback(outcome.result)
            is com.jarvis.app.voice.PhoneCommandOrchestrator.Outcome.ConfirmationNeeded ->
                "I need your confirmation to ${outcome.description}. Say yes to proceed, or say cancel."
            com.jarvis.app.voice.PhoneCommandOrchestrator.Outcome.Cancelled ->
                "Cancelled."
            is com.jarvis.app.voice.PhoneCommandOrchestrator.Outcome.Rejected ->
                "I won't perform that phone action. ${outcome.explanation}"
            is com.jarvis.app.voice.PhoneCommandOrchestrator.Outcome.Unavailable ->
                outcome.explanation
            null -> {
                generateResponse(spoken)
                return
            }
        }
        // Record the action and its honest outcome in the same conversation
        // stream as ordinary replies; never speak an invisible side-channel ack.
        LatencyLayer.ackOnly(spoken, feedback)
        emitEvent(BodyEvent.Reset)
    }

    private fun phoneActionFeedback(result: com.jarvis.app.voice.PhoneControlResult): String = when (result) {
        com.jarvis.app.voice.PhoneControlResult.Success -> "Done."
        is com.jarvis.app.voice.PhoneControlResult.NotFound -> "I couldn't find ${result.target} on the current screen."
        is com.jarvis.app.voice.PhoneControlResult.Ambiguous ->
            "I found ${result.matches} controls labelled ${result.target}. Which one do you mean?"
        is com.jarvis.app.voice.PhoneControlResult.Unavailable -> result.reason
        is com.jarvis.app.voice.PhoneControlResult.UserActionRequired -> result.reason
    }

    /**
     * Spoken text enters the same installed Friday turn pipeline as typed text.
     * The BodyCoordinator owns capture, interruption and presence only; it
     * does not create a second reasoning, Human Core or reply-styling route.
     */
    private fun generateResponse(text: String) {
        LatencyLayer.onUserInput(text)
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
        backgroundScope.launch {
            delay(2000)
            emitEvent(BodyEvent.Reset)
        }
    }

    /** Update visual state for Orb renderer. */
    private fun updateVisualState(bodyState: BodyState) {
        // Do not fabricate an affect estimate when no validated source exists.
        val emotion = 0f
        val arousal = 0f
        val alert = companionIntegration.alertLevel()
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
        warmupExecutor.execute {
            try {
                modelManager.send("") // Warm ping
                isReasoningWarm.set(true)
                reasoningLastUsedAtMs.set(System.currentTimeMillis())
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

    /** Start a resource grace period for the on-demand reasoning organ; Friday remains present on the resident anchor. */
    private fun scheduleReasoningRelease() {
        reasoningLastUsedAtMs.set(System.currentTimeMillis())
        backgroundScope.launch {
            delay(BodyConfig.REASONING_RELEASE_GRACE_MS)
            releaseReasoningIfQuiet()
        }
    }

    /**
     * Release only the on-demand reasoning organ after its grace period.
     * The RESIDENT organ remains anchored, so Friday's continuous identity and
     * lightweight cognition are not unloaded. ModelManager owns the actual cooldown/unload.
     */
    private fun releaseReasoningIfQuiet() {
        if (System.currentTimeMillis() - reasoningLastUsedAtMs.get() >= BodyConfig.REASONING_RELEASE_GRACE_MS) {
            if (isReasoningWarm.getAndSet(false)) {
                modelManager.release(OrganRole.REASONING)
                Log.i(TAG, "Reasoning organ released after quiet grace period; resident anchor remains loaded")
            }
        }
    }

    /** Shutdown all subsystems. */
    fun shutdown() {
        // Stop producers/collectors first, then drain queued events and routed
        // inputs. The injected application scope remains alive for other organs.
        backgroundJob.cancel()
        eventQueue.close()
        micJob?.cancel()
        JarvisMic.stop()
        speechRecognizer?.cancel()
        tts?.stop()
        presenceMonitor?.stop()
        warmupExecutor.shutdown()
    }
}