package com.jarvis.app.cognitive.model

/**
 * ModelStore - Mutable container holding the three current model snapshots.
 *
 * Mirrors the 01G pattern: immutable data classes ([SelfModel], [UserModel],
 * [WorldModel]) held by a mutable store that the [ModelUpdateEngine] mutates
 * and [ModelQuery] reads. Ordering is insertion-stable (LinkedHashMap) so the
 * store is deterministic.
 */
class ModelStore(
    initialSelf: SelfModel = SelfModel(),
    initialUser: UserModel = UserModel(),
    initialWorld: WorldModel = WorldModel()
) {
    @Volatile
    var self: SelfModel = initialSelf

    @Volatile
    var user: UserModel = initialUser

    @Volatile
    var world: WorldModel = initialWorld

    // ---- fact access --------------------------------------------------------------

    fun getFact(domain: ModelDomain, factKey: String): ModelFact? = when (domain) {
        ModelDomain.SELF -> self.fact(factKey)
        ModelDomain.USER -> user.fact(factKey)
        ModelDomain.WORLD -> world.fact(factKey)
    }

    fun setFact(fact: ModelFact): ModelFact {
        when (fact.domain) {
            ModelDomain.SELF -> self = self.copy(facts = ordered(self.facts, fact))
            ModelDomain.USER -> user = user.copy(facts = ordered(user.facts, fact))
            ModelDomain.WORLD -> world = world.copy(facts = ordered(world.facts, fact))
        }
        return fact
    }

    /**
     * Preserve a non-current fact (superseded / contradicted / outdated) in
     * deterministic order so history is never silently overwritten.
     */
    fun pushHistory(fact: ModelFact) {
        when (fact.domain) {
            ModelDomain.SELF -> self = self.copy(factHistory = self.factHistory + fact)
            ModelDomain.USER -> user = user.copy(factHistory = user.factHistory + fact)
            ModelDomain.WORLD -> world = world.copy(factHistory = world.factHistory + fact)
        }
    }

    fun history(domain: ModelDomain): List<ModelFact> = when (domain) {
        ModelDomain.SELF -> self.factHistory
        ModelDomain.USER -> user.factHistory
        ModelDomain.WORLD -> world.factHistory
    }

    fun allCurrentFacts(): List<ModelFact> =
        self.currentFacts() + user.currentFacts() + world.facts.values.filter { it.isCurrent() }

    fun allFacts(domain: ModelDomain): List<ModelFact> = when (domain) {
        ModelDomain.SELF -> self.facts.values.toList() + self.factHistory
        ModelDomain.USER -> user.facts.values.toList() + user.factHistory
        ModelDomain.WORLD -> world.facts.values.toList() + world.factHistory
    }

    // ---- world entity/relationship access ----------------------------------------

    fun setEntity(entity: WorldEntity, historical: Boolean = false) {
        val entries = LinkedHashMap<String, WorldEntity>(world.entities)
        entries[entity.entityId] = entity
        val historicalIds = if (historical) {
            LinkedHashSet(world.historicalEntityIds).apply { add(entity.entityId) }.toList()
        } else world.historicalEntityIds
        world = world.copy(
            entities = entries,
            historicalEntityIds = historicalIds,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun setRelationship(rel: WorldRelationship) {
        val entries = LinkedHashMap<String, WorldRelationship>(world.relationships)
        entries[rel.relationshipId] = rel
        world = world.copy(relationships = entries, updatedAt = System.currentTimeMillis())
    }

    /** Replace all model state (used by persistence restore). */
    fun restore(self: SelfModel, user: UserModel, world: WorldModel) {
        this.self = self
        this.user = user
        this.world = world
    }

    private fun ordered(current: Map<String, ModelFact>, fact: ModelFact): Map<String, ModelFact> {
        val entries = LinkedHashMap<String, ModelFact>(current)
        entries[fact.factKey] = fact
        return entries
    }
}
