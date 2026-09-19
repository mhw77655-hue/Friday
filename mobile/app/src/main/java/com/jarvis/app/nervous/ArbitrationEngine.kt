package com.jarvis.app.nervous

/**
 * Arbitration Engine (§9 arbitration layer): resolves conflicting organism
 * decisions.
 *
 * Organisms vote on an outcome with a priority + confidence + freshness;
 * the engine picks the winner deterministically. Ties are broken by explicit
 * policy (priority first, then confidence, then recency). A lower-confidence
 * high-priority organism does NOT beat a higher-confidence low-priority one —
 * priority is the safety governor, confidence is the correctness governor,
 * and both must be balanced (§13: never sacrifice reliability for speed).
 */
class ArbitrationEngine {

    fun arbitrate(claims: List<OrganismClaim>): ArbitratedDecision {
        if (claims.isEmpty()) {
            return ArbitratedDecision(winner = null, consensus = false, claims = claims)
        }
        val winner = claims.maxWithOrNull(compareBy<OrganismClaim> { it.priority }
            .thenBy { it.confidence }
            .thenByDescending { it.freshnessMs })
        return ArbitratedDecision(
            winner = winner,
            consensus = claims.all { it.value == winner?.value },
            claims = claims
        )
    }

    /** Are any two organisms claiming mutually exclusive outcomes? */
    fun hasConflict(claims: List<OrganismClaim>): Boolean {
        val distinct = claims.map { it.value }.distinct()
        return distinct.size > 1
    }
}

/** One organism's claim on an outcome. */
data class OrganismClaim(
    val organismId: String,
    val value: String,
    val priority: Int,
    val confidence: Double,
    val freshnessMs: Long
)

data class ArbitratedDecision(
    val winner: OrganismClaim?,
    val consensus: Boolean,
    val claims: List<OrganismClaim>
)
