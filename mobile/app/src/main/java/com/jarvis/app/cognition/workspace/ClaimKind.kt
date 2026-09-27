package com.jarvis.app.cognition.workspace

/**
 * WHAT a [Claim] is about, and — for [ClaimKind.PREDICTION] — whether it is a
 * claim about the FUTURE.
 *
 * The tag lives on the KIND, not on the call site, so the guarantee the story
 * demands ("no predictive claim is allowed to exist in the schema without a way
 * to later prove whether it beat guessing") is a property of the type: every
 * [ClaimKind.PREDICTION] claim is schema-checked for a [Claim.baseline] at
 * construction time, and no other kind is allowed to carry one.
 *
 * CONTINUITY-LAW: the last three kinds are the identity-adjacent ones (self
 * model, persona traits, person-model trust tier). Being identity-adjacent is
 * what makes a claim's CHANGE subject to a [ChangeLayer] — free, rate-limited,
 * or frozen — see [ChangeLayer.forTarget].
 */
enum class ClaimKind(val isPrediction: Boolean, private val isIdentityAdjacent: Boolean = false) {
    /**
     * The ephemeral per-turn hypothesis of the user's goal / mood / unstated
     * need. Never durable: it decays and is superseded turn by turn.
     */
    MENTAL_STATE(false),

    /**
     * The deterministic per-turn context-window union (segment turns, salient
     * entities, firewalled cross-session memories, mental state).
     */
    CONTEXT_WINDOW(false),

    /**
     * One reading of an utterance. Several competing readings of the SAME
     * utterance are the normal case: they are published side by side, the
     * highest-confidence one answers [Workspace.current], and the losers stay
     * queryable through [Workspace.claims].
     */
    INTERPRETATION(false),

    /**
     * A claim about what comes NEXT. A prediction is only meaningful if it can
     * later be proven to have beaten the naive guess, so this kind REQUIRES a
     * [Claim.baseline] (what the most-common-answer baseline would have said)
     * and carries a [Claim.wasCorrect] outcome that the next real turn sets
     * once.
     */
    PREDICTION(true),

    /**
     * What the system believes about ITSELF, off the live capability registry
     * (CONTINUITY-LAW). This one kind spans two change layers, so it carries a
     * [Claim.target] naming the field: the real [SelfIdentity] fields (`name`,
     * `version`) are [ChangeLayer.CORE] and every derived field (capability and
     * limitation state, confidence, history, goals) is [ChangeLayer.FAST]. The
     * layer is DERIVED from the field by [ChangeLayer.forTarget], never declared
     * by the caller.
     */
    SELF_MODEL(false, isIdentityAdjacent = true),

    /**
     * A durable persona trait value — the `persona:<trait>` fact
     * [com.jarvis.app.identity.PersonaTuner] persists for the user node, so
     * [ChangeLayer.SLOW] (CONTINUITY-LAW): real, durable, and therefore
     * evidence-counted, rate-limited and logged.
     */
    PERSONA_TRAIT(false, isIdentityAdjacent = true),

    /**
     * The person-model trust tier of one person — the real
     * `relationship:<person>:trust` fact
     * [com.jarvis.app.social.PersonRelationshipModel.setTrust] writes, so
     * [ChangeLayer.SLOW] (CONTINUITY-LAW).
     */
    TRUST_TIER(false, isIdentityAdjacent = true);

    /** Whether this kind is a claim about identity-adjacent state, subject to [ChangeLayer]. */
    val isIdentity: Boolean get() = isIdentityAdjacent

    companion object {
        /** The kind with this [name], or null when the name is not a real kind. */
        fun fromName(name: String): ClaimKind? = values().firstOrNull { it.name == name }
    }
}
