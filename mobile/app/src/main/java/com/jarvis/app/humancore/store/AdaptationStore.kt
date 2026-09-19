package com.jarvis.app.humancore.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * The User Adaptation store (§18): the durable map of "how this user prefers
 * JARVIS to talk."
 *
 * Persistence is REQUIRED by the spec — explicit preferences are High-tier
 * (a user must never re-state "please be more concise" after a restart) and
 * inferred preferences are Moderate-tier (§21.1). The module
 * ([com.jarvis.app.humancore.mod.UserAdaptation]) was previously memory-only,
 * so every preference was lost on process death (HUMAN_CORE_AUDIT C-6).
 *
 * Conflict class (§21.2): explicit preferences merge last-write-wins by
 * timestamp (class 5); inferred preferences are slow scalars (class 2) and
 * merge confidence-weighted. See [com.jarvis.app.humancore.algo.ConflictResolver].
 *
 * Writer discipline: the ONLY writer is User Adaptation through the write
 * methods below. Writes are persisted immediately (§0.14: no state change may
 * live only in memory across a session boundary).
 */
class AdaptationStore(
    private val storage: StoragePort,
    private val clock: () -> Long
) {
    private val lock = Any()

    // Immutable snapshot maps, swapped on write; @Volatile for cross-thread
    // visibility (the read side runs on the Expression Pass thread, writes on
    // the message and integration threads).
    @Volatile private var numeric: Map<String, Preference> = emptyMap()
    @Volatile private var named: Map<String, NamedPreference> = emptyMap()

    fun load() {
        val raw = storage.read(StoreKind.ADAPTATION) ?: return
        val parsed = parse(raw) ?: return
        synchronized(lock) {
            numeric = parsed.first
            named = parsed.second
        }
    }

    fun readNumeric(): Map<String, Preference> = numeric

    fun readNamed(): Map<String, NamedPreference> = named

    fun writeNumeric(dimension: String, pref: Preference) {
        synchronized(lock) {
            numeric = numeric + (dimension to pref)
            persist()
        }
    }

    fun writeNamed(dimension: String, pref: NamedPreference) {
        synchronized(lock) {
            named = named + (dimension to pref)
            persist()
        }
    }

    /** Erase all adaptation state — used by explicit user-requested reset. */
    fun erase() {
        synchronized(lock) {
            numeric = emptyMap()
            named = emptyMap()
            storage.delete(StoreKind.ADAPTATION)
        }
    }

    internal fun persist() {
        storage.write(StoreKind.ADAPTATION, serialize(numeric, named))
    }

    private fun serialize(n: Map<String, Preference>, nm: Map<String, NamedPreference>): String {
        val root = JSONObject()
        root.put("_updatedEpochMs", clock()) // sync clock hint (§21.2)
        val num = JSONArray()
        n.values.forEach { p ->
            num.put(JSONObject()
                .put("dimension", p.dimension)
                .put("value", p.value)
                .put("source", p.source.name)
                .put("confidence", p.confidence)
                .put("ts", p.ts)
                .put("reason", p.reason))
        }
        root.put("numeric", num)
        val nmArr = JSONArray()
        nm.values.forEach { p ->
            nmArr.put(JSONObject()
                .put("dimension", p.dimension)
                .put("value", p.value)
                .put("source", p.source.name)
                .put("confidence", p.confidence)
                .put("ts", p.ts))
        }
        root.put("named", nmArr)
        return root.toString()
    }

    private fun parse(json: String): Pair<Map<String, Preference>, Map<String, NamedPreference>>? {
        return try {
            val root = JSONObject(json)
            val num = mutableMapOf<String, Preference>()
            val numArr = root.optJSONArray("numeric") ?: JSONArray()
            for (i in 0 until numArr.length()) {
                val o = numArr.getJSONObject(i)
                val d = o.optString("dimension")
                if (d.isBlank()) continue
                num[d] = Preference(
                    dimension = d,
                    value = o.optDouble("value"),
                    source = sourceOf(o.optString("source")),
                    confidence = o.optDouble("confidence"),
                    ts = o.optLong("ts"),
                    reason = o.optString("reason")
                )
            }
            val nm = mutableMapOf<String, NamedPreference>()
            val nmArr = root.optJSONArray("named") ?: JSONArray()
            for (i in 0 until nmArr.length()) {
                val o = nmArr.getJSONObject(i)
                val d = o.optString("dimension")
                if (d.isBlank()) continue
                nm[d] = NamedPreference(
                    dimension = d,
                    value = o.optString("value"),
                    source = sourceOf(o.optString("source")),
                    confidence = o.optDouble("confidence"),
                    ts = o.optLong("ts")
                )
            }
            num to nm
        } catch (e: Exception) {
            null
        }
    }

    private fun sourceOf(name: String): PreferenceSource =
        try { PreferenceSource.valueOf(name) } catch (e: Exception) { PreferenceSource.INFERRED }
}

/** The source/precedence of an adaptation preference (§18: explicit > inferred > default). */
enum class PreferenceSource { EXPLICIT, INFERRED, DEFAULT }

/** One numeric adaptation preference (§18). */
data class Preference(
    val dimension: String,
    val value: Double,
    val source: PreferenceSource,
    val confidence: Double,
    val ts: Long,
    val reason: String = ""
)

/** One named adaptation preference (address/language). */
data class NamedPreference(
    val dimension: String,
    val value: String,
    val source: PreferenceSource,
    val confidence: Double,
    val ts: Long
)

/** Resolved numeric preference with its precedence source (§18). */
data class ResolvedNumeric(
    val value: Double,
    val source: PreferenceSource
)
