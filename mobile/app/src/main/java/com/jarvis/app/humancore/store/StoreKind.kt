package com.jarvis.app.humancore.store

/**
 * The five logical stores owned by the Human Core (§0.6).
 *
 * Each store is independently persistable and independently recoverable
 * (§0.17, §0.11) — losing one must never cascade into another. The store is
 * the *what*; the storage engine behind it (file-backed today, Turso/libSQL
 * once migration completes per §21.3) is an implementation detail the Human
 * Core defines rules for but never owns (§0.4).
 *
 * Durability tiers (§21.1):
 *  - IDENTITY   -> Critical   (active alerting on loss; no silent fallback)
 *  - PERSONALITY-> Moderate   (recoverable via re-accumulation)
 *  - MOOD       -> Low        (ephemeral by design; loss is acceptable)
 *  - RELATIONSHIP-> High      (significant user-visible regression on loss)
 *  - ADAPTATION -> High       for explicit prefs, Moderate for inferred (§21.1)
 *  - DIALOGUE   -> Moderate   (prunable internal log)
 */
enum class StoreKind(val fileName: String) {
    IDENTITY("identity.json"),
    PERSONALITY("personality.json"),
    MOOD("mood.json"),
    RELATIONSHIP("relationship.json"),
    ADAPTATION("adaptation.json"),
    DIALOGUE("dialogue.jsonl"),
    /** Nervous-system failure history (JSONL, failure surface → disk). */
    FAILURES("failures.jsonl"),
    /** Cognitive continuity: consolidated memories with provenance and
     *  lifecycle state (survives process restart). */
    CONSOLIDATED_MEMORY("consolidated_memory.json"),

    /** 01H SelfModel snapshot (single state document). */
    SELF_MODEL("self_model.json"),

    /** 01H UserModel snapshot (single state document; sensitive data redacted). */
    USER_MODEL("user_model.json"),

    /** 01H WorldModel snapshot (single state document). */
    WORLD_MODEL("world_model.json")
}
