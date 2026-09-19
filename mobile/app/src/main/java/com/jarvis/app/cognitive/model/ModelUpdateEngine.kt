package com.jarvis.app.cognitive.model

import com.jarvis.app.cognitive.memory.ResolutionMethod

/**
 * ModelUpdateEngine - The single shared update mechanism for SelfModel,
 * UserModel and WorldModel.
 *
 * Pipeline (RULE 24 — deterministic, no hidden randomness):
 *
 *   observe → normalize → compare → classify → update
 *            → preserve provenance → resolve contradiction → update confidence
 *
 * The current truth is never mutated immediately. Each update is first
 * classified against the existing fact:
 *
 *   CONFIRMS    — same value; strengthen confidence/evidence.
 *   CONTRADICTS — different value, both sides credible → structured conflict
 *                 (existing + new both preserved, marked CONTRADICTED).
 *   SUPERSEDES  — different value, new evidence is strictly newer and
 *                 materially stronger → old fact becomes OUTDATED (history
 *                 kept via `supersededBy`), new fact becomes current.
 *   ADDS        — no existing fact; a new evidence-backed fact is created.
 *   TOO_WEAK    — evidence below the strength floor; no change.
 *   OBSOLETE    — evidence older than the current fact; no change.
 *
 * Contradiction resolution reuses the 01G conflict machinery's decision
 * semantics ([ResolutionMethod]) — no new conflict resolver is created.
 */
class ModelUpdateEngine(
    private val config: Config = Config(),
    private val onFactUpdated: (ModelFact) -> Unit = {},
    private val onConflictDetected: (ModelFactConflict) -> Unit = {},
    private val onFactSuperseded: (old: ModelFact, new: ModelFact) -> Unit = { _, _ -> },
    private val onFactBecameStale: (ModelFact) -> Unit = {}
) {

    data class Config(
        /** Floor for a piece of evidence to be allowed to change anything. */
        val minEvidenceStrength: Float = 0.3f,

        /** Both sides must meet this to be treated as a real conflict. */
        val conflictThreshold: Float = 0.55f,

        /** New fact must exceed the old fact's confidence by this delta to supersede. */
        val supersedeDelta: Float = 0.15f,

        /** A fact is stale when age exceeds its expected-change horizon. */
        val stalenessHorizonMs: Long = 7L * 24 * 60 * 60 * 1000, // 7 days default

        /** Confidence gain for a confirming piece of evidence. */
        val confirmBoost: Float = 0.05f,

        /** Confidence ceiling. */
        val maxConfidence: Float = 0.99f,

        /** Default resolution when a contradiction must be auto-resolved. */
        val defaultResolution: ResolutionMethod = ResolutionMethod.HIGHEST_CONFIDENCE
    ) {
        init {
            require(minEvidenceStrength in 0f..1f)
            require(conflictThreshold in 0f..1f)
            require(supersedeDelta >= 0f)
        }
    }

    /**
     * Observe a single fact update against the current store.
     *
     * Deterministic: given the same store state, update payload and policy the
     * outcome (including the produced fact ids' *values*) is the same — the
     * randomized fact id suffix never influences the structural result.
     */
    fun observe(store: ModelStore, update: ModelUpdate): UpdateOutcome {
        val existing = store.getFact(update.domain, update.factKey)

        // 0. Normalize: evidence strength and source reliability clamped.
        val evidenceStrength = update.evidenceStrength.coerceIn(0f, 1f)
        val sourceReliability = update.sourceReliability.coerceIn(0f, 1f)
        val newScore = baseScore(update).coerceIn(0f, 1f)

        // 1. Too weak to change anything?
        if (evidenceStrength < config.minEvidenceStrength) {
            return UpdateOutcome.noChange(update, UpdateClassification.TOO_WEAK)
        }

        // 2. No existing fact → add.
        if (existing == null) {
            val fact = newFact(update, newScore, sourceReliability, evidenceStrength)
            store.setFact(fact)
            onFactUpdated(fact)
            return UpdateOutcome.changed(fact, listOf(fact), UpdateClassification.ADDS)
        }

        // 3. Existing fact present. Obsolete (older than current truth)?
        if (update.observedAt < existing.updatedAt) {
            return UpdateOutcome.noChange(update, UpdateClassification.OBSOLETE, existing)
        }

        // 4. Same value → confirms.
        if (existing.value == update.value) {
            val confirmed = confirm(existing, update, evidenceStrength, sourceReliability)
            store.setFact(confirmed)
            onFactUpdated(confirmed)
            return UpdateOutcome.changed(confirmed, listOf(confirmed), UpdateClassification.CONFIRMS)
        }

        // 5. Different value. Decide between SUPERSEDES / CONTRADICTS / deferred.
        val existingCredible = existing.confidence.score >= config.conflictThreshold
        val newStrongerByDelta = newScore >= existing.confidence.score + config.supersedeDelta

        return if (newStrongerByDelta) {
            supersede(store, existing, update, newScore, sourceReliability, evidenceStrength)
        } else if (existingCredible && newScore >= config.conflictThreshold) {
            contradict(store, existing, update, newScore, sourceReliability, evidenceStrength)
        } else {
            // Different value but not credibly contradictory and not strongly
            // better — current truth is kept; the weaker challenger is
            // recorded in history (marked as a conflicting alternative) so it
            // is never silently lost, and it never displaces current truth.
            val challenger = newFact(update, newScore, sourceReliability, evidenceStrength)
                .copy(conflictingFactIds = listOf(existing.factId))
            store.pushHistory(challenger)
            onFactUpdated(challenger)
            UpdateOutcome.changed(challenger, listOf(challenger), UpdateClassification.CONTRADICTS)
        }
    }

    /**
     * Observe a world entity (or an updated state of one). Old entity instances
     * are moved to PREVIOUS/STALE — history is never overwritten silently.
     */
    fun observeEntity(store: ModelStore, entity: WorldEntity): UpdateOutcome {
        val existing = store.world.entities[entity.entityId]
        if (existing == null) {
            store.setEntity(entity)
            onFactUpdated(store.getFact(ModelDomain.WORLD, entity.entityId) ?: entityFact(entity))
            return UpdateOutcome.changed(entityFact(entity), emptyList(), UpdateClassification.ADDS)
        }
        if (existing.observedAt > entity.observedAt) {
            return UpdateOutcome.noChange(
                ModelUpdate(ModelDomain.WORLD, entity.entityId, entity.canonicalName, entity.source,
                    entity.sourceId, "entity observation", entity.confidence.score),
                UpdateClassification.OBSOLETE
            )
        }
        // Preserve the old instance as historical (PREVIOUS), add the new.
        val supersededOld = existing.copy(state = WorldEntityState.PREVIOUS)
        store.setEntity(supersededOld, historical = true)
        store.setEntity(entity.copy(state = WorldEntityState.CURRENT))
        onFactUpdated(entityFact(entity))
        return UpdateOutcome.changed(entityFact(entity), listOf(entityFact(entity)), UpdateClassification.SUPERSEDES)
    }

    /**
     * Observe a world relationship. A newer relationship supersedes the older
     * one (kept with [WorldRelationship.supersedesRelationshipId]).
     */
    fun observeRelationship(store: ModelStore, rel: WorldRelationship): UpdateOutcome {
        val existing = store.world.relationships[rel.relationshipId]
        if (existing == null) {
            store.setRelationship(rel)
            return UpdateOutcome.changed(relationshipFact(rel), emptyList(), UpdateClassification.ADDS)
        }
        val updated = rel.copy(supersedesRelationshipId = existing.relationshipId)
        store.setRelationship(updated)
        return UpdateOutcome.changed(relationshipFact(updated), emptyList(), UpdateClassification.SUPERSEDES)
    }

    /**
     * Mark facts whose age exceeds their expected-change horizon as stale.
     * Deterministic sweep over the store's current facts.
     */
    fun sweepStale(store: ModelStore, now: Long = System.currentTimeMillis()): Int {
        var staleCount = 0
        for (fact in store.allCurrentFacts()) {
            val stale = isStale(fact, now)
            if (stale) {
                val marked = fact.copy(
                    status = if (fact.status == ModelFactStatus.CONFIRMED ||
                        fact.status == ModelFactStatus.OBSERVED) ModelFactStatus.OUTDATED else fact.status,
                    staleness = fact.staleness.copy(isStale = true)
                )
                store.setFact(marked)
                onFactBecameStale(marked)
                staleCount++
            }
        }
        return staleCount
    }

    // ---- classification internals -------------------------------------------------

    private fun baseScore(update: ModelUpdate): Float =
        (update.evidenceStrength * 0.6f + update.sourceReliability * 0.4f).coerceIn(0f, 1f)

    private fun statusForSource(source: ModelEvidenceSource): ModelFactStatus = when (source) {
        ModelEvidenceSource.EXPLICIT_USER_STATEMENT -> ModelFactStatus.CONFIRMED
        ModelEvidenceSource.CAPABILITY_REGISTRY,
        ModelEvidenceSource.SYSTEM_CONFIGURATION,
        ModelEvidenceSource.ENVIRONMENT_OBSERVATION,
        ModelEvidenceSource.SENSOR_OBSERVATION,
        ModelEvidenceSource.SYSTEM_DECLARATION -> ModelFactStatus.OBSERVED
        ModelEvidenceSource.EXECUTION_RESULT,
        ModelEvidenceSource.MEMORY,
        ModelEvidenceSource.CONTINUITY_RECONSTRUCTION,
        ModelEvidenceSource.RESEARCH_RESULT -> ModelFactStatus.INFERRED
    }

    private fun newFact(
        update: ModelUpdate,
        score: Float,
        sourceReliability: Float,
        evidenceStrength: Float
    ): ModelFact = ModelFact(
        domain = update.domain,
        factKey = update.factKey,
        value = update.value,
        status = statusForSource(update.source),
        confidence = ModelConfidence(
            score = score,
            sourceReliability = sourceReliability,
            confirmationState = if (update.source == ModelEvidenceSource.EXPLICIT_USER_STATEMENT)
                ConfirmationState.CONFIRMED else ConfirmationState.UNCONFIRMED,
            lastValidation = update.observedAt,
            evidenceCount = 1,
            contradictionCount = 0
        ),
        sensitivity = update.sensitivity,
        evidence = listOf(evidenceFor(update, evidenceStrength)),
        observedAt = update.observedAt,
        updatedAt = update.observedAt,
        staleness = StalenessMetadata(
            lastVerifiedAt = update.observedAt,
            expectedChangeRate = update.expectedChangeRate,
            worldVolatility = update.worldVolatility
        )
    )

    private fun confirm(
        existing: ModelFact,
        update: ModelUpdate,
        evidenceStrength: Float,
        sourceReliability: Float
    ): ModelFact {
        val newScore = (existing.confidence.score + config.confirmBoost * evidenceStrength)
            .coerceIn(0f, config.maxConfidence)
        val state = when {
            existing.confidence.confirmationState == ConfirmationState.CONFIRMED ->
                ConfirmationState.CONFIRMED
            update.source == ModelEvidenceSource.EXPLICIT_USER_STATEMENT ->
                ConfirmationState.CONFIRMED
            existing.confidence.evidenceCount >= 2 -> ConfirmationState.PARTIALLY_CONFIRMED
            else -> ConfirmationState.UNCONFIRMED
        }
        return existing.copy(
            confidence = existing.confidence.copy(
                score = newScore,
                sourceReliability = maxOf(existing.confidence.sourceReliability, sourceReliability),
                confirmationState = state,
                lastValidation = update.observedAt,
                evidenceCount = existing.confidence.evidenceCount + 1,
                contradictionCount = existing.confidence.contradictionCount
            ),
            evidence = (existing.evidence + evidenceFor(update, evidenceStrength)).take(20),
            updatedAt = update.observedAt,
            staleness = existing.staleness.copy(lastVerifiedAt = update.observedAt)
        )
    }

    private fun supersede(
        store: ModelStore,
        existing: ModelFact,
        update: ModelUpdate,
        newScore: Float,
        sourceReliability: Float,
        evidenceStrength: Float
    ): UpdateOutcome {
        val replacement = newFact(update, newScore, sourceReliability, evidenceStrength)
        val obsoleteOld = existing.copy(
            status = ModelFactStatus.OUTDATED,
            supersededBy = replacement.factId,
            updatedAt = update.observedAt,
            staleness = existing.staleness.copy(isStale = true)
        )
        // History is preserved: the old fact moves to history, the new fact
        // becomes the current candidate for this key.
        store.pushHistory(obsoleteOld)
        store.setFact(replacement)
        onFactSuperseded(obsoleteOld, replacement)
        onFactUpdated(replacement)
        return UpdateOutcome.changed(replacement, listOf(obsoleteOld, replacement), UpdateClassification.SUPERSEDES)
    }

    private fun contradict(
        store: ModelStore,
        existing: ModelFact,
        update: ModelUpdate,
        newScore: Float,
        sourceReliability: Float,
        evidenceStrength: Float
    ): UpdateOutcome {
        val challenger = newFact(update, newScore, sourceReliability, evidenceStrength)
            .copy(status = ModelFactStatus.CONTRADICTED, conflictingFactIds = listOf(existing.factId))
        // The current candidate stays as the (contested) current-by-key fact —
        // it is visibly CONTRADICTED. The challenger is preserved as a
        // conflicting alternative in history. Nothing is silently erased.
        val contestedCurrent = existing.copy(
            status = ModelFactStatus.CONTRADICTED,
            conflictingFactIds = existing.conflictingFactIds + challenger.factId,
            confidence = existing.confidence.copy(contradictionCount = existing.confidence.contradictionCount + 1),
            updatedAt = update.observedAt
        )
        store.setFact(contestedCurrent)
        store.pushHistory(challenger)
        val conflict = ModelFactConflict(
            domain = update.domain,
            factKey = update.factKey,
            existingFactId = existing.factId,
            existingValue = existing.value,
            existingConfidence = existing.confidence.score,
            newValue = update.value,
            newConfidence = newScore,
            method = config.defaultResolution,
            detectedAt = update.observedAt
        )
        onConflictDetected(conflict)
        onFactUpdated(contestedCurrent)
        return UpdateOutcome.changed(contestedCurrent, listOf(contestedCurrent, challenger), UpdateClassification.CONTRADICTS)
    }

    private fun evidenceFor(update: ModelUpdate, strength: Float): ModelEvidence =
        ModelEvidence(
            source = update.source,
            description = update.evidenceDescription,
            sourceId = update.sourceId,
            strength = strength,
            timestamp = update.observedAt
        )

    private fun isStale(fact: ModelFact, now: Long): Boolean {
        if (!fact.isCurrent()) return false
        val horizon = when (fact.staleness.expectedChangeRate) {
            ExpectedChangeRate.STABLE -> config.stalenessHorizonMs * 4
            ExpectedChangeRate.SLOW -> config.stalenessHorizonMs * 2
            ExpectedChangeRate.MODERATE -> config.stalenessHorizonMs
            ExpectedChangeRate.RAPID -> config.stalenessHorizonMs / 4
        }
        val age = now - fact.staleness.lastVerifiedAt
        // Age is discounted by world volatility: volatile domains stale sooner.
        return age > horizon * (1.0f - fact.staleness.worldVolatility * 0.5f)
    }

    private fun entityFact(entity: WorldEntity): ModelFact =
        ModelFact(
            domain = ModelDomain.WORLD,
            factKey = entity.entityId,
            value = entity.canonicalName,
            status = if (entity.confidence.confirmationState == ConfirmationState.CONFIRMED)
                ModelFactStatus.CONFIRMED else ModelFactStatus.OBSERVED,
            confidence = entity.confidence,
            evidence = listOf(ModelEvidence(entity.source, "world entity observation", entity.sourceId)),
            sensitivity = entity.sensitivity,
            staleness = StalenessMetadata(expectedChangeRate = entity.expectedChangeRate)
        )

    private fun relationshipFact(rel: WorldRelationship): ModelFact =
        ModelFact(
            domain = ModelDomain.WORLD,
            factKey = rel.relationshipId,
            value = "${rel.sourceEntityId} ${rel.type.id} ${rel.targetEntityId}",
            status = ModelFactStatus.OBSERVED,
            confidence = rel.confidence,
            evidence = rel.evidence
        )
}

/**
 * ModelUpdate - Normalized input for the shared update pipeline.
 */
data class ModelUpdate(
    val domain: ModelDomain,
    val factKey: String,
    val value: String,
    val source: ModelEvidenceSource,
    val sourceId: String,
    val evidenceDescription: String = "",
    val evidenceStrength: Float = 0.5f,
    val sourceReliability: Float = 0.7f,
    val sensitivity: Sensitivity = Sensitivity.NONE,
    val expectedChangeRate: ExpectedChangeRate = ExpectedChangeRate.STABLE,
    val worldVolatility: Float = 0.0f,
    val observedAt: Long = System.currentTimeMillis()
)

/**
 * ModelFactConflict - Structured representation of a model-fact contradiction.
 *
 * Uses the 01G conflict semantics: the conflict exposes both alternatives with
 * their confidence and provenance; resolution uses [ResolutionMethod]. Nothing
 * is silently erased.
 */
data class ModelFactConflict(
    val conflictId: String = "mc_${System.currentTimeMillis()}_${(0..9).map { (Math.random() * 36).toInt().toChar() }.joinToString("")}",
    val domain: ModelDomain,
    val factKey: String,
    val existingFactId: String,
    val existingValue: String,
    val existingConfidence: Float,
    val newValue: String,
    val newConfidence: Float,
    val method: ResolutionMethod,
    val detectedAt: Long,
    val resolved: Boolean = false
) {
    fun summary(): String =
        "ModelFactConflict[${domain.name}:$factKey] '$existingValue'(c=${"%.2f".format(existingConfidence)}) " +
            "vs '$newValue'(c=${"%.2f".format(newConfidence)}) via ${method.description}"
}

/**
 * UpdateOutcome - Deterministic result of one observation.
 */
data class UpdateOutcome(
    val classification: UpdateClassification,
    val changed: Boolean,
    /** The new/changed primary fact (or null on no-change). */
    val fact: ModelFact? = null,
    /** All facts produced/touched by this update (for persistence delta). */
    val touchedFacts: List<ModelFact> = emptyList(),
    val conflict: ModelFactConflict? = null
) {
    companion object {
        fun noChange(update: ModelUpdate, classification: UpdateClassification, existing: ModelFact? = null): UpdateOutcome =
            UpdateOutcome(classification, changed = false, fact = existing)

        fun changed(fact: ModelFact, touched: List<ModelFact>, classification: UpdateClassification): UpdateOutcome =
            UpdateOutcome(classification, changed = true, fact = fact, touchedFacts = touched)
    }
}
