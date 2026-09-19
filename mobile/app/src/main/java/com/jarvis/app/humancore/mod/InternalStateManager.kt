package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.protocol.StateSnapshot
import com.jarvis.app.humancore.store.MoodStore
import com.jarvis.app.humancore.store.PersonalityStore
import com.jarvis.app.humancore.store.RelationshipStore

/**
 * The Internal State Manager (§10): the unified, read-only interface to "how
 * is JARVIS right now, with this person."
 *
 * Every consumer — the Expression Pass, the Internal Dialogue Engine,
 * Self-Reflection, diagnostics — asks the ISM for ONE [StateSnapshot] instead
 * of reaching into five stores. The ISM aggregates from the stores, the
 * presence manager, and the most recent affect read, and caches the result,
 * invalidating the cache on any State Bus event (§0.10). It is strictly
 * read-only: it publishes nothing and writes nothing.
 *
 * §10 Failure handling: null/absent fields in the snapshot mean "unknown"
 * (e.g. no mood yet), never "neutral" — consumers must distinguish the two.
 */
class InternalStateManager(
    private val identity: IdentityKernel,
    private val personality: PersonalityStore,
    private val mood: MoodStore,
    private val relationship: RelationshipStore,
    private val presence: PresenceManager,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    @Volatile private var cached: StateSnapshot? = null
    @Volatile private var lastAffect: AffectRead? = null
    private var unsubscribe: (() -> Unit)? = null

    /** Max age of a cached snapshot before it is rebuilt (HUMAN_CORE_AUDIT m-9). */
    private val cacheTtlMs = 2_000L

    init {
        // Retained so a graph can be torn down cleanly (tests / graph
        // replacement) instead of leaking a subscriber on the global bus
        // (HUMAN_CORE_AUDIT M-9).
        unsubscribe = bus.subscribe { event ->
            // Any internal change invalidates the composite view.
            invalidate()
        }
    }

    fun dispose() {
        unsubscribe?.invoke()
        unsubscribe = null
    }

    /** Called at session boundaries: a previous session's affect read must not
     *  leak into this one (§7). */
    fun onSessionStart() {
        lastAffect = null
        invalidate()
    }

    /** Register the latest Perception Pass read of the user (§7 output). */
    fun recordAffect(affect: AffectRead) {
        lastAffect = affect
        invalidate()
    }

    fun lastUserAffect(): AffectRead? = lastAffect

    fun invalidate() {
        cached = null
    }

    fun snapshot(now: Long = clock()): StateSnapshot {
        cached?.let { cached ->
            // Time-bounded cache: never return a snapshot older than the TTL,
            // so `secondsSinceLastContact` (and the view generally) cannot go
            // stale across a long idle (m-9).
            if (now - cached.ts < cacheTtlMs) return cached
        }
        val id = identity.read()
        val affect = lastAffect
        val rel = relationship.read()
        val moodState = mood.effective(now)
        val traits = personality.allTraits().mapValues { (_, t) -> t.current }
        val secondsSince = presence.current(now).lastActiveEpochMs?.let { last ->
            ((now - last) / 1000L).coerceAtLeast(0L)
        }

        val snapshot = StateSnapshot(
            ts = now,
            identityName = id.name,
            identityVersion = id.version,
            traits = traits,
            moodValence = moodState.valence,
            moodArousal = moodState.arousal,
            trust = rel.trust.trust,
            bondDepth = rel.depth.depth,
            significantEventCount = rel.events.size,
            totalInteractions = rel.depth.totalInteractions,
            secondsSinceLastContact = secondsSince,
            presence = presence.current(now),
            lastUserValence = affect?.valence,
            lastUserArousal = affect?.arousal,
            lastUserConfidence = affect?.confidence,
            lastUserSignals = affect?.signals ?: emptyMap()
        )
        cached = snapshot
        return snapshot
    }
}
