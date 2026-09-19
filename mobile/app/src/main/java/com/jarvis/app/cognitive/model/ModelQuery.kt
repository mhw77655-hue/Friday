package com.jarvis.app.cognitive.model

/**
 * ModelQuery - Structured, bounded query API over the three models.
 *
 * Queries never expose raw internal storage: they return projections with
 * relevance applied and output bounded. Intended consumers are the cognitive
 * substrate (context building, reference resolution foundation) and future
 * diagnostic surfaces.
 */
class ModelQuery(private val store: ModelStore) {

    // ---- direct fact/entity lookups -------------------------------------------------

    fun getSelfFact(factKey: String): ModelFact? = store.self.fact(factKey)

    fun getUserFact(factKey: String): ModelFact? = store.user.fact(factKey)

    fun getWorldEntity(entityId: String): WorldEntity? = store.world.entity(entityId)

    fun getWorldRelationship(relationshipId: String): WorldRelationship? =
        store.world.relationship(relationshipId)

    fun getCurrentUserModel(): UserModel = store.user

    fun getCurrentSelfModel(): SelfModel = store.self

    fun getCurrentWorldModel(): WorldModel = store.world

    // ---- relevance-based, bounded context projections -------------------------------

    /**
     * Bounded set of self facts relevant to the query/intent.
     */
    fun getRelevantSelfContext(
        query: String,
        limit: Int = 8,
        includeSensitive: Boolean = false
    ): List<ModelFact> {
        val tokens = queryTokens(query)
        return store.self.currentFacts()
            .filter { includeSensitive || it.sensitivity == Sensitivity.NONE || it.sensitivity == Sensitivity.LOW }
            .map { it to relevanceScore(it, tokens) }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Bounded set of user facts relevant to the query/intent.
     */
    fun getRelevantUserContext(
        query: String,
        limit: Int = 8,
        includeSensitive: Boolean = false
    ): List<ModelFact> {
        val tokens = queryTokens(query)
        return store.user.currentFacts()
            .filter { includeSensitive || it.sensitivity == Sensitivity.NONE || it.sensitivity == Sensitivity.LOW }
            .map { it to relevanceScore(it, tokens) }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Bounded world context: entities + relationships relevant to the query.
     */
    fun getRelevantWorldContext(
        query: String,
        entityLimit: Int = 6,
        relationshipLimit: Int = 6
    ): RelevantWorldContext {
        val tokens = queryTokens(query)
        val entities = store.world.entities.values
            .filter { it.isCurrent() }
            .map { it to relevanceScoreForEntity(it, tokens) }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(entityLimit)
            .map { it.first }
        val entityIds = entities.map { it.entityId }.toSet()
        val rels = store.world.relationships.values
            .filter { it.isCurrent() && (it.sourceEntityId in entityIds || it.targetEntityId in entityIds) }
            .take(relationshipLimit)
        return RelevantWorldContext(entities, rels)
    }

    /**
     * All unresolved contradictions in the current store (candidate truth +
     * conflicting alternatives), bounded.
     */
    fun getContradictions(limit: Int = 10): List<ModelFact> =
        allCurrentAndConflicted(limit)

    private fun allCurrentAndConflicted(limit: Int): List<ModelFact> {
        val out = mutableListOf<ModelFact>()
        for (domain in ModelDomain.entries) {
            for (fact in store.allFacts(domain)) {
                if (fact.status == ModelFactStatus.CONTRADICTED) out.add(fact)
            }
        }
        return out.take(limit)
    }

    // ---- relevance internals ---------------------------------------------------------

    private fun queryTokens(query: String): Set<String> =
        query.lowercase().split(Regex("\\W+")).filter { it.length > 1 }.toSet()

    private fun relevanceScore(fact: ModelFact, tokens: Set<String>): Float {
        if (tokens.isEmpty()) return 0f
        val haystack = (fact.factKey + " " + fact.value).lowercase()
        var hits = 0
        for (t in tokens) if (haystack.contains(t)) hits++
        return hits.toFloat() / tokens.size.toFloat()
    }

    private fun relevanceScoreForEntity(entity: WorldEntity, tokens: Set<String>): Float {
        if (tokens.isEmpty()) return 0f
        val haystack = (entity.entityId + " " + entity.canonicalName + " " +
            entity.aliases.joinToString(" ") + " " + entity.description).lowercase()
        var hits = 0
        for (t in tokens) if (haystack.contains(t)) hits++
        return hits.toFloat() / tokens.size.toFloat()
    }
}

/** Bounded world-context projection. */
data class RelevantWorldContext(
    val entities: List<WorldEntity> = emptyList(),
    val relationships: List<WorldRelationship> = emptyList()
)
