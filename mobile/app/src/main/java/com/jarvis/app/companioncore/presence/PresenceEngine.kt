package com.jarvis.app.companioncore.presence

import com.jarvis.app.companioncore.contract.AttentionTarget
import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.CompanionState
import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.contract.EmotionSnapshot
import com.jarvis.app.companioncore.contract.InterruptFlag
import com.jarvis.app.companioncore.contract.PresenceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §2.1 — Presence Engine.
 *
 * Root coordinator of the Companion Core. Owns the single 7-state
 * `presence_mode` (ASLEEP, WAKING, IDLE, LISTENING, THINKING, SPEAKING,
 * SHUTTING_DOWN) and arbitrates which ONE is active at any moment — exactly
 * one mode system-wide (spec §3 invariant 1). No other subsystem sets
 * `presence_mode` directly; they enqueue a [TransitionRequest] and this engine
 * resolves it once per [tick] against the priority table.
 *
 * ## Arbitration (spec §2.1)
 * Priority: Shutdown > Wake > user-interrupt > Listening > Speaking >
 * Thinking > Idle (ASLEEP is the lowest / boot state). Of simultaneous
 * requests the highest-priority wins; on a tie the most-recently-queued wins
 * and the loser is surfaced through [onRejected] — never silently dropped
 * (spec §2.1 failure handling). A single FIFO of pending requests is drained
 * once per tick.
 *
 * ## Stale-signal fallback (audit M-15)
 * If the §2.30 signal is stale, the engine falls back to IDLE rather than
 * freezing in a stale mode — unless a hard shutdown is already in progress
 * (a stale signal must not yank an orderly shutdown).
 *
 * ## Action derivation (spec §2.1)
 * Reads `current_action` each tick to infer Listening/Thinking/Idle boundaries
 * when no explicit request is pending. A dwell prevents 30Hz flapping.
 *
 * Pure-JVM: the clock is injectable; [tick] is deterministic and exhaustively
 * testable (7×7 transition table, priority races, staleness).
 */
class PresenceEngine(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    initialMode: PresenceMode = PresenceMode.ASLEEP
) {

    /** One queued request to change the presence mode. */
    data class TransitionRequest(
        val mode: PresenceMode,
        val requester: String,
        val reason: String,
        val priority: Int,
        val requestedAtMs: Long
    )

    /** Emitted on every applied transition: (from, to, reason). */
    var onTransition: (from: PresenceMode, to: PresenceMode, reason: String) -> Unit = { _, _, _ -> }

    /** Emitted when a request loses arbitration (reason explains why). */
    var onRejected: (request: TransitionRequest, reason: String) -> Unit = { _, _ -> }

    private val pending = ArrayDeque<TransitionRequest>()

    @Volatile
    private var currentMode: PresenceMode = initialMode
    private var modeEnteredAtMs: Long = nowMs()

    private val _state: MutableStateFlow<CompanionState> =
        MutableStateFlow(CompanionState.initial().copy(presenceMode = initialMode))
    /** The current CompanionState — broadcast to all subsystems each tick. */
    val state: StateFlow<CompanionState> = _state.asStateFlow()

    /** The single active mode (read-only view for diagnostics/tests). */
    val mode: PresenceMode get() = currentMode

    /** Epoch ms the current mode was entered. */
    val modeEnteredAt: Long get() = modeEnteredAtMs

    // ------------------------------------------------------------------ API

    /**
     * Request a transition. Resolved on the next [tick] against the priority
     * table. [priority] defaults to the mode's canonical table priority.
     */
    fun requestTransition(
        mode: PresenceMode,
        requester: String,
        reason: String,
        priority: Int = priorityOf(mode)
    ) {
        pending.addLast(TransitionRequest(mode, requester, reason, priority, nowMs()))
    }

    /** User-interrupt: an explicit user action preempts Listening/Speaking/Thinking. */
    fun interruptToIdle(requester: String, reason: String) {
        requestTransition(
            mode = PresenceMode.IDLE,
            requester = requester,
            reason = reason,
            priority = PRIORITY_USER_INTERRUPT
        )
    }

    /** Blocking, immediate transition — used only by boot/init (no request FIFO). */
    fun force(mode: PresenceMode, requester: String, reason: String) {
        pending.clear()
        applyTransition(mode, requester, reason)
    }

    /**
     * One tick — driven by the Engine Tick Lane (Phase 2 / plan §1.6).
     * 1. Resolve the best pending explicit request (priority arbitration).
     * 2. Stale signal → IDLE fallback (unless shutting down).
     * 3. Otherwise derive Listening/Thinking/Idle from [signal.currentAction]
     *    once the suggestion has dwelled [ACTION_DWELL_TICKS] ticks.
     * Then emit a fresh [CompanionState].
     *
     * Must complete in <1ms and never block (spec §2.1 perf constraint).
     */
    fun tick(
        signal: CompanionSignal = CompanionSignal.neutral(),
        emotion: EmotionSnapshot = EmotionSnapshot.neutral(nowMs()),
        stale: Boolean = false,
        interrupts: Set<InterruptFlag> = emptySet(),
        lastRenderEpochMs: Long? = null
    ) {
        if (!resolveBestExplicitRequest()) {
            if (stale) {
                // Never freeze in a stale mode; never yank an orderly shutdown;
                // and never cut the WAKING bloom — the CompanionCore startup
                // handoff owns WAKING→IDLE once the §12 window elapses (audit F7).
                if (currentMode != PresenceMode.IDLE &&
                    currentMode != PresenceMode.SHUTTING_DOWN &&
                    currentMode != PresenceMode.WAKING
                ) {
                    applyTransition(PresenceMode.IDLE, "PresenceEngine", "stale-signal-fallback")
                }
            } else {
                deriveFromAction(signal.currentAction)
            }
        }

        val attention = if (signal.currentAction == CurrentAction.IDLE) {
            AttentionTarget.NONE
        } else {
            AttentionTarget.USER
        }
        _state.value = CompanionState(
            presenceMode = currentMode,
            emotionVector = emotion.toVector(),
            attentionTarget = attention,
            activeTheme = null,
            lastRenderTimestamp = lastRenderEpochMs,
            interruptFlags = interrupts
        )
    }

    // ------------------------------------------------------------------ internals

    /** Returns true when an explicit request won arbitration and was applied. */
    private fun resolveBestExplicitRequest(): Boolean {
        if (pending.isEmpty()) return false

        val queued = pending.toList()
        pending.clear()

        var best = queued.first()
        for (req in queued) {
            if (req.priority > best.priority ||
                (req.priority == best.priority && req.requestedAtMs >= best.requestedAtMs)
            ) {
                best = req
            }
        }
        // Every loser is surfaced — never silently dropped (spec §2.1).
        queued.filter { it != best }.forEach {
            onRejected(it, "outranked by ${best.requester} → ${best.mode}")
        }
        return applyWinner(best)
    }

    private fun applyWinner(winner: TransitionRequest): Boolean {
        if (winner.mode == currentMode) return true // same-mode re-request: drained, no-op
        applyTransition(winner.mode, winner.requester, winner.reason)
        return true
    }

    private fun applyTransition(mode: PresenceMode, requester: String, reason: String) {
        val from = currentMode
        if (from == mode) return
        currentMode = mode
        modeEnteredAtMs = nowMs()
        onTransition(from, mode, reason)
    }

    /** Dwell-guarded action → mode suggestion (spec §2.1 "infer ... boundaries"). */
    private fun deriveFromAction(action: CurrentAction) {
        val suggested = when (action) {
            CurrentAction.RECEIVING_INPUT -> PresenceMode.LISTENING
            CurrentAction.GENERATING, CurrentAction.TOOL_CALL -> PresenceMode.THINKING
            CurrentAction.IDLE, CurrentAction.DONE -> PresenceMode.IDLE
        }
        if (suggested == currentMode) {
            actionDwell = 0
            return
        }
        if (suggested != actionSuggestion) {
            actionSuggestion = suggested
            actionDwell = 0
            return
        }
        actionDwell++
        if (actionDwell >= ACTION_DWELL_TICKS) {
            applyTransition(suggested, "PresenceEngine", "current_action:$action")
            actionDwell = 0
        }
    }

    private var actionSuggestion: PresenceMode? = null
    private var actionDwell: Int = 0

    companion object {
        // ------------------------------------------------------------------ priorities
        const val PRIORITY_SHUTTING_DOWN = 600
        const val PRIORITY_WAKING = 500
        const val PRIORITY_USER_INTERRUPT = 450
        const val PRIORITY_LISTENING = 400
        const val PRIORITY_SPEAKING = 300
        const val PRIORITY_THINKING = 200
        const val PRIORITY_IDLE = 100
        const val PRIORITY_ASLEEP = 0

        /** Canonical arbitration table (spec §2.1): Shutdown > Wake > user-interrupt > Listening > Speaking > Thinking > Idle. */
        private val TABLE = mapOf(
            PresenceMode.SHUTTING_DOWN to PRIORITY_SHUTTING_DOWN,
            PresenceMode.WAKING to PRIORITY_WAKING,
            PresenceMode.LISTENING to PRIORITY_LISTENING,
            PresenceMode.SPEAKING to PRIORITY_SPEAKING,
            PresenceMode.THINKING to PRIORITY_THINKING,
            PresenceMode.IDLE to PRIORITY_IDLE,
            PresenceMode.ASLEEP to PRIORITY_ASLEEP
        )

        fun priorityOf(mode: PresenceMode): Int = TABLE.getValue(mode)

        /** Consecutive ticks an action-derived suggestion must persist before applying. */
        const val ACTION_DWELL_TICKS = 3
    }
}
