package com.jarvis.app.identity

import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryNode
import com.jarvis.app.memory.RankedMemory

/**
 * A thin, typed semantic query layer over Stage 02's existing
 * [MemoryGraphStore]. This is deliberately NOT new storage: the World Model is
 * the same bi-temporal, supersession-aware entity/relationship graph that
 * Galaxy Memory already persists. This service only adds typed query methods
 * on top of it - it introduces no second graph, no second store, and does not
 * duplicate [MemoryGraphStore]'s validity-window/supersession logic.
 *
 * **Entity-type tagging (reuses the existing schema, no new table).** Worlds
 * entities (people other than the user, places, topics, devices, concepts) are
 * distinguished from the User Model's own node (the user) by an explicit
 * entity-type tag carried as an ordinary fact triple: `(subject=<entity>,"
 * predicate="type", object=<TYPE>)`. The User Model's own node is tagged
 * [EntityType.USER] (subject = "Venon"); every other entity carries a
 * non-USER tag. This reuses `MemoryGraphStore`'s one nodes table - no parallel
 * table is created.
 *
 * **Traversal reuses the existing weighted semantics.** [getRelationships]
 * performs real multi-hop weighted traversal using the SAME edge-weight
 * function as [BlendedMemoryRetriever] (relationship-derived weights), not a
 * new unweighted BFS reimplementation - so relationship strength (same
 * subject+predicate > same subject > same predicate-only) is honored exactly
 * as the rest of Galaxy Memory does.
 */
class WorldModelService(
    private val graphStore: MemoryGraphStore,
    /** Optional; supplies the existing retrieval mechanism for [getRecentlyRelevantEntities]. */
    private val blendedRetriever: BlendedMemoryRetriever? = null
) {

    /** The reserved subject under which the User Model's own node lives. */
    companion object {
        const val USER_NODE_NAME = "Venon"
        const val TYPE_PREDICATE = "type"
    }

    /**
     * Register an entity, stamping its type tag as a `(subject, "type", type)`
     * fact on the existing graph. Calling again with a new type supersedes the
     * old tag via the store's existing supersession mechanism - never an
     * overwrite.
     */
    fun registerEntity(name: String, type: EntityType, source: String = "world-model") {
        graphStore.addFact(subject = name, predicate = TYPE_PREDICATE, `object` = type.name, source = source)
    }

    /**
     * Resolve a single entity by name, returning its type (from the
     * `(name,"type",T)` tag) and the currently-valid facts about it.
     * Returns null when the entity has no facts at [asOfTime].
     */
    fun getEntity(name: String, asOfTime: Long = System.currentTimeMillis()): WorldEntity? {
        val facts = getFactsAbout(name, asOfTime)
        if (facts.isEmpty()) return null
        val typeTag = facts.firstOrNull { it.predicate == TYPE_PREDICATE }?.`object`
        return WorldEntity(
            name = name,
            type = typeTag?.let { EntityType.fromTag(it) } ?: EntityType.UNKNOWN,
            facts = facts
        )
    }

    /**
     * Persist a durable preference/constraint onto the User Model's own graph
     * node ([USER_NODE_NAME]) as an ordinary fact triple with predicate
     * `preference:<key>`. Calling again for the same key supersedes the prior
     * value via the store's existing bi-temporal supersession — never an
     * overwrite, so history is preserved.
     */
    fun setUserPreference(key: String, value: String, source: String = "profile") {
        graphStore.addFact(
            subject = USER_NODE_NAME,
            predicate = "preference:$key",
            `object` = value,
            source = source
        )
    }

    /** The currently-valid value of durable preference [key], or null. */
    fun getUserPreference(key: String, asOfTime: Long = System.currentTimeMillis()): String? {
        val facts = getFactsAbout(USER_NODE_NAME, asOfTime)
        return facts.firstOrNull { it.predicate == "preference:$key" }?.`object`
    }

    /**
     * Persist a durable PERSONA trait adjustment for the user's own node as a
     * fact `(Venon, "persona:<trait>", value)` with the triggering feedback
     * carried in [source] for traceability. A later adjustment for the same
     * trait supersedes the prior one via the store's supersession mechanism.
     */
    fun persistPersonaAdjustment(trait: String, value: String, triggeredBy: String) {
        graphStore.addFact(
            subject = USER_NODE_NAME,
            predicate = "persona:$trait",
            `object` = value,
            source = "persona: $triggeredBy"
        )
    }

    /** All currently-valid facts on the User Model's own node. */
    fun userNodeFacts(asOfTime: Long = System.currentTimeMillis()): List<MemoryNode> =
        getFactsAbout(USER_NODE_NAME, asOfTime)

    /**
     * All currently-valid facts about [entityId] at [asOfTime] - the same
     * supersession-aware, bi-temporal result [MemoryGraphStore.query] returns;
     * no divergent re-derivation.
     */
    fun getFactsAbout(entityId: String, asOfTime: Long = System.currentTimeMillis()): List<MemoryNode> =
        graphStore.query(subject = entityId, asOfTime = asOfTime)

    /**
     * Multi-hop weighted traversal from [entityId]'s facts, reusing the store's
     * existing weighted edge semantics. Returns the reachable nodes (excluding
     * the origin) after up to [maxHops] weighted spreading-activation hops, in
     * descending activation order. This is weighted traversal - nodes connected
     * only by a shared predicate still receive lower activation than same-subject
     * links, exactly matching Galaxy Memory's relationship-strength weighting.
     */
    fun getRelationships(
        entityId: String,
        maxHops: Int = 2,
        asOfTime: Long = System.currentTimeMillis()
    ): List<WorldRelationship> {
        val allNodes = graphStore.query(asOfTime = asOfTime)
        if (allNodes.isEmpty()) return emptyList()

        val origin = allNodes.filter { it.subject == entityId }
        if (origin.isEmpty()) return emptyList()

        val activation = mutableMapOf<String, Float>()
        origin.forEach { activation[it.id] = 1f }

        repeat(maxHops.coerceAtLeast(0)) {
            val snapshot = activation.toMap()
            for (u in allNodes) {
                val baseAct = snapshot[u.id] ?: 0f
                if (baseAct <= 0f) continue
                for (v in allNodes) {
                    if (v.id == u.id) continue
                    val w = BlendedMemoryRetriever.blendedEdgeWeight(u, v)
                    if (w <= 0f) continue
                    activation[v.id] = (activation[v.id] ?: 0f) + baseAct * w
                }
            }
        }

        // Cap activation so pure chain length doesn't grow without bound.
        activation.replaceAll { _, a -> a.coerceAtMost(5f) }
        val originIds = origin.mapTo(mutableSetOf()) { it.id }
        return activation.entries
            .filter { it.key !in originIds }
            .mapNotNull { (id, a) -> allNodes.find { it.id == id } }
            .sortedByDescending { activation[it.id] }
            .map { node -> WorldRelationship(entityId, node, activation[node.id] ?: 0f, maxHops) }
    }

    /**
     * Entities relevant to the current conversation [context], surfaced through
     * the EXISTING [BlendedMemoryRetriever] - no new ranking mechanism. Returns
     * a de-duplicated list of the entities behind the retriever's top results.
     */
    fun getRecentlyRelevantEntities(
        context: String,
        limit: Int = 5
    ): List<WorldEntity> {
        val retriever = blendedRetriever ?: return emptyList()
        return retriever.retrieve(context)
            .take(limit)
            .mapNotNull { result -> getEntity(result.node.subject) }
            .distinctBy { it.name }
    }
}

/** The explicit entity-type tag used to distinguish World vs User Model nodes. */
enum class EntityType {
    USER,
    PERSON,
    PLACE,
    TOPIC,
    DEVICE,
    CONCEPT,
    UNKNOWN;

    companion object {
        fun fromTag(tag: String): EntityType =
            entries.firstOrNull { it.name.equals(tag, ignoreCase = true) } ?: UNKNOWN
    }
}

/** A resolved world-model entity: its type tag plus currently-valid facts. */
data class WorldEntity(
    val name: String,
    val type: EntityType,
    val facts: List<MemoryNode>
)

/** A single traversal hop: [target] node reachable from [origin] with weight [weight]. */
data class WorldRelationship(
    val origin: String,
    val target: MemoryNode,
    val weight: Float,
    val maxHops: Int
)
