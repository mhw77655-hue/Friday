package ai.friday.assistant.companioncore.presence

import ai.friday.assistant.companioncore.contract.AttentionTarget
import ai.friday.assistant.companioncore.contract.CompanionSignal
import ai.friday.assistant.companioncore.contract.CompanionState
import ai.friday.assistant.companioncore.contract.CurrentAction
import ai.friday.assistant.companioncore.contract.EmotionSnapshot
import ai.friday.assistant.companioncore.contract.InterruptFlag
import ai.friday.assistant.companioncore.contract.PresenceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §2.1 — Presence Engine.
 *
 * Root coordinator of the Companion Core. Owns one continuous PRESENT baseline
 * with transient body interactions (LISTENING, SPEAKING, SHUTTING_DOWN). Legacy
 * ASLEEP/WAKING/IDLE/THINKING labels are normalized to PRESENT; work is an
 * activity of the same individual, never a separate existence mode. No other subsystem sets
 * `presence_mode` directly; they enqueue a [TransitionRequest] and this engine
 * resolves it once per [tick] against the priority table.
 *
 * ## Arbitration (spec §2.1)
 * Priority: Shutdown > user-interrupt > Listening > Speaking > PRESENT. Of simultaneous
 * requests the highest-priority wins; on a tie the most-recently-queued wins
 * and the loser is surfaced through [onRejected] — never silently dropped
 * (spec §2.1 failure handling). A single FIFO of pending requests is drained
 * once per tick.
 *
 * ## Stale-signal fallback (audit M-15)
 * If the §2.30 signal is stale, the engine returns to PRESENT rather than
 * freezing in a stale interaction — unless a hard shutdown is already in progress
 * (a stale signal must not yank an orderly shutdown).
 *
 * ## Action derivation (spec §2.1)
 * Reads `current_action` each tick to infer when Friday is attending to input;
 * generation and completed work leave her in PRESENT. A dwell prevents 30Hz flapping.
 *
 * Pure-JVM: the clock is injectable; [tick] is deterministic and exhaustively
 * testable (canonical-mode transitions, priority races, staleness and legacy normalization).
 */
class PresenceEngine(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    initialMode: PresenceMode = PresenceMode.PRESENT
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
    private var currentMode: PresenceMode = canonicalMode(initialMode)
    private var modeEnteredAtMs: Long = nowMs()

    private val _state: MutableStateFlow<CompanionState> =
        MutableStateFlow(CompanionState.initial().copy(presenceMode = canonicalMode(initialMode)))
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
        pending.addLast(TransitionRequest(canonicalMode(mode), requester, reason, priority, nowMs()))
    }

    /** User-interrupt changes attention while Friday remains continuously present. */
    fun interruptToPresent(requester: String, reason: String) {
        requestTransition(
            mode = PresenceMode.PRESENT,
            requester = requester,
            reason = reason,
            priority = PRIORITY_USER_INTERRUPT
        )
    }

    /** Compatibility alias; the target is PRESENT, never an idle/existence state. */
    @Deprecated("Use interruptToPresent")
    fun interruptToIdle(requester: String, reason: String) = interruptToPresent(requester, reason)

    /** Blocking, immediate transition — used only by boot/init (no request FIFO). */
    fun force(mode: PresenceMode, requester: String, reason: String) {
        pending.clear()
        applyTransition(canonicalMode(mode), requester, reason)
    }

    /**
     * One tick — driven by the Engine Tick Lane (Phase 2 / plan §1.6).
     * 1. Resolve the best pending explicit request (priority arbitration).
     * 2. Stale signal → PRESENT fallback (unless shutting down).
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
                if (currentMode != PresenceMode.PRESENT &&
                    currentMode != PresenceMode.SHUTTING_DOWN
                ) {
                    applyTransition(PresenceMode.PRESENT, "PresenceEngine", "stale-signal-fallback")
                }
            } else {
                deriveFromAction(signal.currentAction)
            }
        }

        val attention = if (signal.currentAction == CurrentAction.IDLE && !observedUserPresent) {
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
        val canonical = canonicalMode(mode)
        val from = currentMode
        if (from == canonical) return
        currentMode = canonical
        modeEnteredAtMs = nowMs()
        onTransition(from, canonical, reason)
    }

    /** Dwell-guarded action → mode suggestion (spec §2.1 "infer ... boundaries"). */
    private fun deriveFromAction(action: CurrentAction) {
        val suggested = when (action) {
            CurrentAction.RECEIVING_INPUT -> PresenceMode.LISTENING
            // Generating, tool use and completed work are activities of the
            // same continuously present individual, not separate existence modes.
            CurrentAction.GENERATING, CurrentAction.TOOL_CALL,
            CurrentAction.IDLE, CurrentAction.DONE -> PresenceMode.PRESENT
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

    @Volatile private var observedUserPresent: Boolean = false

    /** Update transient camera-derived presence; no identity or image is retained. */
    fun reportUserPresence(present: Boolean) {
        observedUserPresent = present
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

        /** Legacy modes are mapped to the persistent presence baseline. */
        fun canonicalMode(mode: PresenceMode): PresenceMode = when (mode) {
            PresenceMode.ASLEEP, PresenceMode.WAKING, PresenceMode.IDLE, PresenceMode.THINKING ->
                PresenceMode.PRESENT
            else -> mode
        }

        /** Canonical arbitration table: shutdown > user interrupt > listening > speaking > present. */
        private val TABLE = mapOf(
            PresenceMode.PRESENT to PRIORITY_IDLE,
            PresenceMode.LISTENING to PRIORITY_LISTENING,
            PresenceMode.SPEAKING to PRIORITY_SPEAKING,
            PresenceMode.SHUTTING_DOWN to PRIORITY_SHUTTING_DOWN,
            PresenceMode.ASLEEP to PRIORITY_ASLEEP,
            PresenceMode.WAKING to PRIORITY_WAKING,
            PresenceMode.IDLE to PRIORITY_IDLE,
            PresenceMode.THINKING to PRIORITY_THINKING
        )

        fun priorityOf(mode: PresenceMode): Int = TABLE.getValue(canonicalMode(mode))

        /** Consecutive ticks an action-derived suggestion must persist before applying. */
        const val ACTION_DWELL_TICKS = 3
    }
}
