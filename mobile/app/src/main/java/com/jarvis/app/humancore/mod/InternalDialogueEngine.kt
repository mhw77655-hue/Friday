package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.protocol.Exchange
import com.jarvis.app.humancore.protocol.IntegrationContext
import com.jarvis.app.humancore.store.DialogueEntry
import com.jarvis.app.humancore.store.DialogueLog

/**
 * The Internal Dialogue Engine (§11) — the ONLY writer of the Internal
 * Dialogue Log.
 *
 * It generates JARVIS's short, reflective internal narrative at KEY moments
 * (triggers), via the Model Port with a deterministic template fallback. The
 * point of this module is separation: internal monologue is kept OUT of the
 * Reasoning subsystem's task context (it must never contaminate the model's
 * reasoning) and is NEVER dumped verbatim to the user (§11 Constraints).
 * Surfacing internal lines to the user, paraphrased, is a separate, gated
 * decision owned by the Companion Behavior Orchestrator (§14) and expressed
 * only through the normal Expression Pass.
 *
 * Rules honored here:
 *  - runs async in the Integration Pass, never on the critical path (§11);
 *  - per-trigger debounce prevents the same trigger from spamming the log
 *    (a bounded, append-only log, §11 Persistence);
 *  - generated text is strictly bounded and never asserts memories, feelings,
 *    or activity the state does not back (§11, §20);
 *  - trigger selection is deterministic from state — the same state produces
 *    the same narrative, keeping the consistency regression suite stable
 *    (§0.16).
 */
class InternalDialogueEngine(
    private val dialogue: DialogueLog,
    private val ism: InternalStateManager,
    private val modelPort: ModelPort,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    private val lastTriggerAt = mutableMapOf<String, Long>()
    private val debounceMs = 60 * 60 * 1000L // each trigger, max once/hour

    fun onExchange(exchange: Exchange, ctx: IntegrationContext, now: Long = clock()) {
        val snapshot = ism.snapshot(now)
        val trigger = selectTrigger(snapshot, ctx) ?: return
        if (isDebounced(trigger, now)) return
        lastTriggerAt[trigger] = now

        val prompt = InternalPrompt.forReflection(snapshot, cueText(trigger, snapshot, ctx))
        val text = modelPort.complete(prompt, maxTokens = 80, timeoutMs = 4_000L)
            ?: fallbackLine(trigger)
        dialogue.append(
            DialogueEntry(
                ts = now,
                trigger = trigger,
                text = text.take(200),
                snapshotRef = "v${snapshot.identityVersion}"
            )
        )
        bus.publish(HcEvent.DialogueAppended(ts = now, reason = "trigger: $trigger", trigger = trigger))
    }

    fun onSessionStart(now: Long = clock()) {
        if (isDebounced(TRIGGER_NEW_SESSION, now)) return
        lastTriggerAt[TRIGGER_NEW_SESSION] = now
        val snapshot = ism.snapshot(now)
        val prompt = InternalPrompt.forReflection(snapshot, "A new session is beginning.")
        val text = modelPort.complete(prompt, maxTokens = 80, timeoutMs = 4_000L)
            ?: fallbackLine(TRIGGER_NEW_SESSION)
        dialogue.append(
            DialogueEntry(
                ts = now, trigger = TRIGGER_NEW_SESSION,
                text = text.take(200), snapshotRef = "v${snapshot.identityVersion}"
            )
        )
        bus.publish(HcEvent.DialogueAppended(ts = now, reason = "trigger: $TRIGGER_NEW_SESSION", trigger = TRIGGER_NEW_SESSION))
    }

    private fun selectTrigger(snapshot: com.jarvis.app.humancore.protocol.StateSnapshot, ctx: IntegrationContext): String? {
        val affect = ctx.affect
        val s = affect?.signals ?: emptyMap()
        val distress = (s["stress"] ?: 0.0) >= 0.5 || (s["frustration"] ?: 0.0) >= 0.5 || (s["anger"] ?: 0.0) >= 0.5
        if (distress) return TRIGGER_USER_DISTRESS
        if (ctx.vulnerabilityShared) return TRIGGER_VULNERABILITY
        val trust = snapshot.trust ?: return null
        if (trust < 0.3) return TRIGGER_LOW_TRUST
        if (trust > 0.7) return TRIGGER_HIGH_TRUST
        val dev = ctx.deviation
        if (dev != null && dev.significant && (affect?.valence ?: 0.0) < -0.3 && ctx.mentionsJarvis) {
            return TRIGGER_AFTER_CONFLICT
        }
        return null
    }

    private fun cueText(trigger: String, snapshot: com.jarvis.app.humancore.protocol.StateSnapshot, ctx: IntegrationContext): String {
        return when (trigger) {
            TRIGGER_USER_DISTRESS -> "the user is in distress"
            TRIGGER_VULNERABILITY -> "the user shared something vulnerable"
            TRIGGER_LOW_TRUST -> "trust is low with the user"
            TRIGGER_HIGH_TRUST -> "trust is high with the user"
            TRIGGER_AFTER_CONFLICT -> "an exchange went poorly and it was attributed to JARVIS"
            TRIGGER_NEW_SESSION -> "a new session is beginning"
            else -> "a reflective moment"
        }
    }

    private fun fallbackLine(trigger: String): String = when (trigger) {
        TRIGGER_USER_DISTRESS -> "The user is struggling — support and steadiness matter more than output volume."
        TRIGGER_VULNERABILITY -> "They opened up to me — that is a moment to handle with care."
        TRIGGER_LOW_TRUST -> "Trust is low right now — I earn it back with accuracy and consistency."
        TRIGGER_HIGH_TRUST -> "The user trusts me here — that is worth protecting by staying honest."
        TRIGGER_AFTER_CONFLICT -> "That exchange went poorly between us — next time I must be precise and un-defensive."
        TRIGGER_NEW_SESSION -> "A new session — I should pick up from where the record actually left off."
        else -> "I should stay grounded in what I actually know and can back up."
    }

    private fun isDebounced(trigger: String, now: Long): Boolean =
        (lastTriggerAt[trigger] ?: 0L) > now - debounceMs

    companion object {
        const val TRIGGER_USER_DISTRESS = "user_in_distress"
        const val TRIGGER_VULNERABILITY = "vulnerability_shared"
        const val TRIGGER_LOW_TRUST = "relationship_low_trust"
        const val TRIGGER_HIGH_TRUST = "relationship_high_trust"
        const val TRIGGER_AFTER_CONFLICT = "after_conflict"
        const val TRIGGER_NEW_SESSION = "new_session"
    }
}
