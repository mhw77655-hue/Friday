package com.jarvis.app.social

/**
 * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the per-person
 * relationship state held between JARVIS (the user node, "Venon") and one
 * person.
 *
 * It carries the trust tier (who Venon effectively is to JARVIS), the per-person
 * emotional baseline (NOT a global mood — this baseline belongs to this
 * relationship), and the trajectory stage of the relationship. All three are
 * persisted as ordinary facts on the user's own galaxy node
 * (`relationship:<person>:trust`, `relationship:<person>:trajectory`,
 * `relationship:<person>:emotionalBaseline`) — supersession-aware, no new
 * store.
 */
data class RelationshipState(
    val person: String,
    val trustTier: TrustTier,
    val emotionalBaseline: String? = null,
    val trajectoryStage: RelationshipStage = RelationshipStage.NEW
)

/**
 * The trust tier for one person. STRANGER is the default for anyone not yet
 * modeled; honest trust accumulates through what Venon reveals.
 */
enum class TrustTier {
    STRANGER,
    ACQUAINTANCE,
    KNOWN,
    TRUSTED;

    companion object {
        /** Safe persisted-name parse; unknown or missing names stay STRANGER. */
        fun fromName(name: String?): TrustTier =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: STRANGER

        fun fromOrdinal(ordinal: Int): TrustTier =
            entries.getOrElse(ordinal) { STRANGER }
    }
}

/** Where the relationship currently sits on its trajectory. */
enum class RelationshipStage {
    NEW,
    DEVELOPING,
    ESTABLISHED,
    STRAINED;

    companion object {
        /** Safe persisted-name parse; unknown or missing names stay NEW. */
        fun fromName(name: String?): RelationshipStage =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NEW
    }
}