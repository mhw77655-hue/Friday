package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.Exchange
import com.jarvis.app.humancore.protocol.IntegrationContext
import com.jarvis.app.humancore.store.PersonalityStore
import com.jarvis.app.humancore.store.TraitUpdate

/**
 * The Growth & Evolution Engine (§17) — the ONLY writer of the Personality
 * store's trait vector.
 *
 * It converts end-of-exchange signals into small, evidence-backed trait
 * updates, accumulated into a batch and flushed to the store only when the
 * batch is large enough or enough time has passed. The batching is
 * MANDATORY, not an optimization — it is what makes personality "slow"
 * (§17, §5): a single emotional exchange must never visibly move a trait,
 * and updates are batched against manipulation ("be more like X" requests
 * must not be able to bend personality, §17 Constraints).
 *
 * Growth uses DEMONSTRATED signals (the user behaved this way), never
 * requests ("please be X") — that keeps adaptation distinct: requests go to
 * UserAdaptation (expression), behavior goes to Growth (character). Evidence
 * strings are kept short and stored in the trait history so personality
 * changes are explainable in the audit trail (§17 owns the audit trail).
 */
class GrowthEngine(
    private val personality: PersonalityStore,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    private val pending = mutableListOf<TraitUpdate>()
    private val batchFlushThreshold = 8
    private val batchMaxAgeMs = 60 * 60 * 1000L // 1 hour
    private var lastFlushAt: Long = clock()

    /** Feed one completed exchange; evidence may accumulate into a batch. */
    fun observeExchange(exchange: Exchange, ctx: IntegrationContext, now: Long = clock()) {
        val lower = exchange.userText.lowercase()
        val affect = ctx.affect
        val s = affect?.signals ?: emptyMap()

        // Warmth: vulnerability, gratitude, sustained warmth.
        if (ctx.vulnerabilityShared) pending += TraitUpdate(PersonalityStore.TRAIT_WARMTH, 0.04, "user opened up")
        if ((s["gratitude"] ?: 0.0) >= 0.4) pending += TraitUpdate(PersonalityStore.TRAIT_WARMTH, 0.02, "user expressed gratitude")
        if ((affect?.valence ?: 0.0) >= 0.5) pending += TraitUpdate(PersonalityStore.TRAIT_WARMTH, 0.01, "sustained warm exchanges")
        if ((affect?.valence ?: 0.0) < -0.6 && ctx.mentionsJarvis) {
            pending += TraitUpdate(PersonalityStore.TRAIT_WARMTH, -0.02, "negative exchange attributed to JARVIS")
        }
        // Humor: user matches playfulness (sarcasm/lightness).
        if ((s["sarcasm"] ?: 0.0) >= 0.5) pending += TraitUpdate(PersonalityStore.TRAIT_HUMOR_FREQUENCY, 0.02, "user matches playfulness")
        // Proactiveness: user invites initiative.
        if (PROACTIVENESS_INVITES.any { lower.contains(it) }) {
            pending += TraitUpdate(PersonalityStore.TRAIT_PROACTIVENESS, 0.02, "user invites initiative")
        }
        // Curiosity: user invites JARVIS's own perspective.
        if (CURIOSITY_INVITES.any { lower.contains(it) }) {
            pending += TraitUpdate(PersonalityStore.TRAIT_CURIOSITY, 0.01, "user invites JARVIS's perspective")
        }
        // NOTE: style *requests* ("be direct", "less formal") are deliberately
        // NOT personality evidence. Requests adjust EXPRESSION via UserAdaptation
        // (§18); Growth uses DEMONSTRATED signals only (§17) — otherwise a user
        // could bend character by asking, which the batching is meant to resist.

        maybeFlush(now)
    }

    /**
     * Flush pending trait updates to the store, aggregated per trait. This is
     * the ONLY point the Personality store's write method is called from.
     */
    fun flush(now: Long = clock()) {
        if (pending.isEmpty()) return
        // The batch is passed through un-aggregated: the store applies each
        // observation with its own shrinking learning rate (§0.9), so a
        // single evidence burst can't sum into one oversized step.
        val batch = pending.toList()
        personality.applyBatch(batch)
        bus.publish(
            HcEvent.PersonalityUpdated(ts = now, reason = "growth batch flushed", traits = personality.allTraits().mapValues { (_, t) -> t.current })
        )
        bus.publish(HcEvent.GrowthUpdated(ts = now, reason = "growth batch flushed", traitCount = batch.size))
        pending.clear()
        lastFlushAt = now
    }

    private fun maybeFlush(now: Long) {
        val age = now - lastFlushAt
        if (pending.size >= batchFlushThreshold || (pending.isNotEmpty() && age >= batchMaxAgeMs)) {
            flush(now)
        }
    }

    companion object {
        private val PROACTIVENESS_INVITES = listOf(
            "just tell me", "decide for me", "surprise me", "go ahead", "you pick",
            "take the lead", "suggest something"
        )
        private val CURIOSITY_INVITES = listOf(
            "what do you think", "tell me more about yourself", "how do you see it",
            "what's your take", "explain", "what would you do"
        )
    }
}
