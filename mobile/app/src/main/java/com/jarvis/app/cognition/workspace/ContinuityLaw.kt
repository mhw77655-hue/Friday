package com.jarvis.app.cognition.workspace

/**
 * CONTINUITY-LAW: the gate every change to identity-adjacent state passes
 * through, and the ONE place a [ChangeLayer] is enforced.
 *
 * The three layers mean three different things, and this class is where they
 * stop being documentation:
 *
 *  - [ChangeLayer.FAST] is accepted and published as-is. It is recomputed from a
 *    live source on the next call, so a rate limit on it would be theatre.
 *  - [ChangeLayer.CORE] is REJECTED, unconditionally and before anything is
 *    retained. A CORE field is identity itself (`name`, `version`); the fields
 *    stay governed by the pre-existing frozen-invariant path, and a change that
 *    reached the store anyway is drift for [ReplayCheck] to catch, not something
 *    this gate papers over.
 *  - [ChangeLayer.SLOW] is accepted only when BOTH hold: enough REAL accumulated
 *    evidence claims for that exact field and value ([minEvidenceClaims]), and
 *    enough REAL elapsed time since the last accepted change to that same field
 *    ([minIntervalMs]). Acceptance is a decision, not a proposal: the accepted
 *    value is republished as the live claim and appended to the [ChangeLog] with
 *    the old value, the new value, the justifying claim ids and the instant.
 *
 * EVIDENCE IS RETAINED, THE VALUE IS NOT: a rejected proposal is still a real
 * claim about a real turn, so it is published (nothing is ever deleted) — but it
 * is published at [EVIDENCE_CONFIDENCE] and it does NOT become the field's value.
 * The [ChangeLog] is the only authority for what a SLOW field holds
 * ([valueOf]), and [ReplayCheck] renders SLOW fields from the log, so an
 * unaccepted value can never appear in an answer.
 *
 * The clock is real ([System.currentTimeMillis]) unless a test injects one, so
 * "minimum real-time interval" means wall-clock time and not a call count.
 */
class ContinuityLaw(
    private val workspace: Workspace,
    private val changeLog: ChangeLog,
    private val now: () -> Long = System::currentTimeMillis,
    private val minEvidenceClaims: Int = DEFAULT_MIN_EVIDENCE_CLAIMS,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS
) {

    init {
        require(minEvidenceClaims >= 2) {
            "a SLOW change must require at least 2 real evidence claims, was $minEvidenceClaims"
        }
        require(minIntervalMs > 0L) {
            "a SLOW change must require a positive real-time interval, was $minIntervalMs"
        }
    }

    /**
     * The verdict on one proposed change, in a form a caller can act on and a
     * test can assert on: what layer it was read as, whether it was accepted,
     * the real evidence it was judged on, and the [ChangeLogEntry] it produced
     * (present exactly when a SLOW change was accepted).
     */
    data class ChangeDecision(
        val claimId: String,
        val target: String,
        val layer: ChangeLayer,
        val accepted: Boolean,
        val reason: String,
        val evidenceClaimIds: List<String>,
        val entry: ChangeLogEntry?
    ) {
        val isAccepted: Boolean get() = accepted
        val isRejected: Boolean get() = !accepted
    }

    /**
     * Offer [claim] to the gate. Every real call either accepts it (publishing the
     * accepted value) or rejects it with a reason — never both, never neither.
     *
     * The layer is read from the claim ([Claim.layer]), i.e. derived from the
     * claim's kind and field, so a caller cannot pick its own tempo.
     */
    fun propose(claim: Claim): ChangeDecision {
        val layer = claim.layer
        val field = claim.target?.trim().orEmpty()

        if (layer == ChangeLayer.CORE) {
            // Rejected BEFORE anything is retained: a CORE change must not even
            // become evidence in the store, or a reader of claims() would find it.
            return ChangeDecision(
                claimId = claim.id,
                target = field,
                layer = layer,
                accepted = false,
                reason = "CORE field '$field' is identity itself: it stays governed by the " +
                    "frozen-invariant path and this mechanism rejects the change outright, " +
                    "whatever the evidence",
                evidenceClaimIds = emptyList(),
                entry = null
            )
        }

        if (layer == ChangeLayer.FAST) {
            workspace.publish(claim)
            return ChangeDecision(
                claimId = claim.id,
                target = field,
                layer = layer,
                accepted = true,
                reason = "FAST field '$field' is derived from a live source, so it is " +
                    "unrestricted: recomputing it is the real rate limit",
                evidenceClaimIds = listOf(claim.id),
                entry = null
            )
        }

        // SLOW: this turn's real claim is evidence like any other, so it is
        // retained — chained onto the previous statement of the same field, never
        // overwriting or deleting it — and it does NOT become the value yet.
        val priorEvidence = evidenceFor(claim, field)
        // A claim may never supersede ITSELF, so a re-proposal of the same id
        // chains onto the previous statement of the field, not onto itself.
        val previousStatement = latestStatementFor(claim.kind, field)?.takeIf { it.id != claim.id }
        val evidenceCopy = claim.copy(
            confidence = EVIDENCE_CONFIDENCE,
            supersedes = previousStatement?.id ?: claim.supersedes
        )
        workspace.publish(evidenceCopy)
        val evidenceIds = (priorEvidence.map { it.id } + claim.id).distinct()
        // The bar is a function of THIS FIELD's own accumulated history, not a
        // number written down once: a value that has already been accepted N
        // times is a more established value, and replacing it must cost more
        // real evidence and more real time than the last replacement did. Both
        // curves saturate, because a requirement that grows without bound would
        // make durable identity-adjacent state permanently unchangeable -- a
        // worse deviation than a flat one, not a better one.
        val acceptedChanges = changeLog.entriesFor(field).size
        val requiredClaims = requiredEvidenceClaims(acceptedChanges, minEvidenceClaims)
        val requiredInterval = requiredIntervalMs(acceptedChanges, minIntervalMs)

        if (evidenceIds.size < requiredClaims) {
            return ChangeDecision(
                claimId = claim.id,
                target = field,
                layer = layer,
                accepted = false,
                reason = "insufficient accumulated evidence for '$field': ${evidenceIds.size} of " +
                    "$requiredClaims real claims state '${claim.payload}' — one turn is a " +
                    "suggestion, not a change",
                evidenceClaimIds = evidenceIds,
                entry = null
            )
        }

        val at = now()
        val lastChangeAt = changeLog.lastChangeAt(field)
        if (lastChangeAt != null && at - lastChangeAt < requiredInterval) {
            return ChangeDecision(
                claimId = claim.id,
                target = field,
                layer = layer,
                accepted = false,
                reason = "rate limited: '$field' was last accepted at $lastChangeAt and only " +
                    "${at - lastChangeAt}ms of real time have passed (minimum ${requiredInterval}ms for a field with $acceptedChanges prior accepted change(s))",
                evidenceClaimIds = evidenceIds,
                entry = null
            )
        }

        val entry = ChangeLogEntry(
            id = "change-$field-$at-${claim.id}",
            target = field,
            layer = ChangeLayer.SLOW,
            oldValue = changeLog.latestValue(field),
            newValue = claim.payload,
            evidenceClaimIds = evidenceIds,
            acceptedClaimId = claim.id,
            timestamp = at
        )
        changeLog.append(entry)
        // The accepted value becomes the live claim for the field, at full
        // confidence. It is published from the SAME evidence copy, so only
        // confidence differs between the two publications of one claim id —
        // a claim id is its identity and may not be rewritten wholesale.
        workspace.publish(evidenceCopy.copy(confidence = ACCEPTED_CONFIDENCE))
        val sinceLast = lastChangeAt
            ?.let { "${at - it}ms since the last accepted change to this field" }
            ?: "no prior accepted change to this field"
        return ChangeDecision(
            claimId = claim.id,
            target = field,
            layer = layer,
            accepted = true,
            reason = "accepted: ${evidenceIds.size} real claims state '${claim.payload}', and $sinceLast",
            evidenceClaimIds = evidenceIds,
            entry = entry
        )
    }

    /** The accepted value of [target] per the durable log, or null if never accepted. */
    fun valueOf(target: String): String? = changeLog.latestValue(target)

    /** The full accepted-change history of [target], oldest first. */
    fun historyOf(target: String): List<ChangeLogEntry> = changeLog.entriesFor(target)

    /** The claim that currently holds the accepted value of [target], or null. */
    fun acceptedClaimOf(target: String): Claim? {
        val acceptedId = changeLog.entriesFor(target).lastOrNull()?.acceptedClaimId ?: return null
        return workspace.claims(ClaimKind.PERSONA_TRAIT)
            .plus(workspace.claims(ClaimKind.TRUST_TIER))
            .plus(workspace.claims(ClaimKind.SELF_MODEL))
            .firstOrNull { it.id == acceptedId }
    }

    /**
     * Real retained claims that already state [field] = the proposed value,
     * EXCLUDING this proposal's own id: a claim is a real turn's statement, so
     * re-proposing the same claim id is a retry, never a second opinion.
     */
    private fun evidenceFor(claim: Claim, field: String): List<Claim> =
        workspace.claims(claim.kind)
            .filter { it.id != claim.id && it.target == field && it.payload == claim.payload }
            .distinctBy { it.id }

    /** The newest retained claim about [field] of [kind], whatever its value. */
    private fun latestStatementFor(kind: ClaimKind, field: String): Claim? =
        workspace.claims(kind)
            .filter { it.target == field }
            .maxWithOrNull(compareBy<Claim>({ it.createdAt }, { it.id }))

    companion object {
        /**
         * Real accumulated evidence claims a SLOW change must be supported by
         * before it is ACCEPTED. One utterance proposing a value is a suggestion;
         * a second independent real turn that says the same thing is evidence.
         */
        const val DEFAULT_MIN_EVIDENCE_CLAIMS: Int = 2

        /**
         * Extra real claims each additional accepted change to one field
         * demands. The bar moves with the field's own history instead of
         * being the same number forever.
         */
        const val EVIDENCE_STEP: Int = 1

        /**
         * Ceiling on the evidence bar. A requirement that grew without a
         * bound would make durable identity-adjacent state unchangeable after
         * enough history, which is a worse deviation than a flat one.
         */
        const val MAX_EVIDENCE_CLAIMS: Int = 4

        /**
         * Ceiling on the interval bar, for the same reason.
         */
        const val MAX_INTERVAL_MS: Long = 60_000L

        /**
         * Real claims a SLOW change to a field with [acceptedChanges] prior
         * accepted changes must be supported by, saturating at
         * [MAX_EVIDENCE_CLAIMS]. At zero prior changes this is [base]
         * unchanged: the first change to a field costs what it always did.
         */
        fun requiredEvidenceClaims(acceptedChanges: Int, base: Int = DEFAULT_MIN_EVIDENCE_CLAIMS): Int =
            (base + acceptedChanges * EVIDENCE_STEP).coerceAtMost(MAX_EVIDENCE_CLAIMS)

        /**
         * Real milliseconds a SLOW change to a field with [acceptedChanges]
         * prior accepted changes must wait, saturating at [MAX_INTERVAL_MS].
         */
        fun requiredIntervalMs(acceptedChanges: Int, base: Long = DEFAULT_MIN_INTERVAL_MS): Long =
            (base * (acceptedChanges + 1L)).coerceAtMost(MAX_INTERVAL_MS)

        /**
         * Minimum REAL elapsed milliseconds between two accepted SLOW changes to
         * the same field, so durable identity-adjacent state cannot be rewritten
         * turn after turn.
         */
        const val DEFAULT_MIN_INTERVAL_MS: Long = 5_000L

        /**
         * Confidence a retained-but-UNACCEPTED proposal carries. It is a real
         * claim about a real turn, and it is deliberately the lowest confidence in
         * the store so it can never win [Workspace.current] against an accepted
         * value for the same field.
         */
        const val EVIDENCE_CONFIDENCE: Double = 0.25

        /**
         * Confidence the gate publishes an ACCEPTED value at: a change that passed
         * the evidence count and the real-time interval is stated without hedging.
         */
        const val ACCEPTED_CONFIDENCE: Double = 1.0
    }
}
