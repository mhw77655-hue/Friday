package com.jarvis.app.cognitive

/**
 * Detects pronouns and reference language in user text and resolves each to a
 * concrete entity from [ReferenceStore].  Resolved text replaces pronouns with
 * the entity name so downstream routing/execution sees the real entity.
 *
 * Type-mismatched candidates are excluded (e.g. "it" won't resolve to a PERSON).
 * When 2+ equally-plausible candidates of the same type exist the resolver
 * returns [ResolutionResult.ambiguous] instead of silently guessing.
 */
class PronounResolver(
    private val referenceStore: ReferenceStore,
    /** When set, candidate selection uses salience scoring instead of arbitrary ordering. */
    private val salienceScorer: SalienceScorer? = null
) {

    /** Current turn index and segment id — set before each resolve() call for salience ranking. */
    private var resolveTurnIndex: Long = 0
    private var resolveSegmentId: Int = 0

    /**
     * A single pronoun/reference detected in the turn and its resolution.
     */
    data class Resolution(
        val surface: String,         // the matched pronoun/reference phrase
        val resolvedTo: String?,     // entity name if resolved, null if ambiguous/missing
        val entityType: IntentInference.EntityType?,
        val ambiguous: Boolean = false,
        val candidates: List<IntentInference.Entity> = emptyList()
    )

    data class ResolutionResult(
        val resolvedText: String,
        val resolutions: List<Resolution>,
        val ambiguous: Boolean
    )

    companion object {
        /**
         * Maps a pronoun to the entity types it can grammatically refer to.
         * "it"/"that"/"this" → OBJECT or CONCEPT (never PERSON).
         * "he"/"she" → PERSON only.
         * "them"/"they" → any type (plural reference).
         */
        private val PRONOUN_TYPE_MAP: Map<String, Set<IntentInference.EntityType>> = mapOf(
            "it" to setOf(IntentInference.EntityType.OBJECT, IntentInference.EntityType.CONCEPT),
            "that" to setOf(IntentInference.EntityType.OBJECT, IntentInference.EntityType.CONCEPT),
            "this" to setOf(IntentInference.EntityType.OBJECT, IntentInference.EntityType.CONCEPT),
            "them" to IntentInference.EntityType.values().toSet(),
            "they" to IntentInference.EntityType.values().toSet(),
            "he" to setOf(IntentInference.EntityType.PERSON),
            "she" to setOf(IntentInference.EntityType.PERSON)
        )

        // Ordinal words → 1-based position for "the N one" references
        private val ORDINALS = mapOf(
            "first" to 1, "second" to 2, "third" to 3, "fourth" to 4,
            "fifth" to 5, "1st" to 1, "2nd" to 2, "3rd" to 3, "4th" to 4, "5th" to 5
        )
    }

    /**
     * Count how many entities in the store match a set of compatible types.
     * When a [SalienceScorer] is wired, candidates are sorted by descending
     * salience (most relevant first). Used to detect ambiguity: 2+ same-type
     * entities = ambiguous.
     */
    private fun countCandidates(compatibleTypes: Set<IntentInference.EntityType>): List<IntentInference.Entity> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<IntentInference.Entity>()
        for (mention in referenceStore.all()) {
            val entity = mention.entity
            if (entity.type in compatibleTypes && entity.id !in seen) {
                seen.add(entity.id)
                result.add(entity)
            }
        }

        // Sort by salience when scorer is available (most salient first)
        if (salienceScorer != null && result.size > 1) {
            result.sortByDescending { entity ->
                salienceScorer.score(entity, resolveTurnIndex, resolveSegmentId)
            }
        }

        return result
    }

    /**
     * Set the conversation context for salience-backed resolution.
     * Must be called before [resolve] when using salience scoring.
     */
    fun setContext(turnIndex: Long, segmentId: Int) {
        resolveTurnIndex = turnIndex
        resolveSegmentId = segmentId
    }

    /**
     * Detect and resolve all pronouns/references in [text].
     *
     * Returns a [ResolutionResult] with the text having pronouns replaced by
     * entity names, and the list of individual resolutions for inspection.
     *
     * Replacement is done via regex to avoid partial-word matches (e.g. "he"
     * inside "Where").
     */
    fun resolve(text: String): ResolutionResult {
        val resolutions = mutableListOf<Resolution>()

        // Collect all replacements as (range, replacement) pairs
        data class Replacement(val range: IntRange, val replacement: String)
        val replacements = mutableListOf<Replacement>()

        // 1. Resolve ordinal references: "the first one", "the second one"
        val ordinalPattern = Regex("""(?i)\bthe\s+(first|second|third|fourth|fifth|1st|2nd|3rd|4th|5th)\s+one\b""")
        ordinalPattern.findAll(text).forEach { match ->
            val ordinalWord = match.groupValues[1].lowercase()
            val position = ORDINALS[ordinalWord] ?: return@forEach
            val allMentions = referenceStore.all()
            // Position is 1-based, most-recent-first → flip to chronological, then pick by position
            val chronological = allMentions.reversed()
            val entity = if (position in chronological.indices) chronological[position - 1].entity else null
            resolutions.add(Resolution(
                surface = match.value,
                resolvedTo = entity?.name,
                entityType = entity?.type
            ))
            if (entity != null) {
                replacements.add(Replacement(match.range, entity.name))
            }
        }

        // 2. Resolve pronouns
        val pronounPattern = Regex("""(?i)\b(it|that|this|them|they|he|she)\b""")
        pronounPattern.findAll(text).forEach { match ->
            val pronoun = match.groupValues[1].lowercase()
            val compatibleTypes = PRONOUN_TYPE_MAP[pronoun] ?: return@forEach

            val candidates = countCandidates(compatibleTypes)

            when {
                candidates.isEmpty() -> {
                    resolutions.add(Resolution(
                        surface = match.value,
                        resolvedTo = null,
                        entityType = null
                    ))
                }
                candidates.size == 1 -> {
                    val entity = candidates[0]
                    resolutions.add(Resolution(
                        surface = match.value,
                        resolvedTo = entity.name,
                        entityType = entity.type
                    ))
                    replacements.add(Replacement(match.range, entity.name))
                }
                else -> {
                    // With salience scoring, if the top candidate is significantly
                    // more salient than the rest, resolve to it (not ambiguous).
                    val resolved = if (salienceScorer != null && candidates.size >= 2) {
                        val topScore = salienceScorer.score(candidates[0], resolveTurnIndex, resolveSegmentId)
                        val secondScore = salienceScorer.score(candidates[1], resolveTurnIndex, resolveSegmentId)
                        // Clear winner: top candidate's score is at least 1.5x the second
                        if (topScore > 0.0 && topScore > secondScore * 1.5) {
                            candidates[0]
                        } else null
                    } else null

                    if (resolved != null) {
                        resolutions.add(Resolution(
                            surface = match.value,
                            resolvedTo = resolved.name,
                            entityType = resolved.type
                        ))
                        replacements.add(Replacement(match.range, resolved.name))
                    } else {
                        resolutions.add(Resolution(
                            surface = match.value,
                            resolvedTo = null,
                            entityType = null,
                            ambiguous = true,
                            candidates = candidates
                        ))
                    }
                }
            }
        }

        // Apply replacements from end to start so earlier indices stay valid
        var resolvedText = text
        for (r in replacements.sortedByDescending { it.range.first }) {
            resolvedText = resolvedText.substring(0, r.range.first) +
                r.replacement +
                resolvedText.substring(r.range.last + 1)
        }

        return ResolutionResult(
            resolvedText = resolvedText,
            resolutions = resolutions,
            ambiguous = resolutions.any { it.ambiguous }
        )
    }
}
