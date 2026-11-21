package ai.friday.assistant.latency

import android.content.Context
import ai.friday.assistant.LocalTurnJournal
import ai.friday.assistant.companioncore.engine.BridgePhase
import ai.friday.assistant.companioncore.engine.BridgeStatusClassifier
import ai.friday.assistant.companioncore.presence.CompanionCoreHolder
import ai.friday.assistant.turn.TurnContext
import ai.friday.assistant.turn.FridayReplyDelivery
import ai.friday.assistant.ui.viewmodel.Turn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Latency-first response layer.
 *
 * Splits the conversation loop into two paths:
 *
 * **Fast path** (runs on the caller's thread, sub-millisecond): flips the
 * reactor to THINKING immediately (via the Companion Core `onFastPathAck`
 * seam — no waiting on the bridge), speaks a short pre-warmed ack, primes the
 * model, and adds the user turn. Nothing here blocks.
 *
 * **Slow path** (runs on a dedicated single-thread executor): the existing
 * context, bounded app-private turn journal write, and `CognitiveEngine.process` /
 * `ModelManager.send` run off the UI thread. The deep answer is completed by
 * Friday's reply-delivery boundary and speech output.
 *
 * **Fallback when deep reasoning is slow** (see [LatencyPipeline]): the ack
 * covers the first ~[LatencyPipeline.ACK_GAP_MS]; the orb stays THINKING; past
 * [LatencyPipeline.SLOW_THRESHOLD_MS] the UI escalates to THINKING_LONG
 * (visual only); on a *confirmed* bridge failure exactly one soft error turn is
 * emitted — never a fabricated turn on a mere timeout.
 *
 * Human Core is quarantined and is not initialized or called here. The
 * composition supplies Friday-owned cognitive/session contracts; Companion
 * Core is used only for presentation and turn-state signals.
 *
 * Owns [turns] and [phase]; the conversation UI collects them instead of the
 * ViewModel owning the turn pipeline.
 */
object LatencyLayer {

    /** Where the exchange is in its lifecycle, for the conversation UI. */
    enum class Phase { IDLE, ACKNOWLEDGING, THINKING, THINKING_LONG, REPLYING }

    private val slowExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "friday-latency").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var appContext: Context? = null
    @Volatile private var modelManager: ai.friday.assistant.model.ModelManager? = null
    @Volatile private var pipeline: LatencyPipeline? = null
    @Volatile internal var failureSink: ((ai.friday.assistant.failure.FailureReport) -> Unit)? = null
    @Volatile private var collectorsStarted = false
    private val pipelineLock = Any()

    /** Bind the nervous-system failure surface to the turn pipeline (the reply
     *  path reports brain/model failures through it). */
    fun setFailureSink(sink: (ai.friday.assistant.failure.FailureReport) -> Unit) {
        failureSink = sink
    }

    /** Sentence-streaming reply speech, set once the body subsystem is wired
     *  (English + Egyptian Arabic per-sentence routing). When unset, replies
     *  fall back to one-shot [SpeechEngine.fullSpeak]. */
    @Volatile private var streamingSpeak: ((String) -> Unit)? = null

    /** App-scoped init — called from FridayEngine after the voice engines start. */
    fun init(context: Context, modelManager: ai.friday.assistant.model.ModelManager) {
        appContext = context.applicationContext
        this.modelManager = modelManager
        WarmupEngine.attach(modelManager)
        SpeechEngine.init(context)
        AckSpeech.init(context)
        startCollectorsOnce(pipeline())
        AckSpeech.prepare()
    }

    /** Bind the body's streaming TTS to the reply path (idempotent, late). */
    fun setStreamingSpeak(speak: (String) -> Unit) {
        streamingSpeak = speak
    }

    // ── ONE-2-COMPOSITION: the turn path's platform seam ──────────────────────
    // The composition root builds the ONE pipeline (ai.friday.assistant.onefriday.
    // TurnPathAssembly) and these four members are how the phone supplies the
    // latency layer's own machinery to it. They add no behaviour and change no
    // wiring; they make the existing internals reachable so there is ONE
    // pipeline instead of a second one built here.

    /**
     * Install the composition root's pipeline as THE pipeline, before [init].
     * Without this the layer would build its own — a second turn path on the
     * same device, which is the exact duplication this closes.
     */
    fun installPipeline(p: LatencyPipeline) {
        synchronized(pipelineLock) { pipeline = p }
    }

    /** Run one unit of slow-path turn work on this layer's single-thread executor. */
    fun dispatchTurnWork(block: () -> Unit) {
        slowExecutor.execute(block)
    }

    /** Schedule delayed turn work on this layer's own coroutine scope. */
    fun scheduleTurnWork(ms: Long, block: () -> Unit) {
        scope.launch { delay(ms); block() }
    }

    /** Speak a full reply the way this layer's pipeline does (streaming when the
     *  body has attached, platform TTS otherwise). */
    fun speakReplyText(text: String) = speakReply(text)

    private fun speakReply(text: String) {
        val streaming = streamingSpeak
        if (streaming != null) streaming(text) else SpeechEngine.fullSpeak(text)
    }

    /** The conversation turns, sourced from the layer (survives across screens). */
    val turns: StateFlow<List<Turn>> get() = pipeline().turns

    /** The current exchange phase, for the "FRIDAY is thinking" affordance. */
    val phase: StateFlow<Phase> get() = pipeline().phase

    /** Text input — the single entry point for the conversation loop. */
    fun onUserInput(text: String, ackOverride: String? = null, interlocutor: String? = null) {
        if (text.isBlank()) return
        ensureStarted()
        pipeline().onUserInput(text, ackOverride, interlocutor)
    }

    /**
     * Fast-path-only acknowledgment for a local action that needs no deep
     * reasoning (e.g. the voice "screen" command): records the user + ack
     * turns, flips the reactor, speaks the ack — no brain call, no slow path.
     */
    fun ackOnly(commandText: String, ackPhrase: String) {
        if (commandText.isBlank()) return
        ensureStarted()
        pipeline().ackOnly(commandText, ackPhrase)
    }

    /** Speak a short line through the fast (on-device, pre-warmed) channel. */
    fun speakAck(phrase: String) {
        if (phrase.isBlank()) return
        ensureStarted()
        AckSpeech.fastSpeak(phrase)
    }

    /** Speak a full reply through the slow (cloud-or-device) channel. */
    fun speakFull(text: String) {
        if (text.isBlank()) return
        ensureStarted()
        SpeechEngine.fullSpeak(text)
    }

    private fun ensureStarted() {
        pipeline()
    }

    private fun pipeline(): LatencyPipeline {
        synchronized(pipelineLock) {
            pipeline?.let { return it }
            val p = LatencyPipeline(
                dispatch = { slowExecutor.execute(it) },
                scheduleDelayed = { ms, block -> scope.launch { delay(ms); block() } },
                ackSpeak = { AckSpeech.fastSpeak(it) },
                fullSpeak = { speakReply(it) },
                logTurn = { fromFriday, text -> LocalTurnJournal.logTurn(fromFriday, text) },
                warmPrime = { WarmupEngine.start(); WarmupEngine.prime() },
                reactorSubmitted = { CompanionCoreHolder.instance()?.integration?.onUserSubmitted() },
                reactorFastPathAck = { CompanionCoreHolder.instance()?.integration?.onFastPathAck() },
                reactorFirstSegment = { CompanionCoreHolder.instance()?.integration?.onFirstSegmentReady() },
                reactorUtteranceDone = { CompanionCoreHolder.instance()?.integration?.onUtteranceCompleted() },
                bridgeSend = { text ->
                    try {
                        modelManager?.send(text) ?: Unit
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        failureSink?.invoke(
                            ai.friday.assistant.failure.FailureReport(
                                subsystem = "MODEL",
                                operation = "send",
                                severity = ai.friday.assistant.failure.FailureSeverity.ERROR,
                                category = ai.friday.assistant.failure.FailureCategory.MODEL,
                                message = "Model send threw while generating a reply",
                                source = "LatencyLayer",
                                cause = t.message,
                                recoverability = ai.friday.assistant.failure.Recoverability.RETRYABLE,
                                recoveryAction = ai.friday.assistant.failure.RecoveryAction.RETRY_WITH_BACKOFF
                            )
                        )
                    }
                },
                bridgeStatus = { modelManager?.status?.value ?: "idle" },
                sessionContext = { sessionContext() },
                cognitiveEngine = ai.friday.assistant.FridayEngine.cognitiveEngine
            )
            pipeline = p
            startCollectorsOnce(p)
            return p
        }
    }

    /** App-scoped collectors, started exactly once for whichever pipeline is live. */
    private fun startCollectorsOnce(p: LatencyPipeline) {
        synchronized(pipelineLock) {
            if (collectorsStarted) return
            val mm = modelManager ?: return
            collectorsStarted = true
            startCollectors(p, mm)
        }
    }

    /** App-scoped collectors: model replies → completion, model status → errors. */
    private fun startCollectors(p: LatencyPipeline, mm: ai.friday.assistant.model.ModelManager) {
        scope.launch {
            mm.lastReply.collect { reply ->
                if (reply.isNotBlank()) p.onReplyReady(reply)
            }
        }
        scope.launch {
            mm.status.collect { status -> p.onBridgeStatus(status) }
        }
    }

    private fun sessionContext(): TurnContext {
        return TurnContext(
            channel = TurnContext.Channel.ANDROID_APP,
            localHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            secondsSinceLastContact = null,
            wasAway = null,
            prosody = null
        )
    }
}

/**
 * The latency pipeline itself — pure-JVM testable. Every effect (speech,
 * reactor, bridge, local journal, context and reply delivery) are injected, so the phase ladder,
 * burst correlation, and fallback behavior are unit-testable without Android.
 *
 * Threading: [onUserInput]'s fast path runs on the caller thread; the slow
 * path and reply/error completion run on [dispatch] (a single-thread executor
 * in production, inline in tests), which preserves FIFO between the send side
 * and the reply side — the pending queue head is always the source of the next
 * reply, even under burst traffic (audit C-2).
 */
class LatencyPipeline(
    private val dispatch: (() -> Unit) -> Unit = { it() },
    private val scheduleDelayed: (Long, () -> Unit) -> Unit = { ms, block ->
        Thread { Thread.sleep(ms); block() }.start()
    },
    private val ackSpeak: (String) -> Unit = {},
    private val fullSpeak: (String) -> Unit = {},
    private val logTurn: (Boolean, String) -> Unit = { _, _ -> },
    private val warmPrime: () -> Unit = {},
    private val reactorSubmitted: () -> Unit = {},
    private val reactorFastPathAck: () -> Unit = {},
    private val reactorFirstSegment: () -> Unit = {},
    private val reactorUtteranceDone: () -> Unit = {},
    private val bridgeSend: (String) -> Unit = {},
    private val bridgeStatus: () -> String = { "" },
    private val sessionContext: () -> TurnContext? = { null },
    private val replyDelivery: (String) -> String = FridayReplyDelivery::deliver,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val cognitiveEngine: ai.friday.assistant.cognitive.CognitiveEngine? = null,
    /** Local expression path used when no model can answer. */
    private val modelFreeReply: ((String) -> String?)? = null,
    /** False when the active provider is only the no-model heuristic placeholder. */
    private val modelAvailable: () -> Boolean = { true },
    /** Records an experience in Friday's app-local seed, independent of models. */
    private val experienceObserver: (String) -> Unit = {},
    /** Links a received reply to the user experience that preceded it. */
    private val outcomeObserver: (String, String) -> Unit = { _, _ -> }
) {

    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    private val _phase = MutableStateFlow<LatencyLayer.Phase>(LatencyLayer.Phase.IDLE)

    val turns: StateFlow<List<Turn>> = _turns.asStateFlow()
    val phase: StateFlow<LatencyLayer.Phase> = _phase.asStateFlow()

    private val pending = ArrayDeque<Long>()
    private val pendingInputs = LinkedHashMap<Long, String>()
    private val pendingLock = Any()
    private val turnsLock = Any()
    private val exchangeCounter = AtomicLong(0)
    private val settledForExchange = AtomicLong(0)
    private val lastAckMs = AtomicLong(Long.MIN_VALUE)

    // ------------------------------------------------------------- entry points

    /**
     * One user submission. Fast path runs synchronously here; the slow path is
     * dispatched and the UI is never blocked on it.
     */
    fun onUserInput(text: String, ackOverride: String? = null, interlocutor: String? = null) {
        if (text.isBlank()) return
        val id = exchangeCounter.incrementAndGet()

        // Fast path — sub-millisecond, nothing blocking.
        _phase.value = LatencyLayer.Phase.ACKNOWLEDGING
        reactorSubmitted()
        reactorFastPathAck()      // orb → THINKING on the next Companion tick
        warmPrime()
        appendTurn(Turn(fromFriday = false, text = text))

        // Spoken ack — at most one per ACK_GAP_MS so burst sends don't stutter.
        // The Long.MIN_VALUE sentinel (never a real timestamp) guarantees the
        // first ack always fires and stays overflow-safe for a fixed clock.
        val now = nowMs()
        val last = lastAckMs.get()
        if (last == Long.MIN_VALUE || now - last >= ACK_GAP_MS) {
            lastAckMs.set(now)
            ackSpeak(AckSpeech.pick(ackOverride))
        }

        // Slow path — Friday-owned turn context, app-private journal write and cognitive send.
        dispatch {
            runCatching { experienceObserver(text) }
            logTurn(false, text)
            if (_phase.value == LatencyLayer.Phase.ACKNOWLEDGING) {
                _phase.value = LatencyLayer.Phase.THINKING
            }

            // No language model is not a failed/empty Friday. When the selected
            // provider is the placeholder heuristic, the persistent local
            // individual handles the turn directly without invoking the model
            // manager or waiting for the response timeout.
            if (!modelAvailable()) {
                // Keep Friday's native cognitive organs active even when no
                // language model is resident. The empty send sink preserves
                // local perception, memory, intent, continuity and thread work
                // while allowModelWake prevents hidden model admission.
                cognitiveEngine?.let { engine ->
                    runCatching {
                        kotlinx.coroutines.runBlocking {
                            engine.process(
                                userText = text,
                                sessionContext = sessionContext(),
                                sendBlock = {},
                                interlocutor = interlocutor,
                                allowModelWake = false
                            )
                        }
                    }
                }
                val localReply = runCatching { modelFreeReply?.invoke(text) }
                    .getOrNull()?.takeIf { it.isNotBlank() }
                if (localReply != null) {
                    // Local replies are real observed outcomes too. Feed them
                    // back into the same experience substrate as model replies.
                    runCatching { outcomeObserver(text, localReply) }
                }
                val settledText = localReply
                    ?: "I'm here, but my local expression capability is not available right now."
                appendTurn(Turn(fromFriday = true, text = settledText, verified = false))
                logTurn(true, settledText)
                reactorFirstSegment()
                _phase.value = LatencyLayer.Phase.REPLYING
                if (localReply != null) fullSpeak(localReply)
                _phase.value = LatencyLayer.Phase.IDLE
                reactorUtteranceDone()
                return@dispatch
            }

            val ctx = sessionContext()
            synchronized(pendingLock) {
                pending.addLast(id)
                pendingInputs[id] = text
            }
            val engine = cognitiveEngine
            if (engine != null) {
                kotlinx.coroutines.runBlocking {
                    engine.process(text, ctx, sendBlock = bridgeSend, interlocutor = interlocutor)
                }
            } else {
                bridgeSend(text)
            }
        }

        // Slow-reasoning escalation: purely visual after SLOW_THRESHOLD_MS.
        scheduleDelayed(SLOW_THRESHOLD_MS) {
            if (exchangeCounter.get() == id && _phase.value == LatencyLayer.Phase.THINKING) {
                _phase.value = LatencyLayer.Phase.THINKING_LONG
            }
        }

        // A missing model/reply must not leave the UI in THINKING forever.
        // This is a bounded failure settlement, not a fabricated model answer.
        scheduleDelayed(RESPONSE_TIMEOUT_MS) {
            val expiredInput = synchronized(pendingLock) {
                if (pending.remove(id)) pendingInputs.remove(id) else null
            }
            if (expiredInput != null) {
                if (exchangeCounter.get() == id) _phase.value = LatencyLayer.Phase.IDLE
                LatencyLayer.failureSink?.invoke(
                    ai.friday.assistant.failure.FailureReport(
                        subsystem = "MODEL",
                        operation = "generate_timeout",
                        severity = ai.friday.assistant.failure.FailureSeverity.ERROR,
                        category = ai.friday.assistant.failure.FailureCategory.MODEL,
                        message = "No model reply arrived before the response deadline",
                        source = "LatencyLayer",
                        cause = "Timed out after ${RESPONSE_TIMEOUT_MS}ms",
                        recoverability = ai.friday.assistant.failure.Recoverability.RETRYABLE,
                        recoveryAction = ai.friday.assistant.failure.RecoveryAction.RETRY_WITH_BACKOFF
                    )
                )
                val localReply = runCatching { modelFreeReply?.invoke(expiredInput) }.getOrNull()?.takeIf { it.isNotBlank() }
                if (localReply != null) {
                    runCatching { outcomeObserver(expiredInput, localReply) }
                }
                val settledText = localReply ?: (
                    "I couldn't get a response from a reasoning model. " +
                        "No answer was generated. Check whether a model is selected, downloaded and loaded, then try again."
                    )
                appendTurn(Turn(fromFriday = true, text = settledText, verified = false))
                logTurn(true, if (localReply != null) settledText else "Model response timed out; no generated answer was received.")
                if (localReply != null) {
                    _phase.value = LatencyLayer.Phase.REPLYING
                    fullSpeak(localReply)
                }
                // A timeout is a completed local fallback, not a permanent
                // REPLYING state. The model may never produce a late reply.
                if (exchangeCounter.get() == id) _phase.value = LatencyLayer.Phase.IDLE
                reactorUtteranceDone()
            }
        }
    }

    /**
     * Fast-path-only ack for a local action (e.g. voice "screen"): records the
     * user command + Friday's ack as turns, flips the reactor, speaks the ack.
     * No brain call, no slow path — the exchange settles back to IDLE after a
     * beat.
     */
    fun ackOnly(commandText: String, ackPhrase: String) {
        if (commandText.isBlank()) return
        val id = exchangeCounter.incrementAndGet()

        _phase.value = LatencyLayer.Phase.ACKNOWLEDGING
        reactorSubmitted()
        reactorFastPathAck()
        appendTurn(Turn(fromFriday = false, text = commandText))
        appendTurn(Turn(fromFriday = true, text = ackPhrase, verified = false))
        ackSpeak(ackPhrase)

        dispatch {
            logTurn(false, commandText)
            logTurn(true, ackPhrase)
        }
        scheduleDelayed(ACK_ONLY_IDLE_MS) {
            if (exchangeCounter.get() == id && _phase.value == LatencyLayer.Phase.ACKNOWLEDGING) {
                _phase.value = LatencyLayer.Phase.IDLE
            }
        }
    }

    // ------------------------------------------------------------- completion

    /**
     * A deep reply landed. Runs on [dispatch] so it is serialized after the
     * matching send — the queue head is this reply's source message.
     */
    fun onReplyReady(reply: String) {
        if (reply.isBlank()) return
        dispatch {
            val matched = synchronized(pendingLock) {
                val id = pending.removeFirstOrNull() ?: return@synchronized null
                id to pendingInputs.remove(id)
            } ?: return@dispatch
            val matchedTurn = matched.first
            runCatching { outcomeObserver(matched.second.orEmpty(), reply) }

            _phase.value = LatencyLayer.Phase.REPLYING
            reactorFirstSegment()

            val outbound = replyDelivery(reply)
            val verified = bridgeStatus() == "ok (local)"

            appendTurn(Turn(fromFriday = true, text = outbound, verified = verified))
            logTurn(true, outbound)

            fullSpeak(outbound)
            reactorUtteranceDone()
            _phase.value = LatencyLayer.Phase.IDLE
        }
    }

    /**
     * A bridge status change. Only a *confirmed* error settles an exchange —
     * with exactly one soft error turn, never a fabricated one on a timeout.
     * Drains every pending exchange, since all in-flight sends share the same
     * failed server.
     */
    fun onBridgeStatus(status: String) {
        if (BridgeStatusClassifier.classify(status) != BridgePhase.ERROR) return
        val inFlight = _phase.value in setOf(
            LatencyLayer.Phase.ACKNOWLEDGING,
            LatencyLayer.Phase.THINKING,
            LatencyLayer.Phase.THINKING_LONG
        )
        if (!inFlight) return
        val id = exchangeCounter.get()
        if (settledForExchange.get() == id) return
        settledForExchange.set(id)

        LatencyLayer.failureSink?.invoke(
            ai.friday.assistant.failure.FailureReport(
                subsystem = "MODEL",
                operation = "generate",
                severity = ai.friday.assistant.failure.FailureSeverity.ERROR,
                category = ai.friday.assistant.failure.FailureCategory.BRAIN,
                message = "Brain unreachable — exchange settled with a soft-error reply",
                source = "LatencyLayer",
                cause = status,
                recoverability = ai.friday.assistant.failure.Recoverability.RETRYABLE,
                recoveryAction = ai.friday.assistant.failure.RecoveryAction.RETRY_WITH_BACKOFF
            )
        )

        dispatch {
            val unresolved = synchronized(pendingLock) {
                val items = pending.mapNotNull { pendingId ->
                    pendingInputs.remove(pendingId)?.let { pendingId to it }
                }
                pending.clear()
                items
            }
            _phase.value = LatencyLayer.Phase.IDLE
            if (unresolved.isEmpty()) {
                appendTurn(Turn(fromFriday = true, text = SOFT_ERROR_TEXT, verified = false))
                logTurn(true, SOFT_ERROR_TEXT)
            } else {
                val localReplies = unresolved.mapNotNull { (_, input) ->
                    runCatching { modelFreeReply?.invoke(input) }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.also { reply -> runCatching { outcomeObserver(input, reply) } }
                }
                if (localReplies.isEmpty()) {
                    // One bridge outage is one failure event, even when a burst
                    // left several requests queued against the same dead bridge.
                    appendTurn(Turn(fromFriday = true, text = SOFT_ERROR_TEXT, verified = false))
                    logTurn(true, SOFT_ERROR_TEXT)
                } else {
                    localReplies.forEach { localReply ->
                        appendTurn(Turn(fromFriday = true, text = localReply, verified = false))
                        logTurn(true, localReply)
                        _phase.value = LatencyLayer.Phase.REPLYING
                        fullSpeak(localReply)
                    }
                }
                _phase.value = LatencyLayer.Phase.IDLE
            }
            reactorUtteranceDone()
        }
    }

    // ------------------------------------------------------------- internals

    private fun appendTurn(turn: Turn) {
        synchronized(turnsLock) { _turns.value = _turns.value + turn }
    }

    companion object {
        /** Minimum gap between spoken acks (burst sends don't stutter). */
        const val ACK_GAP_MS = 1_500L
        /** Past this, the UI escalates THINKING → THINKING_LONG (visual only). */
        const val SLOW_THRESHOLD_MS = 2_500L
        /** Maximum wait for a model reply before an explicit failure is shown. */
        const val RESPONSE_TIMEOUT_MS = 30_000L
        /** ackOnly exchanges settle back to IDLE after this beat. */
        const val ACK_ONLY_IDLE_MS = 1_000L
        /** The one soft error turn — only on a confirmed bridge failure. */
        const val SOFT_ERROR_TEXT =
            "I couldn't reach my reasoning engine. Please check the model connection and try again."
    }
}
