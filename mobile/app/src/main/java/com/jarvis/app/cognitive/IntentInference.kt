package com.jarvis.app.cognitive

import com.jarvis.app.humancore.protocol.SessionContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * IntentInference - Infers user intent from input, distinguishing:
 * - Explicit intent (directly stated)
 * - Inferred intent (implied from context)
 * - Constraints (limitations on the intent)
 * - Ambiguity (multiple possible interpretations)
 * - Unknowns (what we cannot determine)
 */
class IntentInference(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val config: Config = Config()
) {

    data class Config(
        val minConfidenceForExplicit: Float = 0.7f,
        val minConfidenceForInferred: Float = 0.4f,
        val ambiguityThreshold: Float = 0.3f,  // Gap between top 2 intents
        val contextWeight: Float = 0.3f,
        val recencyWeight: Float = 0.2f
    )

    /** Infer intent from user input and cognitive context */
    suspend fun infer(
        userText: String,
        cognitiveState: CognitiveState,
        sessionContext: SessionContext? = null
    ): IntentInferenceResult {
        // 0. Empty/blank input is not an intent — short-circuit to UNKNOWN
        if (userText.isBlank()) {
            val emptyExplicit = ExplicitIntent(IntentState.UNKNOWN, 0.0f, "", emptyList())
            return IntentInferenceResult(
                explicitIntent = emptyExplicit,
                inferredIntent = InferredIntent(IntentState.UNKNOWN, 0.0f, emptyList(), "Empty input"),
                finalIntent = IntentState.UNKNOWN,
                confidence = 0.0f,
                constraints = emptyList(),
                ambiguity = AmbiguityAssessment(false, AmbiguityLevel.NONE, emptyList(), 0.0f, null),
                unknowns = listOf(Unknown(UnknownType.MISSING_PARAMETERS, "Input is empty", 0.8f)),
                timestamp = System.currentTimeMillis()
            )
        }

        // 1. Extract explicit intent from text
        val explicitIntent = extractExplicitIntent(userText)

        // 2. Infer intent from context
        val inferredIntent = inferFromContext(userText, cognitiveState, sessionContext)

        // 3. Identify constraints
        val constraints = extractConstraints(userText, cognitiveState)

        // 4. Assess ambiguity
        val ambiguity = assessAmbiguity(explicitIntent, inferredIntent, userText)

        // 5. Identify unknowns
        val unknowns = identifyUnknowns(userText, cognitiveState, explicitIntent, inferredIntent)

        // 6. Determine final intent and confidence
        val (finalIntent, confidence) = resolveIntent(explicitIntent, inferredIntent, ambiguity)

        return IntentInferenceResult(
            explicitIntent = explicitIntent,
            inferredIntent = inferredIntent,
            finalIntent = finalIntent,
            confidence = confidence,
            constraints = constraints,
            ambiguity = ambiguity,
            unknowns = unknowns,
            timestamp = System.currentTimeMillis()
        )
    }

    /** Extract explicitly stated intent from text */
    private fun extractExplicitIntent(text: String): ExplicitIntent {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) {
            return ExplicitIntent(IntentState.UNKNOWN, 0.0f, "", emptyList())
        }

        // Direct command patterns
        if (lower.startsWith("jarvis") || lower.startsWith("hey jarvis")) {
            val command = lower.removePrefix("jarvis").removePrefix("hey jarvis").trim()
            return ExplicitIntent(
                type = IntentState.COMMAND,
                confidence = 0.9f,
                extractedText = command,
                entities = extractEntities(command)
            )
        }

        // Meta-cognitive patterns — checked before generic question/teaching so
        // that "what are you thinking?" or "why did you decide that?" land as
        // META_COGNITIVE rather than being swallowed by the ? suffix rule.
        if (lower.contains("what are you thinking") || lower.contains("explain") ||
            lower.contains("why did you") || lower.contains("how did you") ||
            lower.contains("your reasoning")) {
            return ExplicitIntent(
                type = IntentState.META_COGNITIVE,
                confidence = 0.8f,
                extractedText = text,
                entities = emptyList()
            )
        }

        // Question patterns
        if (lower.endsWith("?") || lower.startsWith("what") || lower.startsWith("how") ||
            lower.startsWith("why") || lower.startsWith("when") || lower.startsWith("where") ||
            lower.startsWith("who") || lower.startsWith("can you") || lower.startsWith("could you")) {
            return ExplicitIntent(
                type = IntentState.QUESTION,
                confidence = 0.85f,
                extractedText = text,
                entities = extractEntities(text)
            )
        }

        // Teaching patterns
        if (lower.contains("teach") || lower.contains("learn") || lower.contains("remember") ||
            lower.contains("pronounce") || lower.contains("means") || lower.contains("is called")) {
            return ExplicitIntent(
                type = IntentState.TEACHING,
                confidence = 0.8f,
                extractedText = text,
                entities = extractEntities(text)
            )
        }

        // Correction patterns
        if (lower.contains("no, ") || lower.startsWith("actually ") || lower.contains("that's wrong") ||
            lower.contains("correction") || lower.contains("not ")) {
            return ExplicitIntent(
                type = IntentState.CORRECTION,
                confidence = 0.75f,
                extractedText = text,
                entities = extractEntities(text)
            )
        }

        // Greeting patterns (prefix match with word boundary — "hi there" is
        // still a greeting even though matches() would require a full match)
        if (Regex("(?i)^(hi|hello|hey|good morning|good evening|good afternoon)\\b").containsMatchIn(lower)) {
            return ExplicitIntent(
                type = IntentState.GREETING,
                confidence = 0.9f,
                extractedText = text,
                entities = emptyList()
            )
        }

        // Farewell patterns
        if (lower.matches(Regex("(?i)^(bye|goodbye|see you|farewell|later)"))) {
            return ExplicitIntent(
                type = IntentState.FAREWELL,
                confidence = 0.9f,
                extractedText = text,
                entities = emptyList()
            )
        }

        // Default: conversation
        return ExplicitIntent(
            type = IntentState.CONVERSATION,
            confidence = 0.5f,
            extractedText = text,
            entities = extractEntities(text)
        )
    }

    /** Infer intent from context (conversation history, goals, etc.) */
    private fun inferFromContext(
        userText: String,
        cognitiveState: CognitiveState,
        sessionContext: SessionContext?
    ): InferredIntent {
        val contextSignals = mutableListOf<ContextSignal>()
        var inferredType = IntentState.UNKNOWN
        var confidence = 0.0f

        // Signal: Current goal alignment
        if (cognitiveState.currentGoal != null) {
            val goalKeywords = cognitiveState.currentGoal!!.description.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            val textKeywords = userText.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            val overlap = goalKeywords.intersect(textKeywords).size
            if (overlap > 0) {
                val score = overlap.toFloat() / goalKeywords.size
                contextSignals.add(ContextSignal("goal_alignment", score, "Aligns with current goal: ${cognitiveState.currentGoal!!.description}"))
                if (score > confidence) {
                    confidence = score * 0.8f
                    inferredType = IntentState.COMMAND // Likely continuing a task
                }
            }
        }

        // Signal: Active subgoals
        if (cognitiveState.activeSubgoals.isNotEmpty()) {
            val nextSubgoal = cognitiveState.activeSubgoals.firstOrNull { it.status == SubgoalStatus.PENDING || it.status == SubgoalStatus.IN_PROGRESS }
            if (nextSubgoal != null) {
                val subgoalKeywords = nextSubgoal.description.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
                val textKeywords = userText.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
                val overlap = subgoalKeywords.intersect(textKeywords).size
                if (overlap > 0) {
                    val score = overlap.toFloat() / subgoalKeywords.size
                    contextSignals.add(ContextSignal("subgoal_alignment", score, "Aligns with active subgoal: ${nextSubgoal.description}"))
                    if (score * 0.7f > confidence) {
                        confidence = score * 0.7f
                        inferredType = IntentState.COMMAND
                    }
                }
            }
        }

        // Signal: Recent conversation context
        if (cognitiveState.activeMemories.isNotEmpty()) {
            val recentTopics = cognitiveState.activeMemories.take(5).flatMap { it.tags }.toSet()
            val textKeywords = userText.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            val overlap = recentTopics.intersect(textKeywords).size
            if (overlap > 0) {
                val score = overlap.toFloat() / maxOf(1, recentTopics.size)
                contextSignals.add(ContextSignal("topic_continuity", score, "Continues recent topic"))
                if (score * 0.5f > confidence) {
                    confidence = score * 0.5f
                    inferredType = IntentState.CONVERSATION
                }
            }
        }

        // Signal: User model preferences
        if (cognitiveState.userState.communicationStyle.directness > 0.7f && userText.length < 20) {
            contextSignals.add(ContextSignal("brevity_preference", 0.6f, "User prefers direct/brief communication"))
        }

        // Signal: Session context
        sessionContext?.let { ctx ->
            if (ctx.wasAway) {
                contextSignals.add(ContextSignal("session_resume", 0.7f, "User returning after absence"))
            }
            if ((ctx.secondsSinceLastContact ?: 0) > 3600) {
                contextSignals.add(ContextSignal("long_gap", 0.5f, "Long gap since last interaction"))
            }
        }

        // If no strong signals, check for ambiguity
        if (confidence < config.minConfidenceForInferred) {
            inferredType = IntentState.AMBIGUOUS
            confidence = 0.3f
        }

        return InferredIntent(
            type = inferredType,
            confidence = confidence,
            signals = contextSignals,
            reasoning = buildReasoning(contextSignals)
        )
    }

    /** Extract constraints from user input */
    private fun extractConstraints(text: String, cognitiveState: CognitiveState): List<Constraint> {
        val constraints = mutableListOf<Constraint>()
        val lower = text.lowercase()

        // Time constraints
        if (lower.contains("now") || lower.contains("immediately") || lower.contains("urgent") || lower.contains("asap")) {
            constraints.add(Constraint(ConstraintType.TIME, "Immediate execution required", 0.9f))
        }
        if (lower.contains("later") || lower.contains("when you can") || lower.contains("eventually")) {
            constraints.add(Constraint(ConstraintType.TIME, "Deferred execution acceptable", 0.7f))
        }

        // Modality constraints
        if (lower.contains("quietly") || lower.contains("silent") || lower.contains("don't speak")) {
            constraints.add(Constraint(ConstraintType.MODALITY, "Non-verbal response preferred", 0.8f))
        }
        if (lower.contains("speak") || lower.contains("say it") || lower.contains("out loud")) {
            constraints.add(Constraint(ConstraintType.MODALITY, "Verbal response required", 0.8f))
        }

        // Detail constraints
        if (lower.contains("brief") || lower.contains("short") || lower.contains("summary") || lower.contains("tldr")) {
            constraints.add(Constraint(ConstraintType.DETAIL, "Brief/concise response", 0.8f))
        }
        if (lower.contains("detail") || lower.contains("explain") || lower.contains("thorough") || lower.contains("comprehensive")) {
            constraints.add(Constraint(ConstraintType.DETAIL, "Detailed response", 0.8f))
        }

        // Privacy constraints
        if (lower.contains("private") || lower.contains("don't remember") || lower.contains("forget")) {
            constraints.add(Constraint(ConstraintType.PRIVACY, "Do not store/remember this", 0.9f))
        }

        // Resource constraints (from cognitive state)
        if (cognitiveState.resourceState.isUnderPressure()) {
            constraints.add(Constraint(ConstraintType.RESOURCE, "System under resource pressure", 0.6f))
        }

        return constraints
    }

    /** Assess ambiguity between explicit and inferred intent */
    private fun assessAmbiguity(
        explicit: ExplicitIntent,
        inferred: InferredIntent,
        text: String
    ): AmbiguityAssessment {
        val interpretations = mutableListOf<IntentInterpretation>()

        // Add explicit interpretation
        interpretations.add(IntentInterpretation(
            intent = explicit.type,
            confidence = explicit.confidence,
            source = InterpretationSource.EXPLICIT,
            evidence = "Direct linguistic pattern: ${explicit.extractedText}"
        ))

        // Add inferred interpretation if different
        if (inferred.type != IntentState.UNKNOWN && inferred.type != explicit.type) {
            interpretations.add(IntentInterpretation(
                intent = inferred.type,
                confidence = inferred.confidence,
                source = InterpretationSource.INFERRED,
                evidence = inferred.reasoning
            ))
        }

        // Check for multiple plausible explicit patterns
        val textLower = text.lowercase()
        var patternCount = 0
        if (textLower.endsWith("?")) patternCount++
        if (textLower.startsWith("jarvis") || textLower.startsWith("hey jarvis")) patternCount++
        if (textLower.contains("teach") || textLower.contains("learn")) patternCount++
        if (textLower.contains("no") || textLower.contains("correction")) patternCount++

        if (patternCount > 1) {
            interpretations.add(IntentInterpretation(
                intent = IntentState.AMBIGUOUS,
                confidence = 0.6f,
                source = InterpretationSource.CONFLICTING_PATTERNS,
                evidence = "Multiple linguistic patterns detected ($patternCount)"
            ))
        }

        // Sort by confidence
        interpretations.sortByDescending { it.confidence }

        val topConfidence = interpretations.firstOrNull()?.confidence ?: 0f
        val secondConfidence = interpretations.getOrNull(1)?.confidence ?: 0f
        val gap = topConfidence - secondConfidence

        val isAmbiguous = gap < config.ambiguityThreshold && interpretations.size > 1
        val ambiguityLevel = when {
            gap < 0.1f -> AmbiguityLevel.HIGH
            gap < 0.3f -> AmbiguityLevel.MEDIUM
            gap < 0.5f -> AmbiguityLevel.LOW
            else -> AmbiguityLevel.NONE
        }

        return AmbiguityAssessment(
            isAmbiguous = isAmbiguous,
            ambiguityLevel = ambiguityLevel,
            topInterpretations = interpretations.take(3),
            confidenceGap = gap,
            primaryAmbiguity = if (isAmbiguous) interpretations[0].intent.name + " vs " + interpretations[1].intent.name else null
        )
    }

    /** Identify unknowns - what we cannot determine */
    private fun identifyUnknowns(
        text: String,
        cognitiveState: CognitiveState,
        explicit: ExplicitIntent,
        inferred: InferredIntent
    ): List<Unknown> {
        val unknowns = mutableListOf<Unknown>()

        // Unknown: Missing context for pronouns
        if (text.lowercase().contains("it") || text.lowercase().contains("that") || text.lowercase().contains("this")) {
            val hasReferent = cognitiveState.activeMemories.any { mem ->
                text.lowercase().contains("it") && mem.content.lowercase().contains("it")
            }
            if (!hasReferent) {
                unknowns.add(Unknown(
                    type = UnknownType.REFERENT,
                    description = "Pronoun 'it/that/this' has no clear referent in context",
                    impact = 0.6f
                ))
            }
        }

        // Unknown: Vague action
        val actionVerbs = listOf("do", "make", "handle", "fix", "take care of", "deal with")
        if (actionVerbs.any { text.lowercase().contains(it) }) {
            unknowns.add(Unknown(
                type = UnknownType.VAGUE_ACTION,
                description = "Generic action verb without specific target",
                impact = 0.5f
            ))
        }

        // Unknown: Missing parameters
        if (explicit.type == IntentState.COMMAND && explicit.entities.isEmpty()) {
            unknowns.add(Unknown(
                type = UnknownType.MISSING_PARAMETERS,
                description = "Command lacks specific entities/parameters",
                impact = 0.7f
            ))
        }

        // Unknown: Goal context missing
        if (cognitiveState.currentGoal == null && explicit.type == IntentState.COMMAND) {
            unknowns.add(Unknown(
                type = UnknownType.MISSING_GOAL_CONTEXT,
                description = "No active goal to frame this command",
                impact = 0.4f
            ))
        }

        // Unknown: User identity
        if (cognitiveState.userState.knownName == null) {
            unknowns.add(Unknown(
                type = UnknownType.USER_IDENTITY,
                description = "User name not known",
                impact = 0.2f
            ))
        }

        // Unknown: Capability match
        if (explicit.type == IntentState.COMMAND) {
            val hasCapability = cognitiveState.capabilityState.toolsAvailable.any { tool ->
                text.lowercase().contains(tool.lowercase())
            }
            if (!hasCapability && explicit.entities.isNotEmpty()) {
                unknowns.add(Unknown(
                    type = UnknownType.CAPABILITY_MATCH,
                    description = "Unclear if system has capability for requested action",
                    impact = 0.6f
                ))
            }
        }

        return unknowns
    }

    /** Resolve final intent from explicit and inferred */
    private fun resolveIntent(
        explicit: ExplicitIntent,
        inferred: InferredIntent,
        ambiguity: AmbiguityAssessment
    ): Pair<IntentState, Float> {
        // If explicit is high confidence and not ambiguous, use it
        if (explicit.confidence >= config.minConfidenceForExplicit && !ambiguity.isAmbiguous) {
            return explicit.type to explicit.confidence
        }

        // If inferred is stronger, use it
        if (inferred.confidence > explicit.confidence && inferred.confidence >= config.minConfidenceForInferred) {
            return inferred.type to inferred.confidence
        }

        // If ambiguous, return ambiguous with combined confidence
        if (ambiguity.isAmbiguous) {
            val combinedConfidence = (explicit.confidence + inferred.confidence) / 2
            return IntentState.AMBIGUOUS to combinedConfidence
        }

        // Default to explicit
        return explicit.type to explicit.confidence
    }

    /** Simple entity extraction */
    private fun extractEntities(text: String): List<Entity> {
        val entities = mutableListOf<Entity>()
        // Filter empty tokens — split(Regex("\\s+")) yields "" for leading or
        // doubled whitespace, and word.first() would crash on an empty string.
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

        // Very simple extraction - proper nouns, quoted strings, etc.
        var i = 0
        while (i < words.size) {
            val word = words[i]
            if (word.length <= 1) {
                i++
                continue
            }
            if (word.startsWith("\"") || word.startsWith("'")) {
                // Quoted entity
                val endQuote = word.lastIndexOf("\"") > 0 || word.lastIndexOf("'") > 0
                if (endQuote && word.length > 1) {
                    entities.add(Entity("ent_${entities.size}", word.substring(1, word.length - 1), EntityType.CONCEPT))
                }
            } else if (word.first().isUpperCase() && word.length > 1 &&
                i + 1 < words.size && words[i + 1].length > 1 && words[i + 1].first().isUpperCase()
            ) {
                // Potential proper noun (two capitalized words)
                entities.add(Entity("ent_${entities.size}", "$word ${words[i + 1]}", EntityType.CONCEPT))
                i++
            }
            i++
        }

        return entities
    }

    private fun buildReasoning(signals: List<ContextSignal>): String {
        if (signals.isEmpty()) return "No contextual signals"
        return signals.map { "${it.name}: ${"%.2f".format(it.strength)} - ${it.description}" }.joinToString("; ")
    }

    // Result types
    data class IntentInferenceResult(
        val explicitIntent: ExplicitIntent,
        val inferredIntent: InferredIntent,
        val finalIntent: IntentState,
        val confidence: Float,
        val constraints: List<Constraint>,
        val ambiguity: AmbiguityAssessment,
        val unknowns: List<Unknown>,
        val timestamp: Long
    )

    data class ExplicitIntent(
        val type: IntentState,
        val confidence: Float,
        val extractedText: String,
        val entities: List<Entity>
    )

    data class InferredIntent(
        val type: IntentState,
        val confidence: Float,
        val signals: List<ContextSignal>,
        val reasoning: String
    )

    data class ContextSignal(
        val name: String,
        val strength: Float,
        val description: String
    )

    data class Constraint(
        val type: ConstraintType,
        val description: String,
        val strength: Float
    )

    enum class ConstraintType {
        TIME, MODALITY, DETAIL, PRIVACY, RESOURCE, SAFETY, CONTEXT
    }

    data class AmbiguityAssessment(
        val isAmbiguous: Boolean,
        val ambiguityLevel: AmbiguityLevel,
        val topInterpretations: List<IntentInterpretation>,
        val confidenceGap: Float,
        val primaryAmbiguity: String?
    )

    enum class AmbiguityLevel { NONE, LOW, MEDIUM, HIGH }

    data class IntentInterpretation(
        val intent: IntentState,
        val confidence: Float,
        val source: InterpretationSource,
        val evidence: String
    )

    enum class InterpretationSource { EXPLICIT, INFERRED, CONFLICTING_PATTERNS, CONTEXTUAL }

    data class Unknown(
        val type: UnknownType,
        val description: String,
        val impact: Float
    )

    enum class UnknownType {
        REFERENT, VAGUE_ACTION, MISSING_PARAMETERS, MISSING_GOAL_CONTEXT,
        USER_IDENTITY, CAPABILITY_MATCH, TEMPORAL_CONTEXT, SPATIAL_CONTEXT
    }

    data class Entity(
        val id: String,
        val name: String,
        val type: EntityType
    )

    enum class EntityType { PERSON, LOCATION, OBJECT, CONCEPT, TIME, QUANTITY, UNKNOWN }
}