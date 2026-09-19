package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.store.RelationshipStore

/**
 * The Relationship Modeling module (§9): builds and maintains the model of
 * "how this person and JARVIS communicate" — the communication baseline and
 * the curated log of significant relationship events.
 *
 * Inputs (§9):
 *  - EI reads (rolling baseline statistics);
 *  - explicit user statements about preferences (which the Integration Pass
 *    also forwards to UserAdaptation, §18);
 *  - internal-dialogue / reflection summaries (self-relevant events);
 *  - growth-derived signals (skill milestones etc.).
 *
 * Constraints honored here:
 *  - NEVER store raw verbatim transcripts as "relationship memory" — only
 *    curated, summarized, relationship-relevant content (§9);
 *  - baseline updates are running statistics, bounded and idempotent — a
 *    message processed twice must not double-count (the Integration Pass
 *    guarantees single processing);
 *  - the significant-event log is bounded and pruned by salience+age, and
 *    the store persists the baseline on every update so process death never
 *    loses an exchange's contribution (§0.14).
 */
class RelationshipModeling(
    private val relationship: RelationshipStore,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    /** The ONLY writer of the communication baseline. Called per genuine user message. */
    fun observe(affect: AffectRead, now: Long = clock()) {
        relationship.updateBaseline(affect, now)
    }

    /**
     * Detect and log a vulnerability-sharing moment (§9). Returns true when a
     * vulnerability event was logged (the Integration Pass uses it to flag
     * the exchange for dialogue/growth/memory).
     */
    fun observeMessage(text: String, now: Long = clock()): Boolean {
        if (!detectVulnerability(text)) return false
        val salience = 0.7
        relationship.addEvent(
            RelationshipStore.CATEGORY_VULNERABILITY,
            summary = "user shared something personally difficult",
            salience = salience,
            now = now
        )
        bus.publish(
            HcEvent.RelationshipEventAdded(
                ts = now, reason = "vulnerability detected in user message",
                category = RelationshipStore.CATEGORY_VULNERABILITY, salience = salience
            )
        )
        return true
    }

    /** Log an explicit communication-preference statement (§9 + §18 cross-feed). */
    fun recordCommunicationPreference(dimension: String, summary: String, now: Long = clock()) {
        relationship.addEvent(
            RelationshipStore.CATEGORY_COMMUNICATION_PREFERENCE,
            summary = "user explicitly stated a preference on $dimension",
            salience = 0.65,
            now = now
        )
        bus.publish(
            HcEvent.RelationshipEventAdded(
                ts = now, reason = "explicit communication preference: $dimension",
                category = RelationshipStore.CATEGORY_COMMUNICATION_PREFERENCE, salience = 0.65
            )
        )
    }

    /**
     * Lightweight vulnerability detection. Deliberately conservative:
     * "this is hard for me", "i'm going through", first-person disclosure
     * markers. No transcripts are stored — only the category + a short
     * summary token.
     */
    private fun detectVulnerability(text: String): Boolean {
        val lower = text.lowercase()
        return VULNERABILITY_PATTERNS.any { lower.contains(it) }
    }

    companion object {
        private val VULNERABILITY_PATTERNS = listOf(
            "i'm going through", "i've been going through", "this is hard for me",
            "i'm struggling", "i'm really struggling", "i opened up", "i don't usually tell anyone",
            "i feel like i can tell you", "i've been dealing with", "this is difficult for me to",
            "i'm having a really tough time", "i need to get this off my chest"
        )
    }
}
