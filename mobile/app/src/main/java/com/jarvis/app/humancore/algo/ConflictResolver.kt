package com.jarvis.app.humancore.algo

import com.jarvis.app.humancore.store.StoreKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Conflict resolution for Human Core state arriving from another device
 * (§21.2). Each store follows one of the spec's five documented conflict
 * classes:
 *
 *  - IDENTITY      -> class 1  immutable/audited: version wins; tie -> local.
 *  - PERSONALITY   -> class 2  slow-scalar average: average trait values,
 *                     union history (dedup), keep newest timestamp.
 *  - MOOD          -> class 3  fast-scalar LWW: newest lastUpdateEpochMs wins.
 *  - RELATIONSHIP  -> class 4  append-log union + averaging: union events and
 *                     trust events (dedup), confidence-weighted slow scalars
 *                     (baseline means, bond depth), SUM interaction counters
 *                     (§21.2: literal cumulative counts), trust LWW on its own
 *                     clock.
 *  - ADAPTATION    -> class 5/2: explicit preferences LWW by timestamp; inferred
 *                     preferences confidence-weighted average.
 *  - DIALOGUE      -> class 4  append-log union: union lines, dedup.
 *
 * Tie-break rule across all classes: LOCAL wins. The device is the authority
 * on its own identity and relationship (§0.4) — a remote copy never overrides
 * an equal local one. Every resolution is deterministic, so the same
 * local+remote pair always produces the same merged document (§0.16).
 */
object ConflictResolver {

    /**
     * @return the merged document, or null when both sides are null.
     */
    fun resolve(kind: StoreKind, local: String?, remote: String?): String? {
        if (local == null) return remote
        if (remote == null) return local
        return when (kind) {
            StoreKind.IDENTITY -> byVersion(local, remote)
            StoreKind.PERSONALITY -> mergePersonality(local, remote)
            StoreKind.MOOD -> lastWriterWins(local, remote, "lastUpdateEpochMs")
            StoreKind.RELATIONSHIP -> mergeRelationship(local, remote)
            StoreKind.ADAPTATION -> mergeAdaptation(local, remote)
            StoreKind.DIALOGUE -> unionJsonLines(local, remote)
            // Failure history is append-only JSONL like DIALOGUE — merge by
            // union of lines (dedup by id is handled at read time).
            StoreKind.FAILURES -> unionJsonLines(local, remote)
            // Cognitive continuity is a single state snapshot document —
            // newest snapshot wins (LWW on the document's updatedAt clock).
            StoreKind.CONSOLIDATED_MEMORY -> lastWriterWins(local, remote, "updatedAt")

            // 01H model snapshots are single state documents — newest snapshot
            // wins (LWW on each document's updatedAt clock), like continuity.
            StoreKind.SELF_MODEL,
            StoreKind.USER_MODEL,
            StoreKind.WORLD_MODEL -> lastWriterWins(local, remote, "updatedAt")
        }
    }

    // ---- class 1: identity (versioned, audited) ----
    private fun byVersion(local: String, remote: String): String {
        val lv = try { JSONObject(local).optInt("version", 0) } catch (e: Exception) { 0 }
        val rv = try { JSONObject(remote).optInt("version", 0) } catch (e: Exception) { 0 }
        return if (rv > lv) remote else local
    }

    // ---- class 2: personality (slow-scalar average) ----
    private fun mergePersonality(local: String, remote: String): String {
        val l = try { JSONObject(local) } catch (e: Exception) { return local }
        val r = try { JSONObject(remote) } catch (e: Exception) { return local }
        val lt = traitMap(l.optJSONArray("traits"))
        val rt = traitMap(r.optJSONArray("traits"))
        val names = (lt.keys + rt.keys).toSet()
        val out = JSONArray()
        for (name in names) {
            val lTrait = lt[name]
            val rTrait = rt[name]
            out.put(when {
                lTrait == null -> rTrait
                rTrait == null -> lTrait
                else -> mergeTrait(lTrait, rTrait)
            })
        }
        val merged = JSONObject().put("traits", out)
        merged.put("_updatedEpochMs", maxOf(l.optLong("_updatedEpochMs", 0), r.optLong("_updatedEpochMs", 0)))
        // Additive-extension rule (§21.4): unknown top-level fields from either
        // side survive the merge instead of being dropped by the whitelist
        // rebuild (HUMAN_CORE_AUDIT C-7).
        carryForwardUnknown(merged, setOf("traits", "_updatedEpochMs"), l, r)
        return merged.toString()
    }

    private fun traitMap(arr: JSONArray?): Map<String, JSONObject> {
        val map = mutableMapOf<String, JSONObject>()
        if (arr == null) return map
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o != null) map[o.optString("name")] = o
        }
        return map
    }

    private fun mergeTrait(l: JSONObject, r: JSONObject): JSONObject {
        val current = (l.optDouble("current") + r.optDouble("current")) / 2.0
        val baseline = (l.optDouble("baseline") + r.optDouble("baseline")) / 2.0
        val learningRate = (l.optDouble("learningRate") + r.optDouble("learningRate")) / 2.0
        val lastUpdated = maxOf(l.optLong("lastUpdatedEpochMs"), r.optLong("lastUpdatedEpochMs"))
        val history = unionHistory(l.optJSONArray("history"), r.optJSONArray("history"))
        return JSONObject()
            .put("name", l.optString("name"))
            .put("current", current)
            .put("baseline", baseline)
            .put("learningRate", learningRate)
            .put("lastUpdatedEpochMs", lastUpdated)
            .put("history", history)
    }

    private fun unionHistory(a: JSONArray?, b: JSONArray?): JSONArray {
        val seen = linkedSetOf<String>()
        val out = JSONArray()
        val sources = listOfNotNull(a, b)
        for (arr in sources) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i)?.toString() ?: continue
                if (seen.add(s)) out.put(arr.getJSONObject(i))
            }
        }
        return out
    }

    // ---- class 3: mood (fast-scalar LWW) ----
    private fun lastWriterWins(local: String, remote: String, clockField: String): String {
        val lc = try { JSONObject(local).optLong(clockField, 0) } catch (e: Exception) { 0 }
        val rc = try { JSONObject(remote).optLong(clockField, 0) } catch (e: Exception) { 0 }
        return if (rc > lc) remote else local
    }

    // ---- class 4: relationship (union + average) ----
    private fun mergeRelationship(local: String, remote: String): String {
        val l = try { JSONObject(local) } catch (e: Exception) { return local }
        val r = try { JSONObject(remote) } catch (e: Exception) { return local }

        val out = JSONObject()
        out.put("_updatedEpochMs", maxOf(l.optLong("_updatedEpochMs", 0), r.optLong("_updatedEpochMs", 0)))

        // baseline: confidence-weighted running-mean merge. Two Welford
        // accumulators (mean + M2 + count) combine as a weighted mean and a
        // pooled M2 — NOT a plain average with a max()'d sample count, which
        // was statistically incoherent (HUMAN_CORE_AUDIT C-7).
        val lb = l.optJSONObject("baseline")
        val rb = r.optJSONObject("baseline")
        if (lb != null && rb != null) {
            val lCount = lb.optInt("sampleCount")
            val rCount = rb.optInt("sampleCount")
            if (lCount == 0 && rCount == 0) out.put("baseline", lb)
            else if (lCount == 0) out.put("baseline", rb)
            else if (rCount == 0) out.put("baseline", lb)
            else {
                val n = lCount + rCount
                val w = lCount.toDouble() * rCount.toDouble() / n
                val lm = lb.optDouble("valenceMean")
                val rm = rb.optDouble("valenceMean")
                val am = lb.optDouble("arousalMean")
                val arm = rb.optDouble("arousalMean")
                out.put("baseline", JSONObject()
                    .put("valenceMean", (lm * lCount + rm * rCount) / n)
                    .put("valenceM2", lb.optDouble("valenceM2") + rb.optDouble("valenceM2") + w * (lm - rm) * (lm - rm))
                    .put("arousalMean", (am * lCount + arm * rCount) / n)
                    .put("arousalM2", lb.optDouble("arousalM2") + rb.optDouble("arousalM2") + w * (am - arm) * (am - arm))
                    .put("sampleCount", n))
            }
        } else out.put("baseline", lb ?: rb)

        // events: append-log union, dedup by id, newest first.
        out.put("events", unionById(l.optJSONArray("events"), r.optJSONArray("events"), "id"))

        // trust: LWW on the trust's OWN clock (the store stamps
        // `lastUpdateEpochMs` only when the trust scalar actually changes). The
        // whole-document `_updatedEpochMs` is bumped by EVERY persist (every
        // message's baseline update), so a newer unrelated write could override
        // a fresher trust change (HUMAN_CORE_AUDIT C-7). Legacy docs without a
        // trust clock fall back to the doc clock.
        val lt = l.optJSONObject("trust")
        val rt = r.optJSONObject("trust")
        if (lt != null && rt != null) {
            val lTs = lt.optLong("lastUpdateEpochMs", 0L).takeIf { it > 0 } ?: l.optLong("_updatedEpochMs", 0)
            val rTs = rt.optLong("lastUpdateEpochMs", 0L).takeIf { it > 0 } ?: r.optLong("_updatedEpochMs", 0)
            val newer = if (rTs > lTs) rt else lt
            out.put("trust", JSONObject()
                .put("trust", newer.optDouble("trust"))
                .put("baseline", (lt.optDouble("baseline") + rt.optDouble("baseline")) / 2.0)
                .put("learningRate", (lt.optDouble("learningRate") + rt.optDouble("learningRate")) / 2.0)
                .put("lastUpdateEpochMs", maxOf(lTs, rTs))
                .put("events", unionById(lt.optJSONArray("events"), rt.optJSONArray("events"), "ts")))
        } else out.put("trust", lt ?: rt)

        // bond: depth averages (slow scalar), interaction counts SUMME (they
        // are literal cumulative counts, §9b/§21.2), milestones union.
        val lbd = l.optJSONObject("bond")
        val rbd = r.optJSONObject("bond")
        if (lbd != null && rbd != null) {
            out.put("bond", JSONObject()
                .put("depth", (lbd.optDouble("depth") + rbd.optDouble("depth")) / 2.0)
                .put("totalInteractions", lbd.optLong("totalInteractions") + rbd.optLong("totalInteractions"))
                .put("startEpochMs", minOf(lbd.optLong("startEpochMs"), rbd.optLong("startEpochMs")))
                .put("longestGapMs", unionNullableLong(lbd.optString("longestGapMs"), rbd.optString("longestGapMs")) { a, b -> maxOf(a, b) })
                .put("lastSessionEpochMs", unionNullableLong(lbd.optString("lastSessionEpochMs"), rbd.optString("lastSessionEpochMs")) { a, b -> maxOf(a, b) })
                .put("milestones", unionStringArray(lbd.optJSONArray("milestones"), rbd.optJSONArray("milestones"))))
        } else out.put("bond", lbd ?: rbd)

        // Additive-extension rule (§21.4): unknown top-level fields survive.
        carryForwardUnknown(out, setOf("_updatedEpochMs", "baseline", "events", "trust", "bond"), l, r)
        return out.toString()
    }

    // ---- class 5/2: user adaptation (explicit LWW, inferred confidence-weighted) ----
    private fun mergeAdaptation(local: String, remote: String): String {
        val l = try { JSONObject(local) } catch (e: Exception) { return local }
        val r = try { JSONObject(remote) } catch (e: Exception) { return local }

        val lNum = prefMap(l.optJSONArray("numeric"))
        val rNum = prefMap(r.optJSONArray("numeric"))
        val numOut = JSONArray()
        for (dim in lNum.keys + rNum.keys) {
            numOut.put(mergeNumericPref(lNum[dim], rNum[dim]))
        }
        val lNm = namedMap(l.optJSONArray("named"))
        val rNm = namedMap(r.optJSONArray("named"))
        val nmOut = JSONArray()
        for (dim in lNm.keys + rNm.keys) {
            nmOut.put(mergeNamedPref(lNm[dim], rNm[dim]))
        }

        val out = JSONObject()
            .put("_updatedEpochMs", maxOf(l.optLong("_updatedEpochMs", 0), r.optLong("_updatedEpochMs", 0)))
            .put("numeric", numOut)
            .put("named", nmOut)
        carryForwardUnknown(out, setOf("_updatedEpochMs", "numeric", "named"), l, r)
        return out.toString()
    }

    private fun prefMap(arr: JSONArray?): Map<String, JSONObject> {
        val map = mutableMapOf<String, JSONObject>()
        if (arr == null) return map
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val d = o.optString("dimension")
            if (d.isNotBlank()) map[d] = o
        }
        return map
    }

    private fun namedMap(arr: JSONArray?): Map<String, JSONObject> =
        prefMap(arr)

    /** Explicit beats inferred; within a source, newest ts wins. */
    private fun mergeNumericPref(lp: JSONObject?, rp: JSONObject?): JSONObject {
        if (lp == null) return rp!!
        if (rp == null) return lp
        val lExpl = lp.optString("source") == "EXPLICIT"
        val rExpl = rp.optString("source") == "EXPLICIT"
        return when {
            lExpl && rExpl -> if (rp.optLong("ts") > lp.optLong("ts")) rp else lp
            lExpl -> lp
            rExpl -> rp
            // Both inferred: confidence-weighted average of the values (§21.2 class 2).
            else -> {
                val lc = lp.optDouble("confidence").coerceAtLeast(0.0)
                val rc = rp.optDouble("confidence").coerceAtLeast(0.0)
                if (lc + rc <= 0) {
                    if (rp.optLong("ts") > lp.optLong("ts")) rp else lp
                } else {
                    val value = (lp.optDouble("value") * lc + rp.optDouble("value") * rc) / (lc + rc)
                    JSONObject()
                        .put("dimension", lp.optString("dimension"))
                        .put("value", value)
                        .put("source", "INFERRED")
                        .put("confidence", Math.min(1.0, lc + rc))
                        .put("ts", maxOf(lp.optLong("ts"), rp.optLong("ts")))
                        .put("reason", if (rp.optLong("ts") > lp.optLong("ts")) rp.optString("reason") else lp.optString("reason"))
                }
            }
        }
    }

    private fun mergeNamedPref(lp: JSONObject?, rp: JSONObject?): JSONObject {
        if (lp == null) return rp!!
        if (rp == null) return lp
        val lExpl = lp.optString("source") == "EXPLICIT"
        val rExpl = rp.optString("source") == "EXPLICIT"
        return when {
            lExpl && rExpl -> if (rp.optLong("ts") > lp.optLong("ts")) rp else lp
            lExpl -> lp
            rExpl -> rp
            else -> if (rp.optLong("ts") > lp.optLong("ts")) rp else lp
        }
    }

    /**
     * Forward-compatible merge: any top-level key neither side defines that
     * isn't already in [out] is carried over verbatim, so a newer build's
     * additive fields survive a merge with an older build's documents
     * (§21.4 additive-extension rule).
     */
    private fun carryForwardUnknown(out: JSONObject, known: Set<String>, vararg sides: JSONObject) {
        for (side in sides) {
            val it = side.keys()
            while (it.hasNext()) {
                val key = it.next()
                if (key !in known && !out.has(key)) out.put(key, side.get(key))
            }
        }
    }

    private fun unionById(a: JSONArray?, b: JSONArray?, key: String): JSONArray {
        val seen = linkedSetOf<String>()
        val out = JSONArray()
        for (arr in listOfNotNull(a, b)) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString(key, o.toString())
                if (seen.add(id)) out.put(o)
            }
        }
        return out
    }

    private fun unionNullableLong(local: String, remote: String, pick: (Long, Long) -> Long): Any {
        val l = local.toLongOrNull()
        val r = remote.toLongOrNull()
        return when {
            l != null && r != null -> pick(l, r) as Any
            l != null -> l as Any
            r != null -> r as Any
            else -> JSONObject.NULL
        }
    }

    private fun unionStringArray(a: JSONArray?, b: JSONArray?): JSONArray {
        val seen = linkedSetOf<String>()
        val out = JSONArray()
        for (arr in listOfNotNull(a, b)) {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotEmpty() && seen.add(s)) out.put(s)
            }
        }
        return out
    }

    // ---- class 4: dialogue (append-log union) ----
    private fun unionJsonLines(local: String, remote: String): String {
        val seen = linkedSetOf<String>()
        for (line in local.lines() + remote.lines()) {
            if (line.isNotBlank()) seen.add(line)
        }
        return seen.joinToString("\n")
    }
}
