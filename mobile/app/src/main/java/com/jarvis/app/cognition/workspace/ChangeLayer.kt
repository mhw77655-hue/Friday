package com.jarvis.app.cognition.workspace

/**
 * CONTINUITY-LAW: HOW FAST a piece of identity-adjacent state is allowed to
 * change, and therefore what a change to it must prove before it is accepted.
 *
 * The three layers exist because the real identity subsystems in this repo write
 * at three genuinely different tempos:
 *
 *  - [FAST] state is rebuilt from live signal every call and never had a
 *    durability story to protect. [SelfModel]'s own contract says it outright:
 *    capabilities, limitations, confidence and goals are all DERIVED from live
 *    sources ([CapabilityRegistry], [StageHistorySource], [GoalSource]), and the
 *    per-turn mental state / context window are recomputed each turn. A change
 *    here is unrestricted — rate-limiting a value that is recomputed on the next
 *    call would be theatre, not safety.
 *  - [SLOW] state is durable and identity-adjacent: the persona trait values
 *    `PersonaTuner` persists as `persona:<trait>` facts, and the person-model
 *    trust tier `PersonRelationshipModel.setTrust` persists as
 *    `relationship:<person>:trust`. It is allowed to change, but only once enough
 *    REAL accumulated evidence claims support it and the previous accepted change
 *    to the same field is far enough in the past — and every accepted change is
 *    appended to a queryable [ChangeLog].
 *  - [CORE] state is identity itself: the `name` and `version` of
 *    [SelfModel.identity] ([SelfIdentity], read live from [IdentitySource]).
 *    This mechanism REJECTS a CORE change unconditionally: those fields stay
 *    governed by the pre-existing frozen-invariant path (`identity.root` is
 *    registered `isFixedInvariant` in `JarvisOrganGraph`, and Human Core's
 *    Identity store only accepts a revision through its own token-gated writer),
 *    which this story leaves untouched.
 *
 * WHY THE LAYER IS DERIVED, NOT DECLARED: a caller that could pass its own
 * layer could label a name change FAST and walk straight through the gate. So
 * [forTarget] computes the layer from the claim's KIND plus the FIELD it names,
 * against the real predicate vocabulary this repo already writes, and a
 * classification it cannot make is an error rather than a silent FAST.
 */
enum class ChangeLayer {
    /** Unrestricted: recomputed from live signal, never a durable identity change. */
    FAST,

    /** Durable and identity-adjacent: evidence-counted, rate-limited, and logged. */
    SLOW,

    /** Identity itself: rejected by this mechanism, governed by the frozen path. */
    CORE;

    companion object {
        /**
         * Namespace the CORE self-model fields are addressed under in a
         * [Claim.target]: the real [SelfIdentity] fields are a bare `name` and
         * `version`, and a target is shared with the other identity kinds, so the
         * self-model's own claims carry this prefix. This is a claim-level field
         * namespace, NOT a graph predicate — the graph predicate vocabulary
         * belongs to `PersonRelationshipModel` / `WorldModelService`.
         */
        const val IDENTITY_PREFIX: String = "identity:"

        /** Real predicate prefix for durable persona trait values (PersonaTuner). */
        const val PERSONA_PREFIX: String = "persona:"

        /**
         * Real predicate prefix for a per-person relationship fact; the trust
         * tier is the `relationship:<person>:trust` triple
         * [PersonRelationshipModel.setTrust] writes on the user node.
         */
        const val RELATIONSHIP_PREFIX: String = "relationship:"

        /** Suffix of the real per-person trust-tier predicate. */
        const val TRUST_SUFFIX: String = ":trust"

        /**
         * The [SelfIdentity] field names that are identity rather than live
         * derived state — the CORE set. A whitelist, never a blacklist: an
         * unrecognised self-model field is FAST because SelfModel documents every
         * non-identity field as derived from a live source, while a field that
         * cannot be classified at all is an error.
         */
        val SELF_MODEL_IDENTITY_FIELDS: List<String> = listOf("name", "version")

        /**
         * The layer of a change to [target] of kind [kind].
         *
         * @throws IllegalArgumentException when an identity-adjacent kind names no
         *   target, or a target outside the real vocabulary of its kind: an
         *   unclassifiable change must never be waved through as FAST.
         */
        fun forTarget(kind: ClaimKind, target: String?): ChangeLayer {
            if (!kind.isIdentity) return FAST
            val field = target?.trim()
            require(!field.isNullOrBlank()) {
                "identity-adjacent claim kind $kind must name the field it changes"
            }
            return when (kind) {
                ClaimKind.SELF_MODEL -> when {
                    field.startsWith(IDENTITY_PREFIX) || field.lowercase() in SELF_MODEL_IDENTITY_FIELDS -> CORE
                    // Every other self-model field is derived from a live source
                    // (capability/limitation state, confidence, history, goals).
                    else -> FAST
                }
                ClaimKind.PERSONA_TRAIT -> {
                    require(field.startsWith(PERSONA_PREFIX)) {
                        "persona trait target must start with '$PERSONA_PREFIX' (the real " +
                            "predicate PersonaTuner writes), was '$field'"
                    }
                    SLOW
                }
                ClaimKind.TRUST_TIER -> {
                    require(field.startsWith(RELATIONSHIP_PREFIX) && field.endsWith(TRUST_SUFFIX)) {
                        "trust tier target must be the real predicate " +
                            "'${RELATIONSHIP_PREFIX}<person>$TRUST_SUFFIX' that " +
                            "PersonRelationshipModel.setTrust writes, was '$field'"
                    }
                    SLOW
                }
                else -> FAST
            }
        }
    }
}
