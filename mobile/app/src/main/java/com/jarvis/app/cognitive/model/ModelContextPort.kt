package com.jarvis.app.cognitive.model

/**
 * ModelContextPort - Minimal seam through which the cognitive substrate
 * requests a bounded, relevance-selected projection of the Self/User/World
 * models for the current task.
 *
 * Deliberately parallel to the 01G [com.jarvis.app.cognitive.memory.ContinuityPort]:
 * cognition asks for "what is relevant now", never the full models. The port
 * keeps the [CognitiveContextBuilder] decoupled from the model internals.
 *
 * Implementations: [ModelContextProvider] (wraps [ModelQuery]).
 */
interface ModelContextPort {
    /**
     * Return a bounded, relevance-selected snapshot of the three models for
     * the given situation. Must return promptly and never block on heavy work.
     */
    suspend fun requestModelContext(
        query: String,
        currentGoal: String? = null,
        activeEntities: List<String> = emptyList()
    ): ModelContextSnapshot
}

/**
 * ModelContextSnapshot - Bounded projection of the models.
 *
 * Only the facts/entities relevant to the query are included (relevance
 * selection lives behind the port). Sensitive facts are excluded by default.
 */
data class ModelContextSnapshot(
    val selfFacts: List<ModelFact> = emptyList(),
    val userFacts: List<ModelFact> = emptyList(),
    val worldEntities: List<WorldEntity> = emptyList(),
    val worldRelationships: List<WorldRelationship> = emptyList(),
    val contradictions: List<ModelFact> = emptyList(),
    val selfSummary: String? = null,
    val userSummary: String? = null,
    val worldSummary: String? = null
) {
    val isEmpty: Boolean
        get() = selfFacts.isEmpty() && userFacts.isEmpty() && worldEntities.isEmpty() &&
            contradictions.isEmpty() && selfSummary == null && userSummary == null && worldSummary == null
}

/**
 * Default [ModelContextPort] backed by [ModelQuery]. Applies bounded,
 * relevance-selected output and excludes sensitive facts unless requested.
 */
class ModelContextProvider(
    private val query: ModelQuery,
    private val config: Config = Config()
) : ModelContextPort {

    data class Config(
        val maxSelfFacts: Int = 6,
        val maxUserFacts: Int = 6,
        val maxWorldEntities: Int = 5,
        val maxWorldRelationships: Int = 5,
        val maxContradictions: Int = 3,
        val includeSensitive: Boolean = false,
        val includeSummaries: Boolean = true
    )

    override suspend fun requestModelContext(
        queryText: String,
        currentGoal: String?,
        activeEntities: List<String>
    ): ModelContextSnapshot {
        val combined = (listOf(queryText, currentGoal ?: "") + activeEntities).joinToString(" ")
        return ModelContextSnapshot(
            selfFacts = query.getRelevantSelfContext(combined, config.maxSelfFacts, config.includeSensitive),
            userFacts = query.getRelevantUserContext(combined, config.maxUserFacts, config.includeSensitive),
            worldEntities = query.getRelevantWorldContext(combined, config.maxWorldEntities).entities,
            worldRelationships = query.getRelevantWorldContext(combined, config.maxWorldRelationships).relationships,
            contradictions = query.getContradictions(config.maxContradictions),
            selfSummary = if (config.includeSummaries) query.getCurrentSelfModel().summary() else null,
            userSummary = if (config.includeSummaries) query.getCurrentUserModel().summary() else null,
            worldSummary = if (config.includeSummaries) query.getCurrentWorldModel().summary() else null
        )
    }
}
