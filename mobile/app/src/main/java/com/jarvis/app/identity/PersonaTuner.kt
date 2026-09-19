package com.jarvis.app.identity

/**
 * Persistent, offline-capable personality adjustment from EXPLICIT user
 * feedback ("be more direct", "be more like that", "stop doing X"). This is a
 * durable trait adjustment, persisted across sessions onto the User Model's
 * node via [WorldModelService]/MemoryGraphStore — NOT a system-prompt-style
 * instruction that only lasts the current session.
 *
 * Traceability: every adjustment is stored so a later reader can reconstruct
 * BOTH what changed and which feedback triggered it (the trait predicate, the
 * new value, and the triggering utterance carried in the fact's source).
 *
 * Only explicit feedback triggers a change; ambient conversational tone alone
 * never does ([PersonaFeedbackGate]). A later contradicting piece of feedback
 * on the same trait SUPERSEDES the earlier adjustment via the store's existing
 * supersession mechanism — it does not stack indefinitely.
 */
class PersonaTuner(
    private val worldModel: WorldModelService,
    private val gate: PersonaFeedbackGate = PersonaFeedbackGate()
) {

    /**
     * Consider [text] for a persona adjustment. If it is explicit feedback,
     * commit a durable, traceable trait adjustment and return it; otherwise
     * (including pure ambient tone) return null and write nothing.
     */
    fun ingest(text: String): PersonaAdjustment? {
        val feedback = gate.extractFeedback(text) ?: return null
        val adjustment = PersonaAdjustment(
            trait = feedback.trait,
            value = feedback.value,
            triggeredBy = text.trim(),
            ts = System.currentTimeMillis()
        )
        // Persist as a fact on the user node: (Venon, "persona:<trait>", value, "persona: <text>").
        removeOld(adjustment.trait)
        worldModel.persistPersonaAdjustment(adjustment.trait, adjustment.value, adjustment.triggeredBy)
        return adjustment
    }

    /**
     * Supersede any currently-valid persona fact for [trait] before writing the
     * new one, so a contradiction supersedes rather than stacking. Delegated to
     * [WorldModelService] which uses graph supersession internally.
     */
    private fun removeOld(trait: String) {
        // addFact already supersedes same-subject+predicate facts; nothing extra needed.
    }

    /** All currently-effective persona adjustments (trait -> value), plus their triggers. */
    fun adjustments(): List<PersonaAdjustment> {
        val facts = worldModel.userNodeFacts().filter { it.predicate.startsWith("persona:") }
        return facts.map { n ->
            PersonaAdjustment(
                trait = n.predicate.removePrefix("persona:"),
                value = n.`object`,
                triggeredBy = n.source.removePrefix("persona: ").ifBlank { "(unknown)" },
                ts = n.validFrom
            )
        }
    }

    /** The current value of a single persona trait, or null. */
    fun currentValue(trait: String): String? =
        worldModel.userNodeFacts().firstOrNull { it.predicate == "persona:$trait" }?.`object`
}

/** A durable, traceable persona trait adjustment. */
data class PersonaAdjustment(
    val trait: String,
    /** The new direction/value for the trait (e.g. "high", "low"). */
    val value: String,
    /** The exact user feedback that triggered this adjustment. */
    val triggeredBy: String,
    val ts: Long
)

/**
 * Deterministic, offline parser that decides whether a user utterance is
 * EXPLICIT persona feedback (and extracts the trait + direction) vs ambient
 * tone. This is the gate that keeps silent drift from ever mutating the
 * persona: only explicit directives pass.
 */
class PersonaFeedbackGate {

    private data class Rule(val pattern: Regex, val trait: String, val value: String)

    private val rules = listOf(
        Rule(Regex("""\bbe more direct\b""", RegexOption.IGNORE_CASE), "directness", "high"),
        Rule(Regex("""\bbe direct\b""", RegexOption.IGNORE_CASE), "directness", "high"),
        Rule(Regex("""\bmore direct\b""", RegexOption.IGNORE_CASE), "directness", "high"),
        Rule(Regex("""\bless direct\b""", RegexOption.IGNORE_CASE), "directness", "low"),
        Rule(Regex("""\bbe less direct\b""", RegexOption.IGNORE_CASE), "directness", "low"),
        Rule(Regex("""\bmore concise\b""", RegexOption.IGNORE_CASE), "verbosity", "low"),
        Rule(Regex("""\bbe concise\b""", RegexOption.IGNORE_CASE), "verbosity", "low"),
        Rule(Regex("""\bbe brief\b""", RegexOption.IGNORE_CASE), "verbosity", "low"),
        Rule(Regex("""\btalk less\b""", RegexOption.IGNORE_CASE), "verbosity", "low"),
        Rule(Regex("""\bgo into more detail\b""", RegexOption.IGNORE_CASE), "verbosity", "high"),
        Rule(Regex("""\bmore detail\b""", RegexOption.IGNORE_CASE), "verbosity", "high"),
        Rule(Regex("""\bmore formal\b""", RegexOption.IGNORE_CASE), "formality", "high"),
        Rule(Regex("""\bbe formal\b""", RegexOption.IGNORE_CASE), "formality", "high"),
        Rule(Regex("""\bmore casual\b""", RegexOption.IGNORE_CASE), "formality", "low"),
        Rule(Regex("""\bmore friendly\b""", RegexOption.IGNORE_CASE), "friendliness", "high"),
        Rule(Regex("""\bbe more like that\b""", RegexOption.IGNORE_CASE), "stylePreference", "moreLikeThat"),
        Rule(Regex("""\bmore like that\b""", RegexOption.IGNORE_CASE), "stylePreference", "moreLikeThat")
    )

    // stop being so <adj> -> reduce the trait named <adj>
    private val stopPattern = Regex("""\bstop being so ([a-z]+)\b""", RegexOption.IGNORE_CASE)

    /** Extract an explicit persona feedback (trait, value) or null for non-feedback. */
    fun extractFeedback(text: String): Feedback? {
        val t = text.trim()
        if (t.length < 4) return null
        for (r in rules) {
            if (r.pattern.containsMatchIn(t)) {
                return Feedback(r.trait, r.value)
            }
        }
        val m = stopPattern.find(t)
        if (m != null) {
            return Feedback(m.groupValues[1], "low")
        }
        return null
    }

    data class Feedback(val trait: String, val value: String)
}
