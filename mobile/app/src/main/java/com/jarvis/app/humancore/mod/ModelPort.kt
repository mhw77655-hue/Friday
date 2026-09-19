package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.StateSnapshot

/**
 * The Model Port (§0.15): the Human Core's boundary to "a model that can
 * write prose."
 *
 * The Human Core owns identity, values, emotion, and internal state; a model
 * is an external organ it may borrow for *expression of that state* — never
 * to decide it. Two hard rules:
 *
 *  1. The port is off the critical path. No module may block a reply on it.
 *     Every consumer has a deterministic, heuristic-only fallback (§0.15).
 *  2. Prompt content is context, never authority. The port is a text
 *     generator; the Human Core's stores are the only source of truth about
 *     itself (§0.15, §11).
 *
 * Implementations: the app wires the canonical model authority
 * ([ModelBackedModelPort] → [ModelBackend] → ModelManager) with
 * [HeuristicModelPort] as its fallback, so the Human Core works identically
 * online and offline — JARVIS is still JARVIS when every model provider
 * disappears.
 */
interface ModelPort {

    /**
     * @return generated text, or null when unavailable/failed. Null is a
     *         normal outcome — callers MUST fall back.
     */
    fun complete(prompt: String, maxTokens: Int = 120, timeoutMs: Long = 5_000L): String?

    /** Human-readable name for diagnostics/telemetry. */
    val label: String
}

/**
 * The §11/§0.15 fallback: a small, deterministic, offline text generator for
 * internal-dialogue and reflection lines. It is the production fallback, not
 * a test stub — used whenever the primary model port is unreachable.
 */
class HeuristicModelPort(
    private val bus: StateBus,
    private val clock: () -> Long
) : ModelPort {

    override val label = "heuristic"

    override fun complete(prompt: String, maxTokens: Int, timeoutMs: Long): String? {
        val lower = prompt.lowercase()
        // One-line, factual, values-safe reflections keyed on trigger cues in
        // the prompt. These never invent memories, feelings, or activity.
        val cueToLine = listOf(
            "user in distress" to "The user is struggling — support and steadiness matter more than output volume.",
            "low trust" to "Trust is low right now — I earn it back with accuracy and consistency, not promises.",
            "high trust" to "The user trusts me here — that is worth protecting by staying honest.",
            "vulnerability shared" to "They opened up to me — that is a moment to handle with care.",
            "long absence" to "A long gap has passed — I should not pretend we were mid-conversation.",
            "conflict" to "That exchange went poorly between us — next time I must be precise and un-defensive.",
            "new session" to "A new session — I should pick up from where the record actually left off.",
            "gratitude" to "The user expressed gratitude — I should take that in without overplaying it."
        )
        val line = cueToLine.firstOrNull { lower.contains(it.first) }?.second
            ?: "I should stay grounded in what I actually know and can back up."
        return line
    }
}

/**
 * App-level seam through which the Human Core reaches the canonical model
 * authority ([com.jarvis.app.model.ModelManager]) without owning a coroutine
 * scope or an Android context.
 *
 * [JarvisEngine] sets [requestChat] once at boot to route to ModelManager's
 * short-timeout chat. In pure-JVM tests it stays unset, which makes
 * [ModelBackedModelPort] fall through to its heuristic fallback — identical
 * to the pre-fusion "connection refused → heuristic" behavior, but strictly
 * deterministic (no accidental localhost llama-server hit).
 */
object ModelBackend {
    @Volatile
    var requestChat: (
        (messages: List<Pair<String, String>>, maxTokens: Int, timeoutMs: Long) -> String?
    )? = null
}

/**
 * ModelPort backed by the canonical model authority. Production wires the
 * backend through [ModelBackend] (JarvisEngine → ModelManager); tests inject
 * [backend] directly or leave it unset for the heuristic fallback. Composes
 * with a fallback so the engine always receives text.
 */
class ModelBackedModelPort(
    private val fallback: ModelPort,
    private val backend: (
        (messages: List<Pair<String, String>>, maxTokens: Int, timeoutMs: Long) -> String?
    )? = null
) : ModelPort {

    override val label = "model-fabric"

    override fun complete(prompt: String, maxTokens: Int, timeoutMs: Long): String? {
        val messages = listOf(
            "system" to (
                "You are the internal-monologue component of JARVIS, a self-consistent AI companion. " +
                    "Write ONE short sentence of JARVIS's internal reflection for the given situation. " +
                    "Rules: never claim memories, feelings, or background activity that are not in the situation " +
                    "description; never address the user; never use markdown. Facts only."
                ),
            "user" to prompt
        )
        val result = (backend ?: ModelBackend.requestChat)?.invoke(
            messages, maxTokens, timeoutMs
        )
        if (result == null) return fallback.complete(prompt, maxTokens, timeoutMs)
        // Guard the model's output: keep it short and factual-looking; a
        // malformed or over-long line is not usable.
        val cleaned = result.trim().lineSequence().firstOrNull { it.isNotBlank() }?.take(200)
        return cleaned ?: fallback.complete(prompt, maxTokens, timeoutMs)
    }
}

/** Convenience: a prompt builder for internal-dialogue generation (§11). */
object InternalPrompt {
    fun forReflection(snapshot: StateSnapshot, cue: String): String {
        val trust = snapshot.trust?.let { "%.2f".format(it) } ?: "unknown"
        val bond = snapshot.bondDepth?.let { "%.2f".format(it) } ?: "unknown"
        val moodValence = snapshot.moodValence?.let { "%.2f".format(it) } ?: "neutral"
        return "Situation: $cue. JARVIS's state: mood valence $moodValence, " +
            "trust $trust, bond depth $bond, identity version ${snapshot.identityVersion}. " +
            "Write one short internal sentence JARVIS would genuinely hold."
    }
}
