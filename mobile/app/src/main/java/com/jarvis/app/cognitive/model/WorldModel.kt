package com.jarvis.app.cognitive.model

/**
 * WorldModel - Bounded, persistent representation of the world Jarvis knows.
 *
 * NOT a simulation of reality: a knowledge representation of entities relevant
 * to Jarvis, with explicit confidence, provenance, temporal validity, and
 * relationships. Uses the existing temporal/provenance patterns from 01G —
 * history is never overwritten silently.
 *
 * Entity types are extensible (a [WorldEntityType] is just an id string with
 * well-known constants) so future entity classes remain possible.
 *
 * Immutable snapshot produced by [ModelUpdateEngine].
 */
data class WorldModel(
    /** Known entities, by entity id. */
    val entities: Map<String, WorldEntity> = emptyMap(),

    /** Known relationships, by relationship id. */
    val relationships: Map<String, WorldRelationship> = emptyMap(),

    /** Generic non-entity world facts (e.g. connectivity state), by fact key. */
    val facts: Map<String, ModelFact> = emptyMap(),

    /** Non-current world facts preserved in deterministic order (history kept). */
    val factHistory: List<ModelFact> = emptyList(),

    /** Entity ids that were observed but are no longer current (temporal). */
    val historicalEntityIds: List<String> = emptyList(),

    val updatedAt: Long = System.currentTimeMillis()
) {
    fun entity(entityId: String): WorldEntity? = entities[entityId]

    fun relationship(relationshipId: String): WorldRelationship? = relationships[relationshipId]

    fun fact(factKey: String): ModelFact? = facts[factKey]

    /** All relationships involving the given entity (as source or target). */
    fun relationshipsOf(entityId: String): List<WorldRelationship> =
        relationships.values.filter { it.sourceEntityId == entityId || it.targetEntityId == entityId }

    fun summary(): String =
        "WorldModel[entities=${entities.size} relationships=${relationships.size} " +
            "facts=${facts.size} historical=${historicalEntityIds.size}]"
}

/**
 * WorldEntityType - Extensible entity class.
 *
 * The well-known constants cover today's domains; new entity classes are
 * introduced by using a new id string (or extending this enum) — nothing in
 * the model hardcodes the closed list.
 */
enum class WorldEntityType(val id: String) {
    PERSON("person"),
    DEVICE("device"),
    APPLICATION("application"),
    FILE("file"),
    FOLDER("folder"),
    PROJECT("project"),
    TASK("task"),
    CAPABILITY("capability"),
    ENVIRONMENT("environment"),
    MODEL("model"),
    ORGANIZATION("organization"),
    LOCATION("location"),
    CONCEPT("concept"),
    ARTIFACT("artifact"),
    EXTERNAL_SERVICE("external-service"),
    UNKNOWN("unknown");

    companion object {
        fun fromId(id: String): WorldEntityType =
            entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

/** Typed attribute value — avoids arbitrary untyped maps where structure helps. */
sealed class AttributeValue {
    data class Text(val value: String) : AttributeValue()
    data class Number(val value: Double) : AttributeValue()
    /** Named Bool (not Boolean) so it never shadows the Kotlin primitive. */
    data class Bool(val value: Boolean) : AttributeValue()
    data class Strings(val values: List<String>) : AttributeValue()

    /** Canonical serialized form. */
    fun asText(): String = when (this) {
        is Text -> value
        is Number -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is Bool -> value.toString()
        is Strings -> values.joinToString(",")
    }
}

/** Temporal state of a world entity. */
enum class WorldEntityState {
    /** Currently believed to hold. */
    CURRENT,

    /** Superseded by newer observation — historical truth preserved. */
    PREVIOUS,

    /** Anticipated/expected state (e.g. planned change). */
    EXPECTED,

    /** No longer believed current and not replaced — stale. */
    STALE
}

/**
 * WorldEntity - A bounded knowledge unit about one entity.
 *
 * Supports structured attributes, explicit state, source, confidence,
 * timestamps, validity and relationships. History is preserved by creating
 * new entity instances (old ones move to PREVIOUS/STALE); nothing is deleted
 * silently.
 */
data class WorldEntity(
    val entityId: String,
    val entityType: WorldEntityType,
    val canonicalName: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    /** Structured attributes (typed values). */
    val attributes: Map<String, AttributeValue> = emptyMap(),
    /** Current temporal state. */
    val state: WorldEntityState = WorldEntityState.CURRENT,
    val source: ModelEvidenceSource = ModelEvidenceSource.ENVIRONMENT_OBSERVATION,
    val sourceId: String = "",
    val confidence: ModelConfidence = ModelConfidence(),
    val observedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Validity window (null = indefinite). */
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    /** Expected change rate — informs staleness. */
    val expectedChangeRate: ExpectedChangeRate = ExpectedChangeRate.STABLE,
    /** Sensitivity classification (e.g. personal data). */
    val sensitivity: Sensitivity = Sensitivity.NONE
) {
    fun isCurrent(): Boolean = state == WorldEntityState.CURRENT && confidence.isCredible()

    fun summary(): String =
        "WorldEntity[$entityId:${entityType.id}] '$canonicalName' state=${state.name} " +
            "conf=${"%.2f".format(confidence.score)}"
}

/**
 * WorldRelationshipType - Relationship kinds between world entities.
 */
enum class WorldRelationshipType(val id: String) {
    OWNS("owns"),
    USES("uses"),
    CONTAINS("contains"),
    DEPENDS_ON("depends-on"),
    PART_OF("part-of"),
    LOCATED_IN("located-in"),
    RELATED_TO("related-to"),
    CREATED_BY("created-by"),
    CONTROLLED_BY("controlled-by"),
    RUNS_ON("runs-on"),
    AVAILABLE_IN("available-in"),
    REQUIRES("requires"),
    PRODUCES("produces"),
    DERIVED_FROM("derived-from");

    companion object {
        fun fromId(id: String): WorldRelationshipType =
            entries.firstOrNull { it.id == id } ?: RELATED_TO
    }
}

/**
 * WorldRelationship - A relationship between two world entities.
 *
 * Carries confidence, provenance and temporal validity. Reuses the existing
 * memory/continuity relationship *semantics* (01G TemporalRelationshipType
 * patterns) without duplicating a graph engine.
 */
data class WorldRelationship(
    val relationshipId: String,
    val sourceEntityId: String,
    val targetEntityId: String,
    val type: WorldRelationshipType,
    val confidence: ModelConfidence = ModelConfidence(),
    /** Provenance description — who/what established this. */
    val provenance: String = "",
    val evidence: List<ModelEvidence> = emptyList(),
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val observedAt: Long = System.currentTimeMillis(),
    /** Set when this relationship superseded an earlier one (history kept). */
    val supersedesRelationshipId: String? = null
) {
    fun isCurrent(): Boolean = validUntil == null || validUntil > System.currentTimeMillis()

    fun summary(): String =
        "WorldRelationship[$sourceEntityId ${type.id} $targetEntityId] " +
            "conf=${"%.2f".format(confidence.score)}"
}
