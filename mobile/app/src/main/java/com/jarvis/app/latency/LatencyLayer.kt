package com.jarvis.app.latency

import android.content.Context
import com.jarvis.app.ObsidianSync
import com.jarvis.app.companioncore.engine.BridgePhase
import com.jarvis.app.companioncore.engine.BridgeStatusClassifier
import com.jarvis.app.companioncore.presence.CompanionCoreHolder
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.pipeline.PerceptionResult
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.ui.viewmodel.Turn
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
 * pipeline — `HumanCore.beginExchange` (Perception), the blocking Obsidian
 * vault write, and `ModelManager.send` — relocated off the UI thread so
 * the reactor and ack are not gated on it. The deep answer is completed by
 * [LatencyPipeline.onReplyReady] (expression pass, integration, speech).
 *
 * **Fallback when deep reasoning is slow** (see [LatencyPipeline]): the ack
 * covers the first ~[LatencyPipeline.ACK_GAP_MS]; the orb stays THINKING; past
 * [LatencyPipeline.SLOW_THRESHOLD_MS] the UI escalates to THINKING_LONG
 * (visual only); on a *confirmed* bridge failure exactly one soft error turn is
 * emitted — never a fabricated turn on a mere timeout.
 *
 * The frozen Human Core is untouched: the layer only calls its public facade
 * ([HumanCore.beginExchange]/[express]/[completeExchange]/[snapshot]) and the
 * additive Companion Core seams. The deep reasoning path is not removed.
 *
 * Owns [turns] and [phase]; the conversation UI collects them instead of the
 * ViewModel owning the turn pipeline.
 */
object LatencyLayer {

    /** Where the exchange is in its lifecycle, for the conversation UI. */
    enum class Phase { IDLE, ACKNOWLEDGING, THINKING, THINKING_LONG, REPLYING }

    private val slowExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "jarvis-latency").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var appContext: Context? = null
    @Volatile private var modelManager: com.jarvis.app.model.ModelManager? = null
    @Volatile private var pipeline: LatencyPipeline? = null
    @Volatile internal var failureSink: ((com.jarvis.app.failure.FailureReport) -> Unit)? = null
    private val pipelineLock = Any()

    /** Bind the nervous-system failure surface to the turn pipeline (the reply
     *  path reports brain/model failures through it). */
    fun setFailureSink(sink: (com.jarvis.app.failure.FailureReport) -> Unit) {
        failureSink = sink
    }

    /** Sentence-streaming reply speech, set once the body subsystem is wired
     *  (English + Egyptian Arabic per-sentence routing). When unset, replies
     *  fall back to one-shot [SpeechEngine.fullSpeak]. */
    @Volatile private var streamingSpeak: ((String) -> Unit)? = null

    /** App-scoped init — called from JarvisEngine after the voice engines start. */
    fun init(context: Context, modelManager: com.jarvis.app.model.ModelManager) {
        appContext = context.applicationContext
        this.modelManager = modelManager
        WarmupEngine.attach(modelManager)
        SpeechEngine.init(context)
        AckSpeech.init(context)
        pipeline()
        AckSpeech.prepare()
    }

    /** Bind the body's streaming TTS to the reply path (idempotent, late). */
    fun setStreamingSpeak(speak: (String) -> Unit) {
        streamingSpeak = speak
    }

    private fun speakReply(text: String) {
        val streaming = streamingSpeak
        if (streaming != null) streaming(text) else SpeechEngine.fullSpeak(text)
    }

    /** The conversation turns, sourced from the layer (survives across screens). */
    val turns: StateFlow<List<Turn>> get() = pipeline().turns

    /** The current exchange phase, for the "JARVIS is thinking" affordance. */
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
                logObsidian = { fromJarvis, text -> ObsidianSync.logTurn(fromJarvis, text) },
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
                            com.jarvis.app.failure.FailureReport(
                                subsystem = "MODEL",
                                operation = "send",
                                severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                                category = com.jarvis.app.failure.FailureCategory.MODEL,
                                message = "Model send threw while generating a reply",
                                source = "LatencyLayer",
                                cause = t.message,
                                recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                                recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY_WITH_BACKOFF
                            )
                        )
                    }
                },
                bridgeStatus = { modelManager?.status?.value ?: "idle" },
                beginExchange = { text, ctx -> HumanCore.beginExchange(text, ctx) },
                express = { reply, ctx -> HumanCore.express(reply, ctx) },
                completeExchange = { t, r, s, ts, perc ->
                    HumanCore.completeExchange(t, r, s, ts, perc)
                },
                sessionContext = { sessionContext() },
                cognitiveEngine = com.jarvis.app.JarvisEngine.cognitiveEngine
            )
            pipeline = p
            startCollectors(p)
            return p
        }
    }

    /** App-scoped collectors: model replies → completion, model status → errors. */
    private fun startCollectors(p: LatencyPipeline) {
        val mm = modelManager ?: return
        scope.launch {
            mm.lastReply.collect { reply ->
                if (reply.isNotBlank()) p.onReplyReady(reply)
            }
        }
        scope.launch {
            mm.status.collect { status -> p.onBridgeStatus(status) }
        }
    }

    private fun sessionContext(): SessionContext {
        val snapshot = HumanCore.snapshot()
        val presence = snapshot?.presence
        return SessionContext(
            channel = SessionContext.Channel.MOBILE_APP,
            localHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            secondsSinceLastContact = snapshot?.secondsSinceLastContact,
            wasAway = presence?.mode != com.jarvis.app.humancore.protocol.PresenceState.Mode.ACTIVE,
            prosody = null
        )
    }
}

/**
 * The latency pipeline itself — pure-JVM testable. Every effect (speech,
 * reactor, bridge, Human Core, Obsidian) is injected, so the phase ladder,
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
    private val logObsidian: (Boolean, String) -> Unit = { _, _ -> },
    private val warmPrime: () -> Unit = {},
    private val reactorSubmitted: () -> Unit = {},
    private val reactorFastPathAck: () -> Unit = {},
    private val reactorFirstSegment: () -> Unit = {},
    private val reactorUtteranceDone: () -> Unit = {},
    private val bridgeSend: (String) -> Unit = {},
    private val bridgeStatus: () -> String = { "" },
    private val beginExchange: (String, SessionContext?) -> PerceptionResult? = { _, _ -> null },
    private val express: (String, SessionContext?) -> StyledResponse = { reply, _ -> StyledResponse.Approved(reply) },
    private val completeExchange: (String, String, StyledResponse, Long, PerceptionResult?) -> Unit = { _, _, _, _, _ -> },
    private val sessionContext: () -> SessionContext? = { null },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val cognitiveEngine: com.jarvis.app.cognitive.CognitiveEngine? = null
) {

    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    private val _phase = MutableStateFlow<LatencyLayer.Phase>(LatencyLayer.Phase.IDLE)

    val turns: StateFlow<List<Turn>> = _turns.asStateFlow()
    val phase: StateFlow<LatencyLayer.Phase> = _phase.asStateFlow()

    private data class PendingTurn(
        val userText: String,
        val perception: PerceptionResult?,
        val context: SessionContext?
    )

    private val pending = ArrayDeque<PendingTurn>()
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
        appendTurn(Turn(fromJarvis = false, text = text))

        // Spoken ack — at most one per ACK_GAP_MS so burst sends don't stutter.
        // The Long.MIN_VALUE sentinel (never a real timestamp) guarantees the
        // first ack always fires and stays overflow-safe for a fixed clock.
        val now = nowMs()
        val last = lastAckMs.get()
        if (last == Long.MIN_VALUE || now - last >= ACK_GAP_MS) {
            lastAckMs.set(now)
            ackSpeak(AckSpeech.pick(ackOverride))
        }

        // Slow path — off the caller thread: perception, Obsidian vault write
        // (the one genuinely blocking op), then the fire-and-forget brain send.
        dispatch {
            val ctx = sessionContext()
            val perception = beginExchange(text, ctx)
            synchronized(pendingLock) { pending.addLast(PendingTurn(text, perception, ctx)) }
            logObsidian(false, text)
            if (_phase.value == LatencyLayer.Phase.ACKNOWLEDGING) {
                _phase.value = LatencyLayer.Phase.THINKING
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
    }

    /**
     * Fast-path-only ack for a local action (e.g. voice "screen"): records the
     * user command + JARVIS's ack as turns, flips the reactor, speaks the ack.
     * No brain call, no slow path — the exchange settles back to IDLE after a
     * beat.
     */
    fun ackOnly(commandText: String, ackPhrase: String) {
        if (commandText.isBlank()) return
        val id = exchangeCounter.incrementAndGet()

        _phase.value = LatencyLayer.Phase.ACKNOWLEDGING
        reactorSubmitted()
        reactorFastPathAck()
        appendTurn(Turn(fromJarvis = false, text = commandText))
        appendTurn(Turn(fromJarvis = true, text = ackPhrase, verified = false))
        ackSpeak(ackPhrase)

        dispatch {
            logObsidian(false, commandText)
            logObsidian(true, ackPhrase)
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
            val turn = synchronized(pendingLock) { pending.removeFirstOrNull() }

            _phase.value = LatencyLayer.Phase.REPLYING
            reactorFirstSegment()

            val styled = express(reply, turn?.context)
            val outbound = styled.outboundText
            val ts = nowMs()
            val verified = bridgeStatus() == "ok (local)"

            appendTurn(Turn(fromJarvis = true, text = outbound, verified = verified))
            logObsidian(true, outbound)

            if (turn != null) {
                completeExchange(turn.userText, reply, styled, ts, turn.perception)
            }

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
            com.jarvis.app.failure.FailureReport(
                subsystem = "MODEL",
                operation = "generate",
                severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                category = com.jarvis.app.failure.FailureCategory.BRAIN,
                message = "Brain unreachable — exchange settled with a soft-error reply",
                source = "LatencyLayer",
                cause = status,
                recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY_WITH_BACKOFF
            )
        )

        dispatch {
            synchronized(pendingLock) { pending.clear() }
            _phase.value = LatencyLayer.Phase.IDLE
            appendTurn(Turn(fromJarvis = true, text = SOFT_ERROR_TEXT, verified = false))
            logObsidian(true, SOFT_ERROR_TEXT)
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
        /** ackOnly exchanges settle back to IDLE after this beat. */
        const val ACK_ONLY_IDLE_MS = 1_000L
        /** The one soft error turn — only on a confirmed bridge failure. */
        const val SOFT_ERROR_TEXT =
            "I couldn't reach my reasoning engine — check that Termux llama-server is running."
    }
}
