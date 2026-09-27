package com.jarvis.app.cognition.workspace

/**
 * The shared cognitive workspace: the ONE place an organ publishes what it
 * knows about this turn, and the only place another organ is allowed to read it
 * from. No subsystem reads another by name.
 *
 * Contract, in the terms the story fixes:
 *  - [publish] validates the claim's schema and retains it. Nothing is ever
 *    deleted: losing a claim to supersession or decay only changes what
 *    [current] answers, never what [claims] can still show.
 *  - [claims] returns every retained claim of one kind (optionally narrowed to
 *    one person), strongest first.
 *  - [current] returns the single highest-confidence LIVE claim of one kind —
 *    the winner among conflicting claims of the same turn.
 *  - [tick] advances the decay clock; every claim loses [Claim.decayRate] per
 *    elapsed [TICK_INTERVAL_MS].
 *
 * A `personId` of null on a query means "no person filter", which is why
 * person-scoped queries and unscoped claims are both expressible before the
 * later multi-person gate lands.
 */
interface Workspace {

    /**
     * Retain [claim]. Re-publishing an existing id is the prediction-outcome
     * path: the claim's identity must be unchanged and [Claim.wasCorrect] may
     * only go from null to a value, exactly once. Publishing a claim that names
     * [Claim.supersedes] marks that claim superseded.
     *
     * @throws IllegalArgumentException when the claim fails its schema check.
     */
    fun publish(claim: Claim)

    /**
     * Every retained claim of [kind] for [personId] (null = no person filter),
     * ordered strongest first: confidence desc, then newest first, then id so
     * the order is total and repeatable.
     */
    fun claims(kind: ClaimKind, personId: String? = null): List<Claim>

    /**
     * The highest-confidence live claim of [kind] for [personId], or null when
     * every claim of that kind has been superseded or decayed to zero.
     */
    fun current(kind: ClaimKind, personId: String? = null): Claim?

    /**
     * Advance the decay clock to [nowMillis]. The first tick only starts the
     * clock; every later tick decays each claim by [Claim.decayRate] per
     * elapsed [TICK_INTERVAL_MS]. Passing an earlier instant is ignored rather
     * than rewinding.
     */
    fun tick(nowMillis: Long = System.currentTimeMillis())

    companion object {
        /** The decay clock's resolution: one [Claim.decayRate] step per second. */
        const val TICK_INTERVAL_MS = 1_000L
    }
}
