package com.jarvis.app.social

/**
 * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the real per-person
 * Person Model shape.
 *
 * A [PersonProfile] is the durable, per-person view of who someone is: identity
 * (a free-form identity line), plus behavior patterns, preferences, and history
 * — each entry keeping its own [StatementConfidence] tier ([TieredValue]). The
 * EXPLICIT and INFERRED tiers travel as SEPARATE entries on the same profile
 * and are NEVER merged; re-stating a fact at a different tier does not
 * overwrite the other tier's record.
 *
 * Profiles are persisted as ordinary facts on the SAME galaxy MemoryGraphStore
 * through the WorldModelService seam — no second store. The tiers are encoded
 * in the fact predicate (e.g. `profile:behavior:communication:explicit`), so
 * the store's existing supersession (same subject+predicate supersedes, other
 * predicates coexist) gives exactly the two-tier semantics for free.
 */
data class PersonProfile(
    val name: String,
    val identity: String? = null,
    val behaviorPatterns: List<TieredValue> = emptyList(),
    val preferences: List<TieredValue> = emptyList(),
    val history: List<TieredValue> = emptyList()
) {
    fun isEmpty(): Boolean =
        identity == null &&
            behaviorPatterns.isEmpty() &&
            preferences.isEmpty() &&
            history.isEmpty()
}

/** Which ledger of the profile a statement belongs to. */
enum class ProfileCategory(val tag: String) {
    BEHAVIOR("behavior"),
    PREFERENCE("preference"),
    HISTORY("history")
}

/**
 * The confidence tier of ONE durable statement about a person.
 *
 * EXPLICIT — Venon stated it directly (e.g. "Sara hates mornings").
 * INFERRED — JARVIS reasoned it from what Venon said, never stated verbatim.
 *
 * The two tiers are tracked separately on the same profile and never merged
 * (AC4).
 */
enum class StatementConfidence {
    EXPLICIT,
    INFERRED;

    companion object {
        /** Safe predicated-suffix parse; unknown suffixes stay null. */
        fun fromTag(tag: String): StatementConfidence? =
            entries.firstOrNull { it.name.equals(tag, ignoreCase = true) }
    }
}

/** A single profile entry carrying its own confidence tier. */
data class TieredValue(
    val value: String,
    val tier: StatementConfidence
)