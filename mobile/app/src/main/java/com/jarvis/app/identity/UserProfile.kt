package com.jarvis.app.identity

/**
 * Durable, structured, explicit record of the user's (Venon's) stable
 * preferences and constraints — the durable layer of the User Model.
 *
 * This is deliberately DISTINCT from raw memory retrieval and from the
 * ephemeral per-turn [UserMentalStateEstimator] hypothesis. Durable preferences
 * here are persisted as facts ON the User Model's own graph node via
 * [WorldModelService]/MemoryGraphStore (superseding prior values, never
 * overwriting) — NOT re-derived by re-reading raw conversation history each
 * time.
 *
 * **Durability gate.** A preference only becomes durable on a genuinely
 * durable signal:
 *  - an explicit statement ("I prefer X", "always X", "never X", "please X"),
 *  - a repeated pattern across multiple distinct sessions, OR
 *  - an explicit correction.
 * A single ambiguous utterance NEVER becomes a durable preference.
 */
class UserProfile(
    private val worldModel: WorldModelService,
    /** Number of distinct sessions that must state a signal for it to be durable via repetition. */
    private val repetitionThreshold: Int = 2,
    private val gate: ProfileDurabilityGate = ProfileDurabilityGate()
) {

    private val observationsBySession = mutableMapOf<String, MutableSet<Pair<String, String>>>()

    /**
     * Directly set a durable preference [key]=[value] and persist it onto the
     * User Model's node via [WorldModelService]. Supersedes any prior value for
     * [key] (never an in-place overwrite).
     */
    fun setPreference(key: String, value: String, source: String = "profile") {
        worldModel.setUserPreference(key, value, source)
    }

    /** The currently-valid durable value of [key], or null if unset. */
    fun getPreference(key: String): String? = worldModel.getUserPreference(key)

    /** All currently-valid durable preferences as a map (key -> value). */
    fun allPreferences(): Map<String, String> {
        val node = worldModel.getEntity(WorldModelService.USER_NODE_NAME) ?: return emptyMap()
        val prefix = "preference:"
        return node.facts
            .filter { it.predicate.startsWith(prefix) }
            .associate { it.predicate.removePrefix(prefix) to it.`object` }
    }

    /**
     * Consider a single user utterance in [sessionId]. Runs the durability gate:
     * explicit statement / explicit correction / repeated-across-sessions are
     * committed as durable preferences; a single ambiguous utterance is NOT.
     *
     * @return the durable profile change committed this call, or null when
     *   nothing durable was written.
     */
    fun ingestUtterance(text: String, sessionId: String): ProfileUpdate? {
        val explicit = gate.extractExplicit(text)
        if (explicit != null) {
            val (cat, value) = explicit
            setPreference(cat, value, source = "explicit utterance")
            return ProfileUpdate(cat, value, PromotionReason.EXPLICIT)
        }

        // Not explicit — check repetition across distinct sessions.
        val candidate = gate.extractCandidatePreference(text) ?: return null
        var seen = observationsBySession.getOrPut(sessionId) { mutableSetOf() }
        seen.add(candidate)
        val distinctSessions = observationsBySession.count { (_, sigs) -> sigs.contains(candidate) }
        if (distinctSessions >= repetitionThreshold) {
            val (cat, value) = candidate
            setPreference(cat, value, source = "repeated across sessions")
            return ProfileUpdate(cat, value, PromotionReason.REPEATED_PATTERN)
        }
        return null
    }

    /** Result of committing a durable profile change. */
    data class ProfileUpdate(
        val category: String,
        val value: String,
        val reason: PromotionReason
    )

    enum class PromotionReason { EXPLICIT, REPEATED_PATTERN }
}

/**
 * Decides whether an utterance carries a DURABLE preference signal vs an
 * ephemeral/ambiguous one. Pure + deterministic (no LLM), so the durability
 * contract is testable: explicit directive markers and repeated patterns pass;
 * a single hedged/ambiguous utterance does not.
 */
class ProfileDurabilityGate {

    private val explicitPatterns = listOf(
        Regex("""\bI prefer (?:to )?(.+?)(?:\.|,|!|\?)?$""", RegexOption.IGNORE_CASE),
        Regex("""\bplease always (.+?)(?:\.|!|\?)?$""", RegexOption.IGNORE_CASE),
        Regex("""\balways (.+?)(?:\.|!|\?)?$""", RegexOption.IGNORE_CASE),
        Regex("""\bnever (.+?)(?:\.|!|\?)?$""", RegexOption.IGNORE_CASE),
        Regex("""\bI (?:want|want you to|expect you to|would like|like) (.+?)(?:\.|!|\?)?$""", RegexOption.IGNORE_CASE)
    )

    /**
     * Extract an explicit durable preference `(category, value)` from [text],
     * or null if the text is not an explicit statement.
     */
    fun extractExplicit(text: String): Pair<String, String>? {
        val t = text.trim()
        if (t.length < 6) return null
        for (p in explicitPatterns) {
            val m = p.find(t) ?: continue
            val raw = m.groupValues[1].trim().trimEnd('.', ',', '!', '?')
            if (raw.isBlank()) continue
            val (cat, value) = categorize(t, raw)
            return cat to value
        }
        return null
    }

    /**
     * Extract a non-explicit preference candidate `(category, value)` — e.g. a
     * hedged preference ("prefer dark mode") that only becomes durable when it
     * repeats across sessions. Returns null for pure noise.
     */
    fun extractCandidatePreference(text: String): Pair<String, String>? {
        val t = text.trim()
        val m = Regex("""\bprefer(?:s|ring)? (?:to |toward |)?([^.;!?,]{3,})""", RegexOption.IGNORE_CASE)
            .find(t)
        if (m == null) return null
        val raw = m.groupValues[1].trim().trimEnd('.', ',', '!', '?')
        if (raw.isBlank()) return null
        // Explicit "I prefer"/"always"/"never" is handled by extractExplicit;
        // this is the ambiguous/hedged overlap.
        if (explicitPatterns.any { it.containsMatchIn(t) }) return null
        val (cat, value) = categorize(t, raw)
        return cat to value
    }

    private fun categorize(sentence: String, value: String): Pair<String, String> {
        val lower = sentence.lowercase()
        val category = when {
            lower.contains("answer") || lower.contains("respond") || lower.contains("reply") ||
                lower.contains("concise") || lower.contains("brief") || lower.contains("detail") ||
                lower.contains("explain") -> "communicationStyle"
            lower.contains("friendly") || lower.contains("formal") || lower.contains("tone") -> "tone"
            lower.contains("dark mode") || lower.contains("light mode") || lower.contains("theme") -> "appearance"
            lower.contains("always") || lower.contains("never") -> "standingInstruction"
            else -> "preference"
        }
        return category to value
    }
}
