package com.jarvis.app.social

import com.jarvis.app.identity.EntityType
import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.memory.MemoryGraphStore

/**
 * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the real per-person
 * Person/Relationship model, persisted through the REAL Galaxy Memory graph
 * store via the [WorldModelService] seam — no second store, no parallel copy.
 *
 * Persistence encoding (reuses the store's supersession-aware one-node schema):
 *
 *  - type tag: `(person, "type", "PERSON")` via [WorldModelService.registerEntity].
 *  - identity: `(person, "identity", <value>)`.
 *  - profile entries: `(person, "profile:<category>:<descriptor>:<tier>", <value>)`
 *    — the tier lives in the predicate, so the EXPLICIT and INFERRED record of
 *    the SAME trait coexist as distinct facts (never merged, AC4) while
 *    re-stating at the SAME tier supersedes the prior value.
 *  - relationship: `(Venon, "relationship:<person>:trust", <tier>)`,
 *    `(Venon, "relationship:<person>:trajectory", <stage>)` and
 *    `(Venon, "relationship:<person>:emotionalBaseline", <value>)` on the
 *    user's own node.
 *
 * Venon-mediated for now: JARVIS models people strictly through what Venon
 * tells him (the API below IS the write path); there is no autonomous
 * extraction in this story.
 */
class PersonRelationshipModel(
    private val graphStore: MemoryGraphStore,
    private val worldModel: WorldModelService
) {

    fun registerPerson(person: String, source: String = "social-relational") {
        worldModel.registerEntity(person, EntityType.PERSON, source)
    }

    /** True when [person] is tagged as a PERSON entity in the world model. */
    fun isRegisteredPerson(person: String): Boolean =
        worldModel.getEntity(person)?.type == EntityType.PERSON

    /**
     * Persist one statement about [person] at its own confidence tier.
     * Re-stating the same category+descriptor+tier supersedes the prior value;
     * a different tier for the same trait is kept as a SEPARATE fact.
     */
    fun recordStatement(
        person: String,
        category: ProfileCategory,
        descriptor: String,
        value: String,
        tier: StatementConfidence,
        source: String = "social-relational"
    ) {
        graphStore.addFact(
            subject = person,
            predicate = "profile:${category.tag}:$descriptor:${tier.name.lowercase()}",
            `object` = value,
            source = source
        )
    }

    /** Persist/replace [person]'s identity line. */
    fun recordIdentity(person: String, identity: String, source: String = "social-relational") {
        graphStore.addFact(subject = person, predicate = "identity", `object` = identity, source = source)
    }

    /** Re-read the durable profile of [person] from the real graph store. */
    fun loadProfile(person: String): PersonProfile {
        val facts = worldModel.getFactsAbout(person)
        return PersonProfile(
            name = person,
            identity = facts.firstOrNull { it.predicate == "identity" }?.`object`,
            behaviorPatterns = profileEntries(facts, ProfileCategory.BEHAVIOR),
            preferences = profileEntries(facts, ProfileCategory.PREFERENCE),
            history = profileEntries(facts, ProfileCategory.HISTORY)
        )
    }

    fun setTrust(person: String, tier: TrustTier, source: String = "social-relational") {
        graphStore.addFact(
            subject = WorldModelService.USER_NODE_NAME,
            predicate = "relationship:$person:trust",
            `object` = tier.name,
            source = source
        )
    }

    fun setTrajectory(person: String, stage: RelationshipStage, source: String = "social-relational") {
        graphStore.addFact(
            subject = WorldModelService.USER_NODE_NAME,
            predicate = "relationship:$person:trajectory",
            `object` = stage.name,
            source = source
        )
    }

    fun setEmotionalBaseline(person: String, baseline: String, source: String = "social-relational") {
        graphStore.addFact(
            subject = WorldModelService.USER_NODE_NAME,
            predicate = "relationship:$person:emotionalBaseline",
            `object` = baseline,
            source = source
        )
    }

    /** Re-read the durable relationship state from the real graph store. */
    fun relationshipState(person: String): RelationshipState {
        val facts = worldModel.getFactsAbout(WorldModelService.USER_NODE_NAME)
        return RelationshipState(
            person = person,
            trustTier = facts.firstOrNull { it.predicate == "relationship:$person:trust" }
                ?.`object`?.let { TrustTier.fromName(it) } ?: TrustTier.STRANGER,
            emotionalBaseline = facts.firstOrNull { it.predicate == "relationship:$person:emotionalBaseline" }?.`object`,
            trajectoryStage = facts.firstOrNull { it.predicate == "relationship:$person:trajectory" }
                ?.`object`?.let { RelationshipStage.fromName(it) } ?: RelationshipStage.NEW
        )
    }

    /**
     * The prompt surface lines for [person]: the profile line and the
     * relationship line, both derived on-the-fly from the real persisted
     * facts — never from a cached copy. Empty for entities that are not
     * registered as people (so places/topics don't get a phantom relationship).
     */
    fun personSurface(person: String): List<String> {
        if (!isRegisteredPerson(person)) return emptyList()
        val profile = loadProfile(person)
        val state = relationshipState(person)
        val lines = mutableListOf<String>()
        lines += "- person $person: identity=${profile.identity ?: "(none)"}, " +
            "behaviors=[${summary(profile.behaviorPatterns)}], " +
            "preferences=[${summary(profile.preferences)}], " +
            "history=[${summary(profile.history)}]"
        lines += "- relationship: $person trust=${state.trustTier.name} " +
            "trajectory=${state.trajectoryStage.name} " +
            "baseline=${state.emotionalBaseline ?: "(none)"}"
        return lines
    }

    private fun summary(entries: List<TieredValue>): String {
        if (entries.isEmpty()) return "(none)"
        return entries.joinToString(", ") { "${it.value}(${it.tier.name.lowercase()})" }
    }

    private fun profileEntries(facts: List<com.jarvis.app.memory.MemoryNode>, category: ProfileCategory): List<TieredValue> =
        facts
            .filter { it.predicate.startsWith("profile:${category.tag}:") }
            .mapNotNull { node ->
                val tier = StatementConfidence.fromTag(node.predicate.substringAfterLast(':'))
                    ?: return@mapNotNull null
                TieredValue(value = node.`object`, tier = tier)
            }
            .sortedBy { it.tier.name }
}