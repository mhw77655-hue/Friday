package com.jarvis.app.humancore

import com.jarvis.app.humancore.algo.ConflictResolver
import com.jarvis.app.humancore.store.StoreKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conflict resolution semantics for the five spec classes (§21.2):
 * version (identity), slow-average (personality), fast-LWW (mood),
 * union+dedup (relationship, dialogue). The transport is absent; the
 * resolution logic is real and deterministic.
 */
class ConflictResolverTest {

    @Test
    fun `identity merge is version-gated, tie goes local`() {
        val local = """{"version":1,"name":"JARVIS"}"""
        val remote = """{"version":2,"name":"Revised"}"""
        assertEquals(remote, ConflictResolver.resolve(StoreKind.IDENTITY, local, remote))

        val localV2 = """{"version":2,"name":"LocalDevice"}"""
        val remoteV2 = """{"version":2,"name":"RemoteDevice"}"""
        // Equal versions -> local wins (device is authority on its own identity, §0.4).
        assertEquals(localV2, ConflictResolver.resolve(StoreKind.IDENTITY, localV2, remoteV2))
    }

    @Test
    fun `null side handling returns the surviving document`() {
        val doc = """{"version":1}"""
        assertEquals(doc, ConflictResolver.resolve(StoreKind.IDENTITY, null, doc))
        assertEquals(doc, ConflictResolver.resolve(StoreKind.IDENTITY, doc, null))
        assertNull(ConflictResolver.resolve(StoreKind.IDENTITY, null, null))
    }

    @Test
    fun `mood merge is last-writer-wins on lastUpdateEpochMs`() {
        val old = """{"lastUpdateEpochMs":1000,"valence":0.2}"""
        val newer = """{"lastUpdateEpochMs":2000,"valence":0.8}"""
        // Whichever SIDE carries the newer clock wins, regardless of which
        // side is passed as "local".
        assertEquals(newer, ConflictResolver.resolve(StoreKind.MOOD, old, newer))
        assertEquals(newer, ConflictResolver.resolve(StoreKind.MOOD, newer, old))
    }

    @Test
    fun `personality merge averages trait values and unions history`() {
        val local = """{"_updatedEpochMs":1000,"traits":[
            {"name":"warmth","current":0.4,"baseline":0.5,"learningRate":0.2,"lastUpdatedEpochMs":1000,"history":[
                {"ts":1,"evidence":"a"}]}]}"""
        val remote = """{"_updatedEpochMs":2000,"traits":[
            {"name":"warmth","current":0.8,"baseline":0.5,"learningRate":0.2,"lastUpdatedEpochMs":2000,"history":[
                {"ts":2,"evidence":"b"}]}]}"""
        val merged = JSONObject(ConflictResolver.resolve(StoreKind.PERSONALITY, local, remote)!!)
        val trait = merged.getJSONArray("traits").getJSONObject(0)
        assertEquals(0.6, trait.getDouble("current"), 1e-9)
        assertEquals(0.5, trait.getDouble("baseline"), 1e-9)
        // both history entries survive, deduped
        assertEquals(2, trait.getJSONArray("history").length())
        assertEquals(2000L, merged.getLong("_updatedEpochMs"))
    }

    @Test
    fun `relationship merge unions events by id and averages slow scalars`() {
        val local = """{"_updatedEpochMs":1000,
            "baseline":{"valenceMean":0.1,"valenceM2":0.0,"arousalMean":0.0,"arousalM2":0.0,"sampleCount":10},
            "events":[{"id":"evt-1","ts":1,"category":"x","salience":0.7,"summary":"one"}],
            "trust":{"trust":0.3,"baseline":0.5,"learningRate":0.2,"events":[]},
            "bond":{"depth":0.2,"totalInteractions":10,"startEpochMs":100,"longestGapMs":null,"lastSessionEpochMs":null,"milestones":[]}}"""
        val remote = """{"_updatedEpochMs":2000,
            "baseline":{"valenceMean":0.5,"valenceM2":0.0,"arousalMean":0.0,"arousalM2":0.0,"sampleCount":20},
            "events":[{"id":"evt-2","ts":2,"category":"y","salience":0.7,"summary":"two"}],
            "trust":{"trust":0.9,"baseline":0.5,"learningRate":0.2,"events":[]},
            "bond":{"depth":0.6,"totalInteractions":20,"startEpochMs":100,"longestGapMs":null,"lastSessionEpochMs":null,"milestones":[]}}"""
        val merged = JSONObject(ConflictResolver.resolve(StoreKind.RELATIONSHIP, local, remote)!!)

        // events: union, dedup, both present
        assertEquals(2, merged.getJSONArray("events").length())
        // baseline: confidence-weighted mean + pooled M2, sample counts summed
        assertEquals((0.1 * 10 + 0.5 * 20) / 30.0, merged.getJSONObject("baseline").getDouble("valenceMean"), 1e-9)
        assertEquals(30, merged.getJSONObject("baseline").getInt("sampleCount"))
        // pooled valence M2 = lM2 + rM2 + (n1*n2/n)*(mean1-mean2)^2
        val pooledM2 = (10.0 * 20.0 / 30.0) * (0.1 - 0.5) * (0.1 - 0.5)
        assertEquals(pooledM2, merged.getJSONObject("baseline").getDouble("valenceM2"), 1e-9)
        // trust: legacy docs (no per-trust clock) fall back to the doc clock
        assertEquals(0.9, merged.getJSONObject("trust").getDouble("trust"), 1e-9)
        // bond: depth averaged, totalInteractions SUMMED (§21.2)
        assertEquals(0.4, merged.getJSONObject("bond").getDouble("depth"), 1e-9)
        assertEquals(30L, merged.getJSONObject("bond").getLong("totalInteractions"))
        assertEquals(2000L, merged.getLong("_updatedEpochMs"))
    }

    @Test
    fun `trust resolves by its own clock, not the whole-document clock`() {
        // Both docs carry trust-specific clocks; the doc whose trust is NEWER
        // wins even when the WHOLE-document clock on the other side is newer
        // (the doc clock is bumped by every unrelated baseline persist).
        val local = """{"_updatedEpochMs":3000,
            "baseline":{"valenceMean":0.0,"valenceM2":0.0,"arousalMean":0.0,"arousalM2":0.0,"sampleCount":1},
            "events":[],
            "trust":{"trust":0.3,"baseline":0.5,"learningRate":0.2,"lastUpdateEpochMs":1000,"events":[]},
            "bond":{"depth":0.0,"totalInteractions":1,"startEpochMs":100,"longestGapMs":null,"lastSessionEpochMs":null,"milestones":[]}}"""
        val remote = """{"_updatedEpochMs":1000,
            "baseline":{"valenceMean":0.0,"valenceM2":0.0,"arousalMean":0.0,"arousalM2":0.0,"sampleCount":1},
            "events":[],
            "trust":{"trust":0.9,"baseline":0.5,"learningRate":0.2,"lastUpdateEpochMs":2000,"events":[]},
            "bond":{"depth":0.0,"totalInteractions":1,"startEpochMs":100,"longestGapMs":null,"lastSessionEpochMs":null,"milestones":[]}}"""
        val merged = JSONObject(ConflictResolver.resolve(StoreKind.RELATIONSHIP, local, remote)!!)
        // local has the NEWER whole-doc clock but the OLDER trust write.
        assertEquals(0.9, merged.getJSONObject("trust").getDouble("trust"), 1e-9)
        assertEquals(2000L, merged.getJSONObject("trust").getLong("lastUpdateEpochMs"))
    }

    @Test
    fun `adaptation merge is last-write-wins per explicit preference`() {
        val local = """{"_updatedEpochMs":1000,"numeric":[
            {"dimension":"verbosity","value":0.15,"source":"EXPLICIT","confidence":0.95,"ts":1000,"reason":"explicit request: concise"}],"named":[]}"""
        val remote = """{"_updatedEpochMs":2000,"numeric":[
            {"dimension":"verbosity","value":0.85,"source":"EXPLICIT","confidence":0.95,"ts":2000,"reason":"explicit request: thorough"}],"named":[]}"""
        val merged = JSONObject(ConflictResolver.resolve(StoreKind.ADAPTATION, local, remote)!!)
        val pref = merged.getJSONArray("numeric").getJSONObject(0)
        assertEquals(0.85, pref.getDouble("value"), 1e-9)
        assertEquals(2000L, pref.getLong("ts"))
    }

    @Test
    fun `unknown top-level fields survive the whitelist-rebuild merges`() {
        // The whitelist-rebuild merges (personality/relationship/adaptation)
        // previously dropped any top-level key they didn't know about, which
        // breaks the additive-extension rule (§21.4) — a newer build's field
        // would vanish after the first sync.
        val pLocal = """{"_updatedEpochMs":1000,"traits":[],"futureField":"a"}"""
        val pRemote = """{"_updatedEpochMs":2000,"traits":[],"futureField":"b"}"""
        val pMerged = JSONObject(ConflictResolver.resolve(StoreKind.PERSONALITY, pLocal, pRemote)!!)
        assertEquals("a", pMerged.getString("futureField"))

        val rLocal = """{"_updatedEpochMs":1000,"futureRelField":7,
            "baseline":{"valenceMean":0.0,"valenceM2":0.0,"arousalMean":0.0,"arousalM2":0.0,"sampleCount":1},
            "events":[],"trust":{"trust":0.5,"baseline":0.5,"learningRate":0.2,"events":[]},
            "bond":{"depth":0.0,"totalInteractions":1,"startEpochMs":1,"longestGapMs":null,"lastSessionEpochMs":null,"milestones":[]}}"""
        val rRemote = """{"_updatedEpochMs":2000,"futureRelField":9,
            "baseline":{"valenceMean":0.0,"valenceM2":0.0,"arousalMean":0.0,"arousalM2":0.0,"sampleCount":1},
            "events":[],"trust":{"trust":0.5,"baseline":0.5,"learningRate":0.2,"events":[]},
            "bond":{"depth":0.0,"totalInteractions":1,"startEpochMs":1,"longestGapMs":null,"lastSessionEpochMs":null,"milestones":[]}}"""
        val rMerged = JSONObject(ConflictResolver.resolve(StoreKind.RELATIONSHIP, rLocal, rRemote)!!)
        assertEquals(7, rMerged.getInt("futureRelField"))
    }

    @Test
    fun `dialogue merge unions lines and dedups`() {
        val local = "line1\nline2\n"
        val remote = "line2\nline3"
        val merged = ConflictResolver.resolve(StoreKind.DIALOGUE, local, remote)!!
        assertEquals("line1\nline2\nline3", merged)
    }

    @Test
    fun `resolution is deterministic for equal inputs`() {
        val local = """{"version":1,"name":"JARVIS"}"""
        val remote = """{"version":2,"name":"Revised"}"""
        val a = ConflictResolver.resolve(StoreKind.IDENTITY, local, remote)
        val b = ConflictResolver.resolve(StoreKind.IDENTITY, local, remote)
        assertNotNull(a)
        assertEquals(a, b)
        assertTrue(true)
    }
}
