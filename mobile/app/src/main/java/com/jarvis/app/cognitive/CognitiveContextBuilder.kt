package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryType
import com.jarvis.app.cognitive.memory.ContinuityPort
import com.jarvis.app.cognitive.model.ModelContextPort
import com.jarvis.app.cognitive.model.ModelContextSnapshot
import com.jarvis.app.cognitive.model.ModelFact
import com.jarvis.app.cognitive.model.WorldEntity
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.model.adapters.ifNotEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * CognitiveContextBuilder - Builds a compact, focused context for reasoning.
 *
 * Principles:
 * - Do NOT dump all history into context
 * - Do NOT make the model receive everything
 * - Rank and select only the most relevant items
 * - Compress when necessary
 * - Include only what's needed for the current cognitive step
 *
 * Build 01G continuity seam: when a [ContinuityPort] is supplied, the builder
 * requests a bounded reconstruction of the current state relevant to this
 * task ("what is true now", not "everything we have ever known") and folds it
 * into the context as a first-class section.
 */
class CognitiveContextBuilder(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val config: Config = Config(),
    private val continuity: ContinuityPort? = null,

    /** Build 01H seam: bounded, relevance-selected Self/User/World model
     *  projection (never the full models). */
    private val modelContext: ModelContextPort? = null
) {

    data class Config(
        val maxContextTokens: Int = 2048,
        val maxMemoryItems: Int = 10,
        val maxAttentionItems: Int = 7,
        val maxActiveMemories: Int = 15,
        val maxSubgoals: Int = 5,
        val maxConstraints: Int = 5,
        val maxUnknowns: Int = 5,
        val includeSelfModel: Boolean = true,
        val includeUserModel: Boolean = true,
        val includeWorldModel: Boolean = true,
        val includeUncertainty: Boolean = true,
        val compressionEnabled: Boolean = true,
        val minRelevanceForMemory: Float = 0.3f
    )

    /** Build compact cognitive context for reasoning */
    suspend fun build(
        cognitiveState: CognitiveState,
        intentResult: IntentInference.IntentInferenceResult,
        workingMemory: WorkingMemory,
        attentionSpotlight: AttentionEngine.AttentionSpotlight,
        sessionContext: SessionContext? = null
    ): CognitiveContext {
        val builder = StringBuilder()
        val sections = mutableListOf<ContextSection>()

        // 1. Current cognitive frame (always included, high priority)
        val frameSection = buildFrameSection(cognitiveState, intentResult)
        sections.add(ContextSection("frame", frameSection, SectionPriority.CRITICAL, estimateTokens(frameSection)))

        // 2. Active goal & subgoals
        if (cognitiveState.currentGoal != null || cognitiveState.activeSubgoals.isNotEmpty()) {
            val goalSection = buildGoalSection(cognitiveState)
            sections.add(ContextSection("goals", goalSection, SectionPriority.HIGH, estimateTokens(goalSection)))
        }

        // 3. Attention spotlight (what matters NOW)
        if (attentionSpotlight.items.isNotEmpty()) {
            val attentionSection = buildAttentionSection(attentionSpotlight)
            sections.add(ContextSection("attention", attentionSection, SectionPriority.HIGH, estimateTokens(attentionSection)))
        }

        // 4. Relevant working memory (top activated items)
        val activeMemories = workingMemory.getActive(config.minRelevanceForMemory)
            .take(config.maxActiveMemories)
        if (activeMemories.isNotEmpty()) {
            val memorySection = buildMemorySection(activeMemories)
            sections.add(ContextSection("memory", memorySection, SectionPriority.MEDIUM, estimateTokens(memorySection)))
        }

        // 4b. Continuity reconstruction (Build 01G): "what is true now" —
        //     bounded, provenance-aware, only if a port is wired.
        val continuitySection = buildContinuitySection(cognitiveState, intentResult)
        if (continuitySection != null) {
            sections.add(ContextSection(
                "continuity", continuitySection, SectionPriority.HIGH, estimateTokens(continuitySection)
            ))
        }

        // 5. Constraints from intent inference
        if (intentResult.constraints.isNotEmpty()) {
            val constraintSection = buildConstraintSection(intentResult.constraints.take(config.maxConstraints))
            sections.add(ContextSection("constraints", constraintSection, SectionPriority.HIGH, estimateTokens(constraintSection)))
        }

        // 6. Unknowns requiring resolution
        if (intentResult.unknowns.isNotEmpty()) {
            val unknownSection = buildUnknownSection(intentResult.unknowns.take(config.maxUnknowns))
            sections.add(ContextSection("unknowns", unknownSection, SectionPriority.MEDIUM, estimateTokens(unknownSection)))
        }

        // 4c. Bounded Self/User/World model projection (Build 01H) — relevance
        //     selected behind the ModelContextPort, never the full models.
        val modelSnapshot = if (modelContext != null) {
            modelContext.requestModelContext(
                query = intentResult.finalIntent.name,
                currentGoal = cognitiveState.currentGoal?.description,
                activeEntities = activeMemories.take(5).map { it.content }
            )
        } else null

        // 7. Self model (compact) — runtime view enriched with model facts
        if (config.includeSelfModel) {
            val selfSection = buildSelfSection(cognitiveState.selfState)
            val enriched = modelSnapshot?.selfFacts?.let { appendModelFacts(selfSection, it, "self") } ?: selfSection
            sections.add(ContextSection("self", enriched, SectionPriority.LOW, estimateTokens(enriched)))
        }

        // 8. User model (compact) — runtime view enriched with model facts
        if (config.includeUserModel) {
            val userSection = buildUserSection(cognitiveState.userState)
            val enriched = modelSnapshot?.userFacts?.let { appendModelFacts(userSection, it, "user") } ?: userSection
            sections.add(ContextSection("user", enriched, SectionPriority.LOW, estimateTokens(enriched)))
        }

        // 9. World model (compact) — runtime view enriched with model entities
        if (config.includeWorldModel) {
            val worldSection = buildWorldSection(cognitiveState.worldState)
            val enriched = modelSnapshot?.worldEntities?.let { appendWorldEntities(worldSection, it) } ?: worldSection
            sections.add(ContextSection("world", enriched, SectionPriority.LOW, estimateTokens(enriched)))
        }

        // 10. Uncertainty profile
        if (config.includeUncertainty && cognitiveState.uncertainty.overall > 0.1f) {
            val uncertaintySection = buildUncertaintySection(cognitiveState.uncertainty)
            sections.add(ContextSection("uncertainty", uncertaintySection, SectionPriority.MEDIUM, estimateTokens(uncertaintySection)))
        }

        // Apply token budget
        val finalSections = applyTokenBudget(sections)

        // Build final context string
        for (section in finalSections) {
            if (section.included) {
                builder.append(section.content).append("\n\n")
            }
        }

        val contextText = builder.toString().trim()
        val totalTokens = finalSections.filter { it.included }.sumOf { it.tokens }

        return CognitiveContext(
            text = contextText,
            sections = finalSections,
            totalTokens = totalTokens,
            withinBudget = totalTokens <= config.maxContextTokens,
            timestamp = System.currentTimeMillis()
        )
    }

    /** Build the current cognitive frame section */
    private fun buildFrameSection(state: CognitiveState, intent: IntentInference.IntentInferenceResult): String {
        val sb = StringBuilder()
        sb.append("=== COGNITIVE FRAME ===\n")
        sb.append("Turn: ${state.turnIndex}\n")
        sb.append("Intent: ${intent.finalIntent.name} (confidence: ${"%.2f".format(intent.confidence)})\n")
        sb.append("  Explicit: ${intent.explicitIntent.type.name} (${"%.2f".format(intent.explicitIntent.confidence)})\n")
        sb.append("  Inferred: ${intent.inferredIntent.type.name} (${"%.2f".format(intent.inferredIntent.confidence)})\n")
        if (intent.ambiguity.isAmbiguous) {
            sb.append("  ⚠ AMBIGUOUS: ${intent.ambiguity.primaryAmbiguity} (gap: ${"%.2f".format(intent.ambiguity.confidenceGap)})\n")
        }
        sb.append("Resource pressure: ${if (state.resourceState.isUnderPressure()) "HIGH" else "NORMAL"}\n")
        sb.append("Capabilities degraded: ${state.capabilityState.degradedCapabilities.joinToString(", ") { if (it.isEmpty()) "none" else it }}\n")
        return sb.toString()
    }

    /** Build goal and subgoals section */
    private fun buildGoalSection(state: CognitiveState): String {
        val sb = StringBuilder()
        sb.append("=== ACTIVE GOALS ===\n")

        state.currentGoal?.let { goal ->
            sb.append("Primary: ${goal.description}\n")
            sb.append("  Priority: ${goal.priority.name}, Status: ${goal.status.name}\n")
            sb.append("  Success: ${goal.successCriteria.joinToString(", ") { if (it.isEmpty()) "none" else it }}\n")
            if (goal.deadline != null) {
                sb.append("  Deadline: ${java.time.Instant.ofEpochMilli(goal.deadline!!)}\n")
            }
        }

        val activeSubgoals = state.activeSubgoals.filter { it.status in setOf(SubgoalStatus.PENDING, SubgoalStatus.IN_PROGRESS) }
            .take(config.maxSubgoals)
        if (activeSubgoals.isNotEmpty()) {
            sb.append("Subgoals:\n")
            for (subgoal in activeSubgoals) {
                sb.append("  - [${subgoal.status.name}] ${subgoal.description}\n")
            }
        }

        state.currentPlan?.let { plan ->
            sb.append("Plan: ${plan.steps.size} steps (${plan.status.name})\n")
            val nextStep = plan.steps.firstOrNull { it.status == PlanStepStatus.PENDING || it.status == PlanStepStatus.IN_PROGRESS }
            nextStep?.let { step ->
                sb.append("  Next: ${step.action} → ${step.expectedOutcome}\n")
            }
        }

        return sb.toString()
    }

    /** Build attention spotlight section */
    private fun buildAttentionSection(spotlight: AttentionEngine.AttentionSpotlight): String {
        val sb = StringBuilder()
        sb.append("=== ATTENTION SPOTLIGHT ===\n")
        sb.append("Top ${spotlight.items.size} items (avg score: ${"%.2f".format(spotlight.averageScore)}):\n")

        for ((i, item) in spotlight.items.withIndex()) {
            sb.append("  ${i + 1}. [${item.source.name}] ${item.content}")
            sb.append(" (salience: ${"%.2f".format(item.salience)}, urgency: ${"%.2f".format(item.urgency)}")
            sb.append(", goalRel: ${"%.2f".format(item.goalRelevance)}, novelty: ${"%.2f".format(item.novelty)})\n")
        }

        return sb.toString()
    }

    /** Build working memory section */
    private fun buildMemorySection(memories: List<ActiveMemory>): String {
        val sb = StringBuilder()
        sb.append("=== WORKING MEMORY (top ${memories.size}) ===\n")

        for ((i, mem) in memories.withIndex()) {
            sb.append("  ${i + 1}. [${mem.memoryType.name}] ${mem.content}")
            sb.append(" (act: ${"%.2f".format(mem.activation)}, rel: ${"%.2f".format(mem.relevance)}")
            sb.append(", goalAlign: ${"%.2f".format(mem.goalAlignment)}, uncert: ${"%.2f".format(mem.uncertainty)})\n")
            if (mem.tags.isNotEmpty()) {
                sb.append("      Tags: ${mem.tags.joinToString(", ")}\n")
            }
        }

        return sb.toString()
    }

    /**
     * Build the continuity section (Build 01G). Suspends only when a
     * [ContinuityPort] is wired; returns null otherwise so cognition is
     * unaffected when no continuity backend is present.
     */
    private suspend fun buildContinuitySection(
        state: CognitiveState,
        intent: IntentInference.IntentInferenceResult
    ): String? {
        val port = continuity ?: return null
        val query = intent.finalIntent.name + " " +
            (state.currentGoal?.description ?: "")
        val entities = intent.explicitIntent.entities.map { it.name }
        val snapshot = port.reconstructContinuity(
            query = query,
            currentGoal = state.currentGoal?.description,
            activeEntities = entities
        )
        if (snapshot.isEmpty) return null

        val sb = StringBuilder()
        sb.append("=== CONTINUITY (what is true now) ===\n")

        if (snapshot.activeTruths.isNotEmpty()) {
            sb.append("Active truths:\n")
            for (t in snapshot.activeTruths) {
                sb.append("  • [${t.memoryType.name}] ${t.content}")
                sb.append(" (conf: ${"%.2f".format(t.confidence)})\n")
            }
        }
        if (snapshot.superseded.isNotEmpty()) {
            sb.append("Superseded (historical):\n")
            for (t in snapshot.superseded) {
                sb.append("  • ${t.content} (superseded, conf: ${"%.2f".format(t.confidence)})\n")
            }
        }
        if (snapshot.conflicts.isNotEmpty()) {
            sb.append("⚠ Unresolved conflicts:\n")
            for (c in snapshot.conflicts) {
                sb.append("  • ${c.description}\n")
            }
        }
        if (snapshot.unknowns.isNotEmpty()) {
            sb.append("Unknowns (cannot establish):\n")
            for (u in snapshot.unknowns) {
                sb.append("  • $u\n")
            }
        }
        return sb.toString()
    }

    /** Build constraints section */
    private fun buildConstraintSection(constraints: List<IntentInference.Constraint>): String {
        val sb = StringBuilder()
        sb.append("=== CONSTRAINTS ===\n")
        for (c in constraints) {
            sb.append("  - [${c.type.name}] ${c.description} (strength: ${"%.2f".format(c.strength)})\n")
        }
        return sb.toString()
    }

    /** Build unknowns section */
    private fun buildUnknownSection(unknowns: List<IntentInference.Unknown>): String {
        val sb = StringBuilder()
        sb.append("=== UNKNOWNS ===\n")
        for (u in unknowns) {
            sb.append("  - [${u.type.name}] ${u.description} (impact: ${"%.2f".format(u.impact)})\n")
        }
        return sb.toString()
    }

    /** Build self model section (compact) */
    private fun buildSelfSection(self: SelfModel): String {
        val sb = StringBuilder()
        sb.append("=== SELF MODEL ===\n")
        sb.append("Identity: ${self.identityName} v${self.identityVersion}\n")
        sb.append("Mood: valence=${"%.2f".format(self.currentMood.valence)}, arousal=${"%.2f".format(self.currentMood.arousal)}")
        self.currentMood.dominantEmotion?.let { sb.append(", emotion=$it") }
        sb.append("\n")
        sb.append("Cognitive load: ${"%.2f".format(self.selfAssessment.cognitiveLoad)}, confidence: ${"%.2f".format(self.selfAssessment.confidence)}\n")
        if (self.limitations.isNotEmpty()) {
            sb.append("Limitations: ${self.limitations.take(3).joinToString(", ")}\n")
        }
        return sb.toString()
    }

    /** Build user model section (compact) */
    private fun buildUserSection(user: UserModel): String {
        val sb = StringBuilder()
        sb.append("=== USER MODEL ===\n")
        user.knownName?.let { sb.append("Name: $it\n") }
        sb.append("Relationship: ${"%.2f".format(user.relationshipDepth)}, Trust: ${"%.2f".format(user.trustLevel)}\n")
        sb.append("Style: formal=${"%.2f".format(user.communicationStyle.formality)}, verbose=${"%.2f".format(user.communicationStyle.verbosity)}")
        sb.append(", direct=${"%.2f".format(user.communicationStyle.directness)}, humor=${"%.2f".format(user.communicationStyle.humor)}\n")
        user.currentContext.currentActivity?.let { sb.append("Activity: $it\n") }
        user.currentContext.recentTopics.take(3).ifNotEmpty { sb.append("Recent topics: ${it.joinToString(", ")}\n") }
        if (user.predictedNeeds.isNotEmpty()) {
            sb.append("Predicted needs: ${user.predictedNeeds.take(3).joinToString(", ")}\n")
        }
        return sb.toString()
    }

    /** Build world model section (compact) */
    private fun buildWorldSection(world: WorldModel): String {
        val sb = StringBuilder()
        sb.append("=== WORLD MODEL ===\n")
        sb.append("Time: ${java.time.Instant.ofEpochMilli(world.currentTime)} (${world.timeZone})\n")
        sb.append("Environment: ${world.environment.deviceState}, connectivity=${world.environment.connectivity}\n")
        if (world.activeEntities.isNotEmpty()) {
            sb.append("Entities: ${world.activeEntities.take(3).map { "${it.name}(${it.type.name})" }.joinToString(", ")}\n")
        }
        world.temporalContext.upcomingEvents.take(2).ifNotEmpty { sb.append("Upcoming: ${it.joinToString(", ")}\n") }
        return sb.toString()
    }

    /** Append bounded, relevance-selected model facts to a section (Build 01H). */
    private fun appendModelFacts(section: String, facts: List<ModelFact>, label: String): String {
        if (facts.isEmpty()) return section
        val sb = StringBuilder(section)
        if (!section.endsWith("\n")) sb.append("\n")
        sb.append("Model facts ($label):\n")
        for (f in facts.take(6)) {
            sb.append("  - ${f.factKey} = ${f.value} [${f.status.name}, conf=${"%.2f".format(f.confidence.score)}]\n")
        }
        return sb.toString()
    }

    /** Append bounded, relevance-selected world entities to the world section. */
    private fun appendWorldEntities(section: String, entities: List<WorldEntity>): String {
        if (entities.isEmpty()) return section
        val sb = StringBuilder(section)
        if (!section.endsWith("\n")) sb.append("\n")
        sb.append("Model entities:\n")
        for (e in entities.take(5)) {
            sb.append("  - ${e.canonicalName} (${e.entityType.id}) [${e.state.name}, conf=${"%.2f".format(e.confidence.score)}]\n")
        }
        return sb.toString()
    }

    /** Build uncertainty section */
    private fun buildUncertaintySection(uncertainty: UncertaintyProfile): String {
        val sb = StringBuilder()
        sb.append("=== UNCERTAINTY ===\n")
        sb.append("Overall: ${"%.2f".format(uncertainty.overall)}\n")
        sb.append("  Intent ambiguity: ${"%.2f".format(uncertainty.intentAmbiguity)}\n")
        sb.append("  Factual uncertainty: ${"%.2f".format(uncertainty.factualUncertainty)}\n")
        sb.append("  Goal conflict: ${"%.2f".format(uncertainty.goalConflict)}\n")
        sb.append("  Resource uncertainty: ${"%.2f".format(uncertainty.resourceUncertainty)}\n")
        if (uncertainty.unknowns.isNotEmpty()) {
            sb.append("Unknowns: ${uncertainty.unknowns.take(3).joinToString(", ")}\n")
        }
        if (uncertainty.assumptions.isNotEmpty()) {
            sb.append("Assumptions: ${uncertainty.assumptions.take(3).joinToString(", ")}\n")
        }
        return sb.toString()
    }

    /** Apply token budget to sections */
    private fun applyTokenBudget(sections: List<ContextSection>): List<ContextSection> {
        var totalTokens = 0
        val result = mutableListOf<ContextSection>()

        // Sort by priority first — CRITICAL (frame) must always land at the top
        // so it is never starved of budget by lower-priority sections.
        val sorted = sections.sortedBy { it.priority.ordinal }

        for (section in sorted) {
            val sectionTokens = section.tokens
            if (totalTokens + sectionTokens <= config.maxContextTokens) {
                result.add(section.copy(included = true))
                totalTokens += sectionTokens
            } else if (config.compressionEnabled && section.priority != SectionPriority.CRITICAL) {
                // Try compression
                val compressed = compressSection(section)
                val compressedTokens = estimateTokens(compressed.content)
                if (totalTokens + compressedTokens <= config.maxContextTokens) {
                    result.add(compressed.copy(included = true))
                    totalTokens += compressedTokens
                } else {
                    result.add(section.copy(included = false))
                }
            } else {
                result.add(section.copy(included = false))
            }
        }

        return result
    }

    /** Compress a section by summarizing */
    private fun compressSection(section: ContextSection): ContextSection {
        val lines = section.content.lines().toList()
        if (lines.size <= 3) return section

        val keepCount = maxOf(2, (lines.size * 0.5).toInt())
        val firstLines = lines.take(keepCount / 2)
        val lastLines = lines.takeLast(keepCount - keepCount / 2)

        val compressedContent = (firstLines + listOf("... [${lines.size - keepCount} lines compressed] ...") + lastLines).joinToString("\n")

        return section.copy(
            content = compressedContent,
            tokens = estimateTokens(compressedContent)
        )
    }

    /** Estimate token count (rough: ~4 chars per token) */
    private fun estimateTokens(text: String): Int = maxOf(1, text.length / 4)

    // Data classes
    enum class SectionPriority { CRITICAL, HIGH, MEDIUM, LOW }

    data class ContextSection(
        val name: String,
        val content: String,
        val priority: SectionPriority,
        val tokens: Int,
        val included: Boolean = true
    )

    data class CognitiveContext(
        val text: String,
        val sections: List<ContextSection>,
        val totalTokens: Int,
        val withinBudget: Boolean,
        val timestamp: Long
    ) {
        fun getIncludedSections(): List<String> = sections.filter { it.included }.map { it.name }
        fun getExcludedSections(): List<String> = sections.filter { !it.included }.map { it.name }
    }
}