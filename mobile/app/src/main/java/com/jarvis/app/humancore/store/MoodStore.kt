package com.jarvis.app.humancore.store

import com.jarvis.app.humancore.algo.Clamp
import com.jarvis.app.humancore.algo.Decay
import org.json.JSONObject

/**
 * The Mood System store (§6): a small set of bounded scalar affect
 * dimensions — a minimal two-axis model (valence: negative↔positive, and
 * arousal: calm↔energized) that deliberately avoids over-engineering a large
 * emotion taxonomy. This is JARVIS's own internal weather, distinct from its
 * read of the USER's emotional state (which is the Emotional Intelligence
 * module's output, §7) and from long-term character (Personality, §5).
 *
 * Writer discipline: the ONLY writer is the Emotional Regulation module
 * ([com.jarvis.app.humancore.mod.EmotionalRegulation]) through [applyNudge].
 * Mood is never written directly by conversation content — content flows
 * through Emotional Regulation first, which decides whether and how much to
 * let an event move the mood (§6 Inputs).
 *
 * Decay: mood decays continuously toward baseline using the one approved
 * mechanism (§0.9 bounded exponential decay), computed LAZILY on read —
 * "what would the value be right now given elapsed time" is evaluated at
 * query time, never on a timer, to avoid unnecessary wakeups on a
 * resource-constrained device (§6, §0.14).
 *
 * Failure handling: if unreadable, reset to neutral baseline — a severity-3
 * (cosmetic) failure, logged, no alert. Losing mood on app reinstall or long
 * absence is acceptable and arguably realistic (§6 Persistence, Low tier).
 *
 * Constraint: mood may influence *style* but must never influence *values
 * compliance* — the Consistency Guard ignores mood entirely when checking
 * hard boundaries (§6 Constraints, §20).
 */
class MoodStore(
    private val storage: StoragePort,
    private val clock: () -> Long
) {
    @Volatile private var state: MoodState = neutral()

    // Decay is per-field tunable (§0.9). Half-life ~1 hour at the default
    // factor — mood is minutes-to-hours, not weeks.
    private val valenceDecayFactor: Double = 0.5   // per hour
    private val arousalDecayFactor: Double = 0.5   // per hour
    private val decayUnitMs: Long = 3_600_000L     // 1 hour

    fun load() {
        val raw = storage.read(StoreKind.MOOD)
        if (raw == null) {
            state = neutral()
            return
        }
        val parsed = parse(raw)
        state = parsed ?: neutral()
    }

    /** The stored (un-decayed) state, for persistence/debugging. */
    fun stored(): MoodState = state

    /**
     * The effective mood "right now": stored values decayed toward baseline
     * for the elapsed time since the last write. Pure, lazy, cheap.
     */
    fun effective(now: Long): MoodState {
        val elapsed = (now - state.lastUpdateEpochMs).coerceAtLeast(0L)
        val units = elapsed.toDouble() / decayUnitMs.toDouble()
        val valence = Decay.value(state.valence, state.valenceBaseline, valenceDecayFactor, units)
        val arousal = Decay.value(state.arousal, state.arousalBaseline, arousalDecayFactor, units)
        return MoodState(
            valence = valence,
            arousal = arousal,
            valenceBaseline = state.valenceBaseline,
            arousalBaseline = state.arousalBaseline,
            valenceDecayFactor = valenceDecayFactor,
            arousalDecayFactor = arousalDecayFactor,
            lastUpdateEpochMs = now
        )
    }

    /**
     * The only write path — Emotional Regulation module only.
     * Simple additive deltas within a single event, clamped to [-1, 1]
     * independently of the update math (§0.11). Persisted immediately: the
     * store is tiny and §0.14 requires no Human Core state to live only in
     * memory across a session boundary.
     */
    internal fun applyNudge(valenceDelta: Double, arousalDelta: Double, now: Long) {
        // Fold in elapsed decay first: the nudge is applied to the EFFECTIVE
        // value ("what the mood would be right now"), never to the stored,
        // un-decayed value — otherwise every write restarts the decay clock and
        // erases all elapsed time (§6, §0.9). See HUMAN_CORE_AUDIT C-3.
        val eff = effective(now)
        state = MoodState(
            valence = Clamp.unitSymmetric(eff.valence + valenceDelta),
            arousal = Clamp.unitSymmetric(eff.arousal + arousalDelta),
            valenceBaseline = state.valenceBaseline,
            arousalBaseline = state.arousalBaseline,
            valenceDecayFactor = valenceDecayFactor,
            arousalDecayFactor = arousalDecayFactor,
            lastUpdateEpochMs = now
        )
        persist()
    }

    /**
     * Slowly re-point a dimension's baseline (used by Emotional Regulation
     * for the very slow coupling from Personality warmth/energy-adjacent
     * traits — a baseline is nudged, never set equal to a trait, §6).
     */
    internal fun nudgeBaselines(valenceBaselineDelta: Double, arousalBaselineDelta: Double, now: Long) {
        // The valence/arousal carried forward must be the decayed effective
        // values (toward the OLD baseline), not the stored un-decayed ones —
        // otherwise re-pointing the baseline would also erase elapsed decay
        // (C-3). Future decay then proceeds toward the NEW baseline.
        val eff = effective(now)
        state = MoodState(
            valence = eff.valence,
            arousal = eff.arousal,
            valenceBaseline = Clamp.unitSymmetric(state.valenceBaseline + valenceBaselineDelta),
            arousalBaseline = Clamp.unitSymmetric(state.arousalBaseline + arousalBaselineDelta),
            valenceDecayFactor = valenceDecayFactor,
            arousalDecayFactor = arousalDecayFactor,
            lastUpdateEpochMs = now
        )
        persist()
    }

    /** Severity-3 reset: neutral baseline, logged, no alert. */
    internal fun reset() {
        state = neutral()
        persist()
    }

    internal fun persist() {
        storage.write(StoreKind.MOOD, serialize(state))
    }

    private fun neutral(): MoodState = MoodState(
        valence = 0.0,
        arousal = 0.0,
        valenceBaseline = 0.0,
        arousalBaseline = 0.0,
        valenceDecayFactor = valenceDecayFactor,
        arousalDecayFactor = arousalDecayFactor,
        lastUpdateEpochMs = clock()
    )

    private fun serialize(s: MoodState): String {
        return JSONObject()
            .put("valence", s.valence)
            .put("arousal", s.arousal)
            .put("valenceBaseline", s.valenceBaseline)
            .put("arousalBaseline", s.arousalBaseline)
            .put("valenceDecayFactor", s.valenceDecayFactor)
            .put("arousalDecayFactor", s.arousalDecayFactor)
            .put("lastUpdateEpochMs", s.lastUpdateEpochMs)
            .toString()
    }

    private fun parse(json: String): MoodState? {
        return try {
            val o = JSONObject(json)
            MoodState(
                valence = o.optDouble("valence"),
                arousal = o.optDouble("arousal"),
                valenceBaseline = o.optDouble("valenceBaseline"),
                arousalBaseline = o.optDouble("arousalBaseline"),
                valenceDecayFactor = o.optDouble("valenceDecayFactor", valenceDecayFactor),
                arousalDecayFactor = o.optDouble("arousalDecayFactor", arousalDecayFactor),
                lastUpdateEpochMs = o.optLong("lastUpdateEpochMs")
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Two-axis mood state (§6 owned data). Values are [-1, 1]; decay per hour. */
data class MoodState(
    val valence: Double,
    val arousal: Double,
    val valenceBaseline: Double,
    val arousalBaseline: Double,
    val valenceDecayFactor: Double,
    val arousalDecayFactor: Double,
    val lastUpdateEpochMs: Long
)
