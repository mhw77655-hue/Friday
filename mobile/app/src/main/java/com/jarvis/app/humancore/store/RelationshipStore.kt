package com.jarvis.app.humancore.store

import com.jarvis.app.humancore.algo.Clamp
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Relationship Modeling store (§9) with its two children Trust Modeling
 * (§9a) and Long-Term Bond Formation Tracker (§9b), persisted as one logical
 * record because they share the same durability tier (High, §21.1) and the
 * same sync behavior (§9 Synchronization).
 *
 * This is the durable, per-user record of what JARVIS has learned about its
 * relationship with this specific person — NOT world memory (facts that
 * would be true if JARVIS and the user had never met, §2). The discriminator
 * is structural: relationship-relevant content lives here; everything else
 * goes to the Memory subsystem via the Memory Interface (§16).
 *
 * Writer discipline:
 *  - communication baseline: [RelationshipModeling] (§9)
 *  - significant-event log: [RelationshipModeling] (§9) and, for
 *    trust-relevant events, [TrustModeling] (§9a)
 *  - trust scalar/events: [TrustModeling] (§9a)
 *  - bond depth/milestones: [BondFormation] (§9b)
 *  - end-of-session integration (session boundary bookkeeping): the
 *    Integration Pass (§0.5)
 *
 * Constraints honored here: never store raw verbatim conversation transcripts
 * as "relationship memory" — only curated, summarized relationship-relevant
 * content (§9); must support explicit user-requested erasure as a clean,
 * complete operation (§9 Constraints).
 */
class RelationshipStore(
    private val storage: StoragePort,
    private val clock: () -> Long
) {

    @Volatile private var record: RelationshipRecord = freshRecord()

    /**
     * Bind the single per-user owner relationship to an owner identity after a
     * successful Android biometric authorization result (People Voice/Face
     * Memory owner-chain). The binding is durable (persisted here) and the
     * record keys on owner identity + relationship id, never device possession.
     *
     * Single-owner guard: only [com.jarvis.app.humancore.protocol.OwnerBinding.OWNER_RELATIONSHIP_ID]
     * may be bound through this path, and once bound, a different [ownerKey]
     * is refused (the owner relationship belongs to the first authenticated
     * owner; the same owner re-authenticating refreshes the timestamp).
     *
     * @return true when the binding was accepted/refreshed, false when refused
     *   (non-owner relationship id, or a different ownerKey already bound).
     */
    internal fun bindOwner(binding: com.jarvis.app.humancore.protocol.OwnerBinding): Boolean {
        if (binding.relationshipId != com.jarvis.app.humancore.protocol.OwnerBinding.OWNER_RELATIONSHIP_ID) return false
        val current = record.owner
        if (current != null && current.ownerKey != binding.ownerKey) return false
        record = record.copy(owner = binding)
        persist() // §0.14: no state change may live only in memory
        return true
    }

    /** The bound owner identity, or null when no owner binding exists yet. */
    internal fun ownerBinding(): com.jarvis.app.humancore.protocol.OwnerBinding? =
        record.owner

    /** Starting values per spec: trust is earned, not assumed (§9a); bond starts near zero (§9b). */
    private val initialTrust: Double = 0.25
    private val initialBondDepth: Double = 0.0
    private val trustInitialLearningRate: Double = 0.20
    private val trustShrinkFactor: Double = 0.90
    private val trustLearningRateFloor: Double = 0.01

    fun load() {
        val raw = storage.read(StoreKind.RELATIONSHIP)
        if (raw == null) {
            record = freshRecord()
            return
        }
        record = parse(raw) ?: freshRecord()
    }

    fun read(): RelationshipRecord = record

    // ---- Relationship Modeling (baseline + significant events) ----

    /** Running statistics update from one affect read (§9 Algorithms). */
    internal fun updateBaseline(affect: com.jarvis.app.humancore.protocol.AffectRead, now: Long) {
        val b = record.baseline
        val n = b.sampleCount
        // Welford-style incremental mean/spread; cheap per-message statistics.
        val newCount = n + 1
        val valDelta = affect.valence - b.valenceMean
        val aroDelta = affect.arousal - b.arousalMean
        val newValMean = b.valenceMean + valDelta / newCount
        val newAroMean = b.arousalMean + aroDelta / newCount
        val newValM2 = b.valenceM2 + valDelta * (affect.valence - newValMean)
        val newAroM2 = b.arousalM2 + aroDelta * (affect.arousal - newAroMean)
        record = record.copy(
            baseline = CommBaseline(
                valenceMean = newValMean,
                valenceM2 = newValM2,
                arousalMean = newAroMean,
                arousalM2 = newAroM2,
                sampleCount = newCount
            ),
            depth = record.depth.copy(totalInteractions = record.depth.totalInteractions + 1)
        )
        persist() // §0.14: no state change may live only in memory
    }

    /**
     * Add a significant relationship event if it crosses the salience
     * threshold; prune the bounded log by salience+age (§9 Algorithms).
     */
    internal fun addEvent(category: String, summary: String, salience: Double, now: Long) {
        if (salience < EVENT_SALIENCE_THRESHOLD) return
        val event = SignificantEvent(
            id = "evt-${now}-${(record.events.size)}",
            ts = now,
            category = category,
            salience = Clamp.unit(salience),
            summary = summary.take(EVENT_SUMMARY_MAX)
        )
        val merged = (record.events + event)
            .sortedByDescending { it.ts }
            .take(EVENT_LOG_LIMIT)
        record = record.copy(events = merged)
        persist() // §0.14
    }

    // ---- Trust Modeling ----

    /**
     * Apply a trust-relevant observation (asymmetric, §9a). Negative
     * (trust-damaging) events move the scalar faster downward than positive
     * events move it upward — trust is slow to build and fast to lose, a
     * deliberate design choice, not an oversight.
     */
    internal fun applyTrust(observation: Double, confidence: Double, eventSummary: String?, now: Long) {
        val t = record.trust
        val negativeBoost = if (observation < t.trust) TRUST_NEGATIVE_BOOST else 1.0
        val effectiveRate = t.learningRate * negativeBoost
        val newTrust = Clamp.unit(
            t.trust + effectiveRate * (observation - t.trust) * confidence.coerceIn(0.0, 1.0)
        )
        val newRate = com.jarvis.app.humancore.algo.Accumulate.withShrinkingRate(
            t.learningRate, trustShrinkFactor, trustLearningRateFloor
        )
        val history = if (eventSummary != null) {
            // Newest N events are kept (rollover), matching the significant-
            // event log's prune-oldest pattern — previously the FIRST (oldest)
            // events were retained and new entries silently dropped, leaving a
            // permanently stale audit trail (HUMAN_CORE_AUDIT C-4).
            (t.events + TrustEvent(ts = now, summary = eventSummary.take(160), direction = if (newTrust >= t.trust) 1 else -1))
                .takeLast(TRUST_EVENT_LIMIT)
        } else t.events
        record = record.copy(
            trust = TrustState(
                trust = newTrust,
                baseline = t.baseline,
                learningRate = newRate,
                lastUpdateEpochMs = now,
                events = history
            )
        )
        persist() // §0.14
    }

    // ---- Bond Formation ----

    /**
     * Bond depth accumulates via a saturating growth function (fast early
     * growth that increasingly plateaus — §9b), not unbounded linear growth.
     * Called at session boundaries only, never per-message (§9b Lifecycle).
     */
    internal fun growBond(sessionCount: Int, now: Long) {
        val d = record.depth
        val rate = bondLearningRate(d.totalInteractions)
        // totalInteractions counts actual interactions (incremented per
        // message in updateBaseline). Session count only scales the growth
        // step — it must not double-count the counter — but it must actually
        // be used: a 3-message session and a 300-message session are not the
        // same amount of shared history (§9b). Capped so a single enormous
        // session cannot max out bond in one step (HUMAN_CORE_AUDIT C-5).
        val steps = sessionCount.coerceIn(1, BOND_SESSION_STEP_CAP)
        val newDepth = Clamp.unit(d.depth + rate * (1.0 - d.depth) * steps)
        record = record.copy(
            depth = d.copy(
                depth = newDepth,
                totalInteractions = d.totalInteractions,
                lastSessionEpochMs = now
            )
        )
        persist() // §0.14
    }

    /**
     * A long gap since last contact applies a one-time, bounded, partial
     * decay to bond depth — not full decay (reconnecting after absence should
     * feel like picking back up, not starting over, §9b) — and records a
     * reconnection milestone rather than treating the gap as pure loss.
     */
    internal fun applyGapDecay(now: Long) {
        val d = record.depth
        val gapMs = d.lastSessionEpochMs?.let { now - it } ?: 0L
        val oneTimeDecay = 1.0 - GAP_DECAY_FRACTION // e.g. 0.2 -> depth * 0.8
        record = record.copy(
            depth = d.copy(
                depth = Clamp.unit(d.depth * oneTimeDecay),
                longestGapMs = maxOf(d.longestGapMs ?: 0L, gapMs),
                milestones = d.milestones + BOND_MILESTONE_RECONNECTION
            )
        )
        persist() // §0.14
    }

    /** Set a milestone flag once; flags generally persist (§9b). */
    internal fun setMilestone(milestone: String) {
        record = record.copy(depth = record.depth.copy(milestones = record.depth.milestones + milestone))
        persist() // §0.14
    }

    /** Called at cold start of a first interaction (or relationship reset). */
    internal fun resetRelationship(now: Long) {
        record = freshRecord().copy(depth = record.depth.copy(startEpochMs = now))
        persist()
    }

    /** Explicit user-requested erasure: clean, complete removal (§9 Constraints). */
    internal fun erase(now: Long) {
        storage.delete(StoreKind.RELATIONSHIP)
        record = freshRecord()
    }

    internal fun persist() {
        storage.write(StoreKind.RELATIONSHIP, serialize(record))
    }

    private fun bondLearningRate(totalInteractions: Long): Double {
        // Saturating growth slows as interactions accumulate (same shrinking
        // pattern as §0.9), floored so it never goes fully inert.
        val r = trustInitialLearningRate * Math.pow(0.985, totalInteractions.toDouble())
        return r.coerceAtLeast(trustLearningRateFloor)
    }

    private fun freshRecord(): RelationshipRecord = RelationshipRecord(
        baseline = CommBaseline(0.0, 0.0, 0.0, 0.0, 0),
        events = emptyList(),
        trust = TrustState(
            trust = initialTrust,
            baseline = initialTrust,
            learningRate = trustInitialLearningRate,
            lastUpdateEpochMs = 0L,
            events = emptyList()
        ),
        depth = BondState(
            depth = initialBondDepth,
            totalInteractions = 0L,
            startEpochMs = clock(),
            longestGapMs = null,
            lastSessionEpochMs = null,
            milestones = emptySet()
        ),
        owner = null
    )

    private fun serialize(r: RelationshipRecord): String {
        val root = JSONObject()
        root.put("_updatedEpochMs", clock()) // sync clock hint (§21.2)
        root.put("baseline", JSONObject()
            .put("valenceMean", r.baseline.valenceMean)
            .put("valenceM2", r.baseline.valenceM2)
            .put("arousalMean", r.baseline.arousalMean)
            .put("arousalM2", r.baseline.arousalM2)
            .put("sampleCount", r.baseline.sampleCount))

        val ev = JSONArray()
        r.events.forEach { e ->
            ev.put(JSONObject()
                .put("id", e.id)
                .put("ts", e.ts)
                .put("category", e.category)
                .put("salience", e.salience)
                .put("summary", e.summary))
        }
        root.put("events", ev)

        val te = JSONArray()
        r.trust.events.forEach { e ->
            te.put(JSONObject().put("ts", e.ts).put("summary", e.summary).put("direction", e.direction))
        }
        root.put("trust", JSONObject()
            .put("trust", r.trust.trust)
            .put("baseline", r.trust.baseline)
            .put("learningRate", r.trust.learningRate)
            .put("lastUpdateEpochMs", r.trust.lastUpdateEpochMs)
            .put("events", te))

        val ms = JSONArray()
        r.depth.milestones.forEach { m -> ms.put(m) }
        root.put("bond", JSONObject()
            .put("depth", r.depth.depth)
            .put("totalInteractions", r.depth.totalInteractions)
            .put("startEpochMs", r.depth.startEpochMs)
            .put("longestGapMs", r.depth.longestGapMs?.let { it as Any } ?: JSONObject.NULL)
            .put("lastSessionEpochMs", r.depth.lastSessionEpochMs?.let { it as Any } ?: JSONObject.NULL)
            .put("milestones", ms))

        root.put("owner", r.owner?.let { o ->
            JSONObject()
                .put("relationshipId", o.relationshipId)
                .put("ownerKey", o.ownerKey)
                .put("authenticator", o.authenticator)
                .put("authenticatedAtEpochMs", o.authenticatedAtEpochMs)
        } ?: JSONObject.NULL)
        return root.toString()
    }

    private fun parse(json: String): RelationshipRecord? {
        return try {
            val root = JSONObject(json)
            val b = root.getJSONObject("baseline")
            val baseline = CommBaseline(
                valenceMean = b.optDouble("valenceMean"),
                valenceM2 = b.optDouble("valenceM2"),
                arousalMean = b.optDouble("arousalMean"),
                arousalM2 = b.optDouble("arousalM2"),
                sampleCount = b.optInt("sampleCount")
            )
            val eventsArr = root.optJSONArray("events") ?: JSONArray()
            val events = mutableListOf<SignificantEvent>()
            for (i in 0 until eventsArr.length()) {
                val e = eventsArr.getJSONObject(i)
                events.add(SignificantEvent(
                    id = e.optString("id"),
                    ts = e.optLong("ts"),
                    category = e.optString("category"),
                    salience = e.optDouble("salience"),
                    summary = e.optString("summary")
                ))
            }
            val trustObj = root.optJSONObject("trust") ?: JSONObject()
            val tEventsArr = trustObj.optJSONArray("events") ?: JSONArray()
            val tEvents = mutableListOf<TrustEvent>()
            for (i in 0 until tEventsArr.length()) {
                val e = tEventsArr.getJSONObject(i)
                tEvents.add(TrustEvent(
                    ts = e.optLong("ts"),
                    summary = e.optString("summary"),
                    direction = e.optInt("direction", 0)
                ))
            }
            val bondObj = root.optJSONObject("bond") ?: JSONObject()
            val msArr = bondObj.optJSONArray("milestones") ?: JSONArray()
            val milestones = mutableSetOf<String>()
            for (i in 0 until msArr.length()) milestones.add(msArr.getString(i))
            val lastSession = if (bondObj.isNull("lastSessionEpochMs")) null else bondObj.optLong("lastSessionEpochMs")

            val ownerObj = root.optJSONObject("owner")
            val owner = if (ownerObj == null || ownerObj.length() == 0) {
                null
            } else {
                com.jarvis.app.humancore.protocol.OwnerBinding(
                    relationshipId = ownerObj.optString("relationshipId", com.jarvis.app.humancore.protocol.OwnerBinding.OWNER_RELATIONSHIP_ID),
                    ownerKey = ownerObj.optString("ownerKey", ""),
                    authenticator = ownerObj.optString("authenticator", ""),
                    authenticatedAtEpochMs = ownerObj.optLong("authenticatedAtEpochMs", 0L)
                )
            }

            RelationshipRecord(
                baseline = baseline,
                events = events,
                trust = TrustState(
                    trust = Clamp.unit(trustObj.optDouble("trust", initialTrust)),
                    baseline = trustObj.optDouble("baseline", initialTrust),
                    learningRate = trustObj.optDouble("learningRate", trustInitialLearningRate),
                    lastUpdateEpochMs = trustObj.optLong("lastUpdateEpochMs", 0L),
                    events = tEvents
                ),
                depth = BondState(
                    depth = Clamp.unit(bondObj.optDouble("depth", initialBondDepth)),
                    totalInteractions = bondObj.optLong("totalInteractions", 0L),
                    startEpochMs = bondObj.optLong("startEpochMs", clock()),
                    longestGapMs = if (bondObj.has("longestGapMs") && !bondObj.isNull("longestGapMs")) bondObj.optLong("longestGapMs") else null,
                    lastSessionEpochMs = lastSession,
                    milestones = milestones
                ),
                owner = owner
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val CATEGORY_VULNERABILITY = "vulnerability_shared"
        const val CATEGORY_COMMUNICATION_PREFERENCE = "communication_preference"
        const val CATEGORY_TRUST = "trust_event"
        const val CATEGORY_MILESTONE = "milestone"
        const val CATEGORY_RECONNECTION = "reconnection"
        const val BOND_MILESTONE_RECONNECTION = "reconnection_after_gap"

        /**
         * The single "long absence" threshold (§9b/§0.12): the gap after which
         * a returning session is treated as a long absence — bond applies its
         * one-time partial decay and the integration layer emits a reconnection
         * cue. One source of truth, shared by BondFormation and HumanCore
         * (previously duplicated as independent 3-day constants,
         * HUMAN_CORE_AUDIT m-18).
         */
        const val LONG_GAP_THRESHOLD_MS = 3L * 24 * 60 * 60 * 1000

        private const val EVENT_SALIENCE_THRESHOLD = 0.5
        private const val EVENT_LOG_LIMIT = 100
        private const val EVENT_SUMMARY_MAX = 200
        private const val TRUST_EVENT_LIMIT = 60
        private const val TRUST_NEGATIVE_BOOST = 1.8
        private const val GAP_DECAY_FRACTION = 0.2
        private const val BOND_SESSION_STEP_CAP = 10
    }
}

/** Running communication-baseline statistics (§9, §8 input). Welford M2 form. */
data class CommBaseline(
    val valenceMean: Double,
    val valenceM2: Double,
    val arousalMean: Double,
    val arousalM2: Double,
    val sampleCount: Int
) {
    val valenceSpread: Double get() = if (sampleCount > 1) Math.sqrt(valenceM2 / (sampleCount - 1)) else 0.0
    val arousalSpread: Double get() = if (sampleCount > 1) Math.sqrt(arousalM2 / (sampleCount - 1)) else 0.0
}

/** One curated significant relationship event (§9 owned data). */
data class SignificantEvent(
    val id: String,
    val ts: Long,
    val category: String,
    val salience: Double,
    val summary: String
)

/** Trust state and its event history (§9a). */
data class TrustState(
    val trust: Double,
    val baseline: Double,
    val learningRate: Double,
    /**
     * Trust-specific write clock (§21.2): stamped when the trust scalar is
     * written, so conflict resolution can LWW on the trust change itself
     * rather than the whole-document `_updatedEpochMs` (which is bumped by
     * every unrelated persist — every message — and could override a fresher
     * trust write with an older one; HUMAN_CORE_AUDIT C-7).
     */
    val lastUpdateEpochMs: Long,
    val events: List<TrustEvent>
)

data class TrustEvent(
    val ts: Long,
    val summary: String,
    val direction: Int
)

/** Bond depth and shared-history metadata (§9b). */
data class BondState(
    val depth: Double,
    val totalInteractions: Long,
    val startEpochMs: Long,
    val longestGapMs: Long?,
    val lastSessionEpochMs: Long?,
    val milestones: Set<String>
)

/** The full per-user relationship record. */
data class RelationshipRecord(
    val baseline: CommBaseline,
    val events: List<SignificantEvent>,
    val trust: TrustState,
    val depth: BondState,
    /**
     * The owner identity bound to this relationship after a successful Android
     * biometric authorization result (People Voice/Face Memory owner-chain).
     * Null until the owner relationship is bound; persists with the record.
     */
    val owner: com.jarvis.app.humancore.protocol.OwnerBinding? = null
)
