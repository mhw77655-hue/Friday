package com.jarvis.app.cognition.workspace

/**
 * The real, in-memory [Workspace]: the claim store the production composition
 * wires and the per-turn organs publish into.
 *
 * It is deliberately the whole implementation — no storage target, no network
 * call, no platform binding. Claims are ephemeral by construction: they live
 * for the process, and a fresh turn supersedes the previous turn's claim of the
 * same kind. A durable record of anything stays in memory (the galaxy graph /
 * provenance ledger); the workspace only carries what is true RIGHT NOW.
 *
 * Synchronized because the real turn path publishes from a coroutine scope while
 * the server's own threads can read.
 */
class InMemoryWorkspace : Workspace {

    private val lock = Any()

    /** Insertion-ordered so [claims] is stable before it is sorted by strength. */
    private val byId = LinkedHashMap<String, Claim>()

    /**
     * Ids retired by a [Claim.supersedes] pointer. Kept beside the claims rather
     * than as a field on them: supersession is a fact ABOUT the workspace's
     * history, and a published claim is immutable.
     */
    private val supersededIds = LinkedHashSet<String>()

    /** When the decay clock was last advanced; null until the first tick. */
    private var lastTickAt: Long? = null

    override fun publish(claim: Claim) {
        synchronized(lock) {
            // The workspace is the boundary an untrusted organ hands state to,
            // so it re-checks the schema rather than trusting the constructor.
            claim.validateSchema()
            val existing = byId[claim.id]
            if (existing != null) {
                require(claim.hasSameIdentity(existing)) {
                    "claim '${claim.id}' is already published with different content; " +
                        "a claim id is its identity and may only settle wasCorrect"
                }
                require(existing.wasCorrect == null || existing.wasCorrect == claim.wasCorrect) {
                    "claim '${claim.id}' already settled its prediction outcome as " +
                        "${existing.wasCorrect}; an outcome is set once"
                }
            } else {
                claim.supersedes?.let { supersededIds.add(it) }
            }
            byId[claim.id] = claim
        }
    }

    override fun claims(kind: ClaimKind, personId: String?): List<Claim> = synchronized(lock) {
        byId.values
            .filter { it.kind == kind && (personId == null || it.personId == personId) }
            .sortedWith(STRONGEST_FIRST)
    }

    override fun current(kind: ClaimKind, personId: String?): Claim? = synchronized(lock) {
        byId.values
            .filter {
                it.kind == kind &&
                    (personId == null || it.personId == personId) &&
                    it.isLive(superseded = supersededIds.contains(it.id))
            }
            .sortedWith(STRONGEST_FIRST)
            .firstOrNull()
    }

    override fun tick(nowMillis: Long) {
        synchronized(lock) {
            val last = lastTickAt
            lastTickAt = nowMillis
            // The first tick only starts the clock; an earlier instant is ignored
            // rather than rewinding a clock that already moved.
            val elapsed = if (last == null) 0L else (nowMillis - last).coerceAtLeast(0L)
            val steps = (elapsed / Workspace.TICK_INTERVAL_MS).toInt()
            if (steps <= 0) return@synchronized
            val ids = byId.keys.toList()
            for (id in ids) {
                val claim = byId.getValue(id)
                if (claim.confidence <= 0.0) continue
                byId[id] = claim.copy(confidence = (claim.confidence - claim.decayRate * steps).coerceAtLeast(0.0))
            }
        }
    }

    private companion object {
        /**
         * Strongest first, with a total order so a set of equal-confidence claims
         * always answers the same way: newest wins, then id.
         */
        val STRONGEST_FIRST: Comparator<Claim> = compareByDescending<Claim> { it.confidence }
            .thenByDescending { it.createdAt }
            .thenBy { it.id }
    }
}
