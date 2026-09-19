package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.StateSnapshot
import com.jarvis.app.humancore.store.DialogueEntry
import com.jarvis.app.humancore.store.DialogueLog
import com.jarvis.app.humancore.store.RelationshipStore

/**
 * The Self-Reflection module (§15): periodically summarizes the human core's
 * internal narrative into a compact, factual periodic summary.
 *
 * It reads the Internal Dialogue log and the Relationship store and produces
 * ONE short, honest paragraph about "how this period went". The summary is
 * stored as a special internal-dialogue entry (trigger `self_reflection`) so
 * the log remains self-contained and the reflection itself becomes part of
 * the narrative for the next period.
 *
 * Constraints honored here:
 *  - summaries are factual summaries of *logged* state — no invented
 *    insights, no performance, no fabrication (§15, §20);
 *  - the summary is internal; surfacing it to the user is a separate,
 *    gated choice that goes through the Expression Pass (§14/§15 boundary);
 *  - period is time-based (daily) and also runs lazily after a long
 *    absence, so a returning session has a reflection ready without needing
 *    a timer.
 */
class SelfReflection(
    private val dialogue: DialogueLog,
    private val relationship: RelationshipStore,
    private val ism: InternalStateManager,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    private val reflectionIntervalMs = 24 * 60 * 60 * 1000L // daily
    private var lastReflectionTs: Long = 0L

    /**
     * @param force  true on session start after a long absence — reflect
     *               regardless of interval (§15 Lifecycle).
     */
    fun maybeReflect(now: Long = clock(), force: Boolean = false) {
        if (lastReflectionTs == 0L) {
            // First call this process: pick the cadence up from the PERSISTED
            // dialogue log so a process restart does not reset the interval and
            // re-reflect immediately (HUMAN_CORE_AUDIT m-11).
            lastReflectionTs = dialogue.read().asReversed()
                .firstOrNull { it.trigger == TRIGGER_SELF_REFLECTION }
                ?.ts ?: 0L
        }
        if (!force && (now - lastReflectionTs) < reflectionIntervalMs) return
        lastReflectionTs = now
        val snapshot = ism.snapshot(now)
        val summary = buildSummary(snapshot)
        dialogue.append(
            DialogueEntry(
                ts = now,
                trigger = TRIGGER_SELF_REFLECTION,
                text = summary.take(400),
                snapshotRef = "v${snapshot.identityVersion}"
            )
        )
        bus.publish(HcEvent.SelfReflectionGenerated(ts = now, reason = "periodic reflection", periodLabel = "daily"))
    }

    private fun buildSummary(snapshot: StateSnapshot): String {
        val rel = relationship.read()
        val recent = dialogue.read().takeLast(5)
        val recentThemes = recent.joinToString("; ") { it.trigger }
        val trust = snapshot.trust?.let { "%.2f".format(it) } ?: "unknown"
        val bond = snapshot.bondDepth?.let { "%.2f".format(it) } ?: "unknown"
        return "Reflection for this period: ${rel.depth.totalInteractions} interactions so far, " +
            "trust ~$trust, bond depth ~$bond, significant events ${rel.events.size}. " +
            "Recent internal themes: ${recentThemes.ifBlank { "none" }}."
    }

    companion object {
        const val TRIGGER_SELF_REFLECTION = "self_reflection"
    }
}
