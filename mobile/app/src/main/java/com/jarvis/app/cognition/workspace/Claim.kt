package com.jarvis.app.cognition.workspace

/**
 * ONE piece of state an organ is willing to share, addressed by [kind] instead
 * of by naming the organ that produced it.
 *
 * This is the replacement for named wires: a subsystem that needs the current
 * mental state asks the [Workspace] for [ClaimKind.MENTAL_STATE]; it does not
 * hold a reference to the estimator that happened to compute it. A subsystem
 * that needs a prediction must be able to prove the prediction beat guessing,
 * so a [ClaimKind.PREDICTION] claim cannot exist without a [baseline] and a
 * [wasCorrect] outcome slot.
 *
 * A claim is IMMUTABLE. The only field that may change after construction is
 * [wasCorrect], and only from null to a value, through [resolved] — that is what
 * "set once the real next turn confirms or contradicts it" means in code. The
 * decay of [confidence] over time is the workspace's arithmetic, never a
 * mutation of the published claim.
 *
 * @param id unique within one workspace; re-publishing an existing id is the
 *   outcome-resolution path, not a new claim.
 * @param payload the shared content. A text rendering of whatever the organ
 *   published; the codec that produced it is the organ's own business.
 * @param confidence in 0.0..1.0. What it means is the publishing organ's to
 *   document: a measured reading's own confidence, or an explicitly declared
 *   prior — never a silently invented number.
 * @param sourceOrgan the organ that published it. Recorded so a reader can see
 *   WHERE a claim came from without holding a reference to that organ.
 * @param personId reserved for the later multi-person gate: null means the
 *   claim is not scoped to a person. Reserved NOW so the schema never needs a
 *   breaking migration when scoping arrives.
 * @param decayRate confidence lost per [Workspace.TICK_INTERVAL_MS] once
 *   [Workspace.tick] advances past the claim's age. 0.0 = does not decay.
 * @param createdAt millis the organ published it.
 * @param supersedes id of the claim this one replaces. The superseded claim is
 *   kept (never deleted) but stops being live.
 * @param baseline REQUIRED for a [ClaimKind.PREDICTION]: what the naive
 *   most-common-answer baseline would have said instead. Forbidden for every
 *   other kind, so a prediction cannot hide behind a non-predictive kind.
 * @param wasCorrect the PREDICTION outcome: null until a real later turn
 *   confirms or contradicts it, then true/false exactly once.
 * @param target CONTINUITY-LAW: for an identity-adjacent claim, the FIELD being
 *   asserted ("persona:directness", "relationship:Alice:trust", "name"). It is
 *   what the [layer] is computed FROM, so the caller cannot declare its own tempo
 *   and wave a CORE change through as FAST. Null for a claim that is not about a
 *   named identity field.
 */
data class Claim(
    val id: String,
    val kind: ClaimKind,
    val payload: String,
    val confidence: Double,
    val sourceOrgan: String,
    val personId: String? = null,
    val decayRate: Double = 0.0,
    val createdAt: Long,
    val supersedes: String? = null,
    val baseline: String? = null,
    val wasCorrect: Boolean? = null,
    val target: String? = null
) {
    init {
        validateSchema()
    }

    /** True when this claim is about the future and must be provable. */
    val isPrediction: Boolean get() = kind.isPrediction

    /**
     * CONTINUITY-LAW: how fast this claim's field is allowed to change. Derived
     * from the KIND plus the FIELD, never declared by the publisher — see
     * [ChangeLayer.forTarget].
     */
    val layer: ChangeLayer get() = ChangeLayer.forTarget(kind, target)

    /** True when the outcome of a prediction has been settled by a real turn. */
    val isResolved: Boolean get() = wasCorrect != null

    /**
     * Whether this claim is still answering questions: not superseded, and not
     * decayed away to zero confidence. A dead claim is still RETAINED and
     * queryable through [Workspace.claims] — decay and supersession change what
     * is current, never what happened.
     */
    fun isLive(superseded: Boolean = false): Boolean = !superseded && confidence > 0.0

    /**
     * Whether [other] is the same claim (same id, content, provenance and
     * timing) — i.e. re-publishing it may only settle [wasCorrect], never
     * rewrite the claim. [confidence] is deliberately excluded: the workspace
     * decays a stored claim's confidence over time, so a resolved outcome
     * published after a tick still matches the stored identity.
     */
    fun hasSameIdentity(other: Claim): Boolean =
        id == other.id &&
            kind == other.kind &&
            payload == other.payload &&
            sourceOrgan == other.sourceOrgan &&
            personId == other.personId &&
            decayRate == other.decayRate &&
            createdAt == other.createdAt &&
            supersedes == other.supersedes &&
            baseline == other.baseline &&
            target == other.target

    /**
     * The prediction outcome, settled by a real later turn. Returns the same
     * claim with [wasCorrect] set; the workspace accepts it only if the claim is
     * a prediction and [wasCorrect] was still null.
     */
    fun resolved(correct: Boolean): Claim = copy(wasCorrect = correct)

    /**
     * The runtime schema check every claim must pass, at construction and again
     * at [Workspace.publish] (a workspace is the boundary, so it re-checks what
     * it is handed). In Kotlin the fields are non-null-typed, so this is the
     * real enforcement point: a PREDICTION claim without a baseline cannot be
     * constructed at all.
     */
    fun validateSchema() {
        require(id.isNotBlank()) { "claim id must be non-blank" }
        require(payload.isNotBlank()) { "claim '$id' must carry a non-blank payload" }
        require(sourceOrgan.isNotBlank()) { "claim '$id' must name the organ that published it" }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "claim '$id' confidence must be in 0.0..1.0, was $confidence"
        }
        require(decayRate.isFinite() && decayRate >= 0.0) {
            "claim '$id' decayRate must be >= 0.0, was $decayRate"
        }
        require(createdAt >= 0L) { "claim '$id' createdAt must be a real instant, was $createdAt" }
        require(supersedes != id) { "claim '$id' cannot supersede itself" }
        if (kind.isPrediction) {
            require(!baseline.isNullOrBlank()) {
                "PREDICTION claim '$id' must carry the naive most-common-answer baseline " +
                    "so a later real turn can prove whether it beat guessing"
            }
        } else {
            require(baseline == null) {
                "non-prediction claim '$id' of kind $kind must not carry a prediction baseline"
            }
            require(wasCorrect == null) {
                "non-prediction claim '$id' of kind $kind cannot carry a prediction outcome"
            }
        }
        if (kind.isIdentity) {
            require(!target.isNullOrBlank()) {
                "identity-adjacent claim '$id' of kind $kind must name the field it changes"
            }
        } else {
            require(target == null) {
                "claim '$id' of kind $kind is not identity-adjacent, so it must not name a field"
            }
        }
        // Resolve the layer now rather than at first read: a claim that cannot be
        // classified never exists, so no CORE change can enter the store unlabelled.
        layer
    }

    companion object {
        /**
         * The only honest way to mint a prediction: the kind is fixed to
         * [ClaimKind.PREDICTION] and [baseline] is required, so a caller cannot
         * accidentally publish a guess dressed up as another kind.
         */
        fun prediction(
            id: String,
            payload: String,
            confidence: Double,
            sourceOrgan: String,
            baseline: String,
            createdAt: Long,
            personId: String? = null,
            decayRate: Double = 0.0,
            supersedes: String? = null,
            wasCorrect: Boolean? = null
        ): Claim = Claim(
            id = id,
            kind = ClaimKind.PREDICTION,
            payload = payload,
            confidence = confidence,
            sourceOrgan = sourceOrgan,
            personId = personId,
            decayRate = decayRate,
            createdAt = createdAt,
            supersedes = supersedes,
            baseline = baseline,
            wasCorrect = wasCorrect
        )
    }
}
