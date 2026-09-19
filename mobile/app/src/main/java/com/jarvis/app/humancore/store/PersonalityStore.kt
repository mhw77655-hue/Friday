package com.jarvis.app.humancore.store

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * The Personality Engine store (§5): a fixed set of named trait dimensions,
 * each a bounded scalar with a current value, a slow baseline, a shrinking
 * learning rate, and a compact change history (enough to explain "why is
 * JARVIS more direct now than six months ago", not a full log of every
 * micro-update).
 *
 * Writer discipline: the ONLY writer is the Growth & Evolution Engine
 * ([com.jarvis.app.humancore.mod.GrowthEngine]) through [applyBatch] — never
 * a single message. Batching is mandatory, not a performance optimization:
 * it is what keeps personality "slow" (§2, §17). The trait vector is read by
 * Conversation Style Controller, Internal Dialogue Engine, Self-Reflection,
 * and the Consistency Guard (to check style outputs are trait-consistent).
 *
 * Failure handling: if the store is unreadable, fall back to the documented
 * default trait vector — a severity-2 failure (degraded personality
 * expression, not a safety issue), logged but not alerting (§5).
 *
 * Clamping: trait values are always clamped to [0, 1] independently of the
 * update algorithm (§0.11 runaway-state guard). No trait may ever cross into
 * contradicting a hard-boundary value — the Guard enforces that at the
 * Expression layer regardless of trait value (§5 Constraints).
 */
class PersonalityStore(
    private val storage: StoragePort,
    private val clock: () -> Long
) {

    /** Documented default trait vector, used at cold start and on failure reset. */
    private val defaultTraits: Map<String, Double> = mapOf(
        TRAIT_DIRECTNESS to 0.60,
        TRAIT_WARMTH to 0.75,
        TRAIT_HUMOR_FREQUENCY to 0.55,
        TRAIT_FORMALITY to 0.20,
        TRAIT_PROACTIVENESS to 0.45,
        TRAIT_CURIOSITY to 0.70
    )

    // ConcurrentHashMap: with the Integration Pass running on its own thread
    // (§0.12, C-1), reads (Expression Pass, main thread) may race with batched
    // writes (Growth Engine, integration thread). A concurrent map keeps both
    // safe without serializing the read path.
    private val traits = ConcurrentHashMap<String, Trait>()

    val defaultLearningRate: Double = 0.20
    val learningRateShrinkFactor: Double = 0.90
    val learningRateFloor: Double = 0.01

    fun load() {
        val raw = storage.read(StoreKind.PERSONALITY)
        if (raw == null) {
            resetToDefaults()
            return
        }
        val parsed = parse(raw)
        if (parsed == null) {
            resetToDefaults()
            return
        }
        traits.clear()
        traits.putAll(parsed)
        // New trait dimensions added to the vector start at their documented
        // default and accumulate from the point they're introduced (§5
        // Extension points) — a stored vector from an older build may lack
        // dimensions the current build documents.
        defaultTraits.forEach { (name, def) ->
            if (traits[name] == null) traits[name] = Trait(name, def, def, defaultLearningRate, clock(), emptyList())
        }
    }

    fun trait(name: String): Trait? = traits[name]

    fun traitValue(name: String): Double? = traits[name]?.current

    fun allTraits(): Map<String, Trait> = traits.toMap()

    fun traitNames(): List<String> = defaultTraits.keys.toList()

    /** Documented default for a trait (for Expression Pass baseline math). */
    fun defaultValue(name: String): Double = defaultTraits[name] ?: 0.5

    /**
     * The only write path. Applies a batched set of evidence-backed updates
     * and appends an audit entry per trait (§17 owns the audit trail).
     * Callers must be the Growth & Evolution Engine — no other module.
     */
    internal fun applyBatch(updates: List<TraitUpdate>) {
        updates.forEach { u ->
            val current = traits[u.name] ?: Trait(
                name = u.name,
                current = defaultValue(u.name),
                baseline = defaultValue(u.name),
                learningRate = defaultLearningRate,
                lastUpdatedEpochMs = clock(),
                history = emptyList()
            )
            // §0.9: slow traits accumulate via the shrinking learning rate —
            // each observation's magnitude is damped by the rate, so early
            // evidence moves the trait more than later evidence does. A raw
            // additive step would let a burst of messages (or a hostile
            // exchange) swing a personality trait far too fast.
            val newValue = com.jarvis.app.humancore.algo.Clamp.unit(
                com.jarvis.app.humancore.algo.Accumulate.step(
                    current.current, current.current + u.magnitude, current.learningRate, 1.0
                )
            )
            val newRate = com.jarvis.app.humancore.algo.Accumulate.withShrinkingRate(
                current.learningRate, learningRateShrinkFactor, learningRateFloor
            )
            val history = (current.history + TraitChange(
                ts = clock(),
                evidence = u.evidence,
                magnitude = u.magnitude,
                direction = if (u.magnitude >= 0) 1.0 else -1.0
            )).let { h -> if (h.size > HISTORY_LIMIT) h.takeLast(HISTORY_LIMIT) else h }

            traits[u.name] = current.copy(
                current = newValue,
                baseline = current.baseline,
                learningRate = newRate,
                lastUpdatedEpochMs = clock(),
                history = history
            )
        }
        persist()
    }

    /** Severity-2 failure reset: return to the documented defaults. */
    internal fun resetToDefaults() {
        traits.clear()
        defaultTraits.forEach { (name, def) ->
            traits[name] = Trait(name, def, def, defaultLearningRate, clock(), emptyList())
        }
    }

    internal fun persist() {
        storage.write(StoreKind.PERSONALITY, serialize(traits))
    }

    private fun serialize(map: Map<String, Trait>): String {
        val root = JSONObject()
        root.put("_updatedEpochMs", clock()) // sync clock hint (§21.2 LWW/average merge)
        val arr = JSONArray()
        map.forEach { (name, t) ->
            val obj = JSONObject()
                .put("name", name)
                .put("current", t.current)
                .put("baseline", t.baseline)
                .put("learningRate", t.learningRate)
                .put("lastUpdatedEpochMs", t.lastUpdatedEpochMs)
            val hist = JSONArray()
            t.history.forEach { h ->
                hist.put(
                    JSONObject()
                        .put("ts", h.ts)
                        .put("evidence", h.evidence)
                        .put("magnitude", h.magnitude)
                        .put("direction", h.direction)
                )
            }
            obj.put("history", hist)
            arr.put(obj)
        }
        root.put("traits", arr)
        return root.toString()
    }

    private fun parse(json: String): Map<String, Trait>? {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("traits") ?: JSONArray()
            val map = mutableMapOf<String, Trait>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val histArr = o.optJSONArray("history") ?: JSONArray()
                val hist = mutableListOf<TraitChange>()
                for (j in 0 until histArr.length()) {
                    val h = histArr.getJSONObject(j)
                    hist.add(
                        TraitChange(
                            ts = h.optLong("ts"),
                            evidence = h.optString("evidence"),
                            magnitude = h.optDouble("magnitude"),
                            direction = h.optDouble("direction", 1.0)
                        )
                    )
                }
                map[o.getString("name")] = Trait(
                    name = o.getString("name"),
                    current = o.optDouble("current"),
                    baseline = o.optDouble("baseline"),
                    learningRate = o.optDouble("learningRate"),
                    lastUpdatedEpochMs = o.optLong("lastUpdatedEpochMs"),
                    history = hist
                )
            }
            map
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val TRAIT_DIRECTNESS = "directness"
        const val TRAIT_WARMTH = "warmth"
        const val TRAIT_HUMOR_FREQUENCY = "humorFrequency"
        const val TRAIT_FORMALITY = "formality"
        const val TRAIT_PROACTIVENESS = "proactiveness"
        const val TRAIT_CURIOSITY = "curiosity"
        private const val HISTORY_LIMIT = 40
    }
}

/** One trait dimension's full state (§5 owned data). */
data class Trait(
    val name: String,
    val current: Double,
    val baseline: Double,
    val learningRate: Double,
    val lastUpdatedEpochMs: Long,
    val history: List<TraitChange>
)

/** One compact change-history entry, enough to explain a trait's movement. */
data class TraitChange(
    val ts: Long,
    val evidence: String,
    val magnitude: Double,
    val direction: Double
)

/**
 * A single batched trait update produced by the Growth & Evolution Engine
 * (§17). `magnitude` is the signed movement applied to the trait's current
 * value (already confidence-weighted by the engine).
 */
data class TraitUpdate(
    val name: String,
    val magnitude: Double,
    val evidence: String
)
