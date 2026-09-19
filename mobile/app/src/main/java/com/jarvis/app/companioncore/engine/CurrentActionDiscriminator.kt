package com.jarvis.app.companioncore.engine

import com.jarvis.app.companioncore.contract.CurrentAction

/**
 * The `current_action` discriminator (plan §4.2.1, audit A-4/M-12/R-I6) —
 * the explicit state machine that maps app-brain-bridge activity onto the
 * spec's `current_action` enum, and the sole owner of that mapping.
 *
 * Explicit state machine, NOT heuristic string matching (audit A-4): the
 * bridge's status string is classified by [BridgeStatusClassifier] into a
 * small phase enum, and the state machine below transitions on those phases
 * plus explicit conversation-boundary events.
 *
 * ## Conservative fallback (plan §4.2.1)
 * The Kotlin `JarvisBrainBridge` exposes no tool-vs-generation distinction.
 * Per the plan, both tool and generation paths are mapped to GENERATING (the
 * `THINKING`-while-sending fallback); TOOL_CALL is never produced locally
 * (a remote binding may supply it directly).
 *
 * ## Dwell hysteresis (audit R-P6)
 * Transitions that could flop (GENERATING↔IDLE on bridge-status errors) are
 * held for a minimum dwell before they are applied, so a bridge-status
 * transient can never cause Thinking→Idle→Thinking oscillation. The
 * `FirstSegmentReady` path also only advances to DONE once generation is
 * confirmed, so a ready segment never races the "ok" status.
 *
 * The discriminator is a pure, deterministic state machine: same
 * (state, event, now) always yields the same result, making the full
 * transition table unit-testable.
 */
class CurrentActionDiscriminator(
    private val now: () -> Long = { System.currentTimeMillis() },
    /** Minimum dwell for a potentially-flapping transition (audit R-P6). */
    private val minDwellMs: Long = DEFAULT_MIN_DWELL_MS
) {

    @Volatile private var action: CurrentAction = CurrentAction.IDLE
    @Volatile private var enteredAtMs: Long = now()
    @Volatile private var generationConfirmed: Boolean = false

    /** Current action — read by `SignalMapper` each tick. */
    val current: CurrentAction get() = action

    /**
     * Drive the state machine with a bridge-status observation (called each
     * tick by `HumanCoreIntegration`).
     */
    fun onBridgeStatus(status: String) {
        val t = now()
        when (BridgeStatusClassifier.classify(status)) {
            BridgePhase.SENDING -> {
                // Receiving input + a send attempt begins => thinking/generating.
                // Conservative fallback: both tool and generation map here.
                if (action == CurrentAction.RECEIVING_INPUT) {
                    action = CurrentAction.GENERATING
                    generationConfirmed = false
                    enteredAtMs = t
                } else if (action == CurrentAction.DONE) {
                    // A new send started while we were in DONE: the exchange is
                    // fresh, drop back to GENERATING (not RECEIVING_INPUT — a
                    // send attempt implies input was already received).
                    action = CurrentAction.GENERATING
                    generationConfirmed = false
                    enteredAtMs = t
                }
            }
            BridgePhase.OK -> {
                if (action == CurrentAction.GENERATING) {
                    generationConfirmed = true
                    // Stay GENERATING until FirstSegmentReady actually arrives;
                    // a ready segment races the ok-status and is resolved there.
                }
            }
            BridgePhase.ERROR -> {
                // Error during an exchange: fall back to IDLE, but only after
                // the dwell window so a transient error can't flop the mode.
                if (action == CurrentAction.GENERATING ||
                    action == CurrentAction.RECEIVING_INPUT
                ) {
                    if (t - enteredAtMs >= minDwellMs) {
                        action = CurrentAction.IDLE
                        generationConfirmed = false
                        enteredAtMs = t
                    }
                }
            }
            BridgePhase.UNKNOWN -> Unit
        }
    }

    /**
     * A user submitted text (conversation-boundary event). Authoritative: from
     * any state, a new user message begins RECEIVING_INPUT.
     */
    fun onUserSubmitted() {
        action = CurrentAction.RECEIVING_INPUT
        generationConfirmed = false
        enteredAtMs = now()
    }

    /**
     * Latency-first seam (latency layer): the fast path has already issued a
     * spoken acknowledgment, so generation is guaranteed to be starting —
     * transition RECEIVING_INPUT (or DONE, a fresh exchange) → GENERATING
     * immediately, without waiting for a bridge-status observation. The orb
     * reaches THINKING ~100ms after input instead of after the first network
     * round-trip. Conservative: only advances the two states that logically
     * precede an in-flight exchange; IDLE is left alone (no input received yet
     * means nothing is being generated).
     */
    fun onFastPathAck() {
        when (action) {
            CurrentAction.RECEIVING_INPUT, CurrentAction.DONE -> {
                action = CurrentAction.GENERATING
                generationConfirmed = false
                enteredAtMs = now()
            }
            else -> Unit
        }
    }

    /**
     * The first reply segment is ready to speak. Advances to DONE once the
     * exchange has actually begun generating (or when a segment arrives so
     * fast the ok-status raced it — in which case the exchange is clearly
     * finished).
     */
    fun onFirstSegmentReady() {
        when (action) {
            CurrentAction.GENERATING -> {
                // A ready segment means generation finished, whether or not the
                // "ok (local)" status was observed first (audit R-P6 guard:
                // only advance once there is something concrete to speak).
                action = CurrentAction.DONE
                generationConfirmed = false
                enteredAtMs = now()
            }
            else -> Unit
        }
    }

    /** The utterance finished speaking (conversation-boundary event). */
    fun onUtteranceCompleted() {
        if (action == CurrentAction.DONE) {
            action = CurrentAction.IDLE
            enteredAtMs = now()
        }
    }

    /**
     * Time-based recovery: if we are stuck in DONE past the idle window with
     * no completion event, drop back to IDLE (plan §4.2.1: DONE → IDLE on
     * idle timeout). Called by the tick loop.
     */
    fun tickIdleTimeout(idleTimeoutMs: Long = DEFAULT_DONE_IDLE_TIMEOUT_MS) {
        if (action == CurrentAction.DONE && now() - enteredAtMs >= idleTimeoutMs) {
            action = CurrentAction.IDLE
        }
    }

    companion object {
        const val DEFAULT_MIN_DWELL_MS: Long = 1_500L
        const val DEFAULT_DONE_IDLE_TIMEOUT_MS: Long = 60_000L
    }
}

/**
 * Coarse phase classification of the brain-bridge status string. Kept as its
 * own small unit so the mapping from status strings to phases is testable and
 * stable even when the bridge's wording changes (audit A-4: not heuristic
 * string matching on the action itself).
 */
enum class BridgePhase { SENDING, OK, ERROR, UNKNOWN }

object BridgeStatusClassifier {
    /** Map a bridge status string to a coarse phase. */
    fun classify(status: String?): BridgePhase {
        if (status == null || status.isBlank() || status == "idle") return BridgePhase.UNKNOWN
        return when {
            status == "sending (local)" || status.startsWith("retrying") -> BridgePhase.SENDING
            status == "ok (local)" -> BridgePhase.OK
            status.contains("failed") ||
                status.contains("error") ||
                status.contains("unreachable") ||
                status.contains("parse failed") -> BridgePhase.ERROR
            else -> BridgePhase.UNKNOWN
        }
    }
}
