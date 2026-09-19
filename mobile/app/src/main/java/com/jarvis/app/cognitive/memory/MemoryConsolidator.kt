package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * MemoryConsolidator - Evaluates experience candidates and promotes them
 * to consolidated long-term memory.
 *
 * Pipeline: ExperienceRecord -> MemoryCandidate -> evaluate -> promote/reject/defer
 * -> ConsolidatedMemory
 */
class MemoryConsolidator(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val config: Config = Config(),
    private val onPromotion: (ConsolidatedMemory) -> Unit = {},
    private val onRejection: (MemoryCandidate, String) -> Unit = { _, _ -> },
    private val onConflict: (MemoryConflict) -> Unit = {}
) {

    data class Config(
        /** Minimum promotion score to promote */
        val promotionThreshold: Float = 0.6f,

        /** Maximum score to reject (below this = reject) */
        val rejectionThreshold: Float = 0.3f,

        /** Maximum candidates to evaluate per cycle */
        val maxEvaluationsPerCycle: Int = 10,

        /** Enable automatic evaluation on insert */
        val autoEvaluate: Boolean = true,

        /** Enable conflict detection during promotion */
        val detectConflicts: Boolean = true,

        /** Minimum time before re-evaluating deferred candidates (ms) */
        val reevaluationCooldownMs: Long = 300000, // 5 minutes

        /** Max candidates to hold in evaluation queue */
        val maxCandidateQueueSize: Int = 100
    )

    // Candidate queue for evaluation
    private val candidateQueue = ConcurrentHashMap<String, MemoryCandidate>()

    // Consolidated memories (by ID) — internal so the MemoryUpdateEngine and
    // ContinuityManager can mutate lifecycle state without a separate repository.
    internal val consolidatedMemories = ConcurrentHashMap<String, ConsolidatedMemory>()

    // Memories by type for conflict detection
    private val memoriesByType = ConcurrentHashMap<MemoryType, MutableList<String>>()

    // Statistics
    private val _stats = MutableStateFlow<Stats>(Stats())
    val statsFlow: StateFlow<Stats> = _stats.asStateFlow()

    private val insertCount = AtomicLong(0)
    private val promotionCount = AtomicLong(0)
    private val rejectionCount = AtomicLong(0)
    private val conflictCount = AtomicLong(0)

    init {
        initializeMemoryTypeIndex()
    }

    /** Submit an experience for evaluation and potential promotion */
    fun submitExperience(experience: ExperienceRecord): SubmitResult {
        if (candidateQueue.size >= config.maxCandidateQueueSize) {
            return SubmitResult.QUEUE_FULL
        }

        // Create candidate with initial promotion factors
        val candidate = evaluatePromotionFactors(experience)

        candidateQueue[candidate.experience.experienceId] = candidate
        insertCount.incrementAndGet()
        updateStats()

        if (config.autoEvaluate) {
            scope.launch { evaluateCandidate(candidate.experience.experienceId) }
        }

        return SubmitResult.ACCEPTED(candidate.experience.experienceId)
    }

    /** Submit multiple experiences */
    fun submitExperiences(experiences: List<ExperienceRecord>): List<SubmitResult> {
        return experiences.map { submitExperience(it) }
    }

    /** Evaluate promotion factors for an experience */
    private fun evaluatePromotionFactors(experience: ExperienceRecord): MemoryCandidate {
        val factors = PromotionFactors(
            recurrence = calculateRecurrence(experience),
            importance = experience.importance,
            userRelevance = calculateUserRelevance(experience),
            goalRelevance = calculateGoalRelevance(experience),
            futureUtility = calculateFutureUtility(experience),
            novelty = calculateNovelty(experience),
            reliability = experience.confidence,
            explicitInstruction = if (experience.source == ExperienceSource.USER_INTERACTION ||
                experience.source == ExperienceSource.CORRECTION ||
                experience.source == ExperienceSource.FEEDBACK) 1.0f else 0.0f,
            failureLearningValue = if (experience.failures.isNotEmpty()) 0.8f else 0.0f
        )

        val score = factors.computeScore()

        return MemoryCandidate(
            experience = experience,
            promotionFactors = factors,
            promotionScore = score,
            evaluated = false
        )
    }

    /** Calculate recurrence factor */
    private fun calculateRecurrence(experience: ExperienceRecord): Float {
        // Check how many similar experiences exist in consolidated memories
        var similarCount = 0
        for (mem in consolidatedMemories.values) {
            if (mem.memoryType == experience.candidateMemoryType &&
                calculateContentSimilarity(mem.content, experience.action.description) > 0.7f) {
                similarCount++
            }
        }
        // Normalize: 5+ similar = high recurrence (1.0)
        return (similarCount / 5f).coerceIn(0.0f, 1.0f)
    }

    /** Calculate user relevance */
    private fun calculateUserRelevance(experience: ExperienceRecord): Float {
        // High if from user interaction, correction, or feedback
        return when (experience.source) {
            ExperienceSource.USER_INTERACTION -> 1.0f
            ExperienceSource.CORRECTION -> 0.9f
            ExperienceSource.FEEDBACK -> 0.8f
            ExperienceSource.EXTERNAL_EVENT -> 0.3f
            else -> 0.5f
        }
    }

    /** Calculate goal relevance */
    private fun calculateGoalRelevance(experience: ExperienceRecord): Float {
        // If related to a goal, check goal importance
        return if (experience.relatedGoalId != null) {
            // In real implementation, would look up goal priority
            0.7f
        } else {
            0.2f
        }
    }

    /** Calculate future utility */
    private fun calculateFutureUtility(experience: ExperienceRecord): Float {
        // Heuristic based on memory type and action
        return when (experience.candidateMemoryType) {
            MemoryType.FACT -> 0.9f
            MemoryType.PREFERENCE -> 0.8f
            MemoryType.VOCABULARY -> 0.7f
            MemoryType.EPISODIC -> 0.5f
            MemoryType.CONTEXT -> 0.2f
        }
    }

    /** Calculate novelty */
    private fun calculateNovelty(experience: ExperienceRecord): Float {
        // Check if similar content already exists
        var maxSimilarity = 0.0f
        for (mem in consolidatedMemories.values) {
            if (mem.memoryType == experience.candidateMemoryType) {
                val similarity = calculateContentSimilarity(mem.content, experience.action.description)
                maxSimilarity = maxOf(maxSimilarity, similarity)
            }
        }
        return (1.0f - maxSimilarity).coerceIn(0.0f, 1.0f)
    }

    /** Simple content similarity (word overlap) */
    private fun calculateContentSimilarity(a: String, b: String): Float {
        val wordsA = a.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val wordsB = b.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0f
        val overlap = wordsA.intersect(wordsB).size
        return overlap.toFloat() / maxOf(wordsA.size, wordsB.size)
    }

    /** Evaluate a candidate by ID */
    fun evaluateCandidate(experienceId: String): EvaluationResult {
        val candidate = candidateQueue[experienceId] ?: return EvaluationResult.NOT_FOUND

        if (candidate.evaluated) {
            return EvaluationResult.ALREADY_EVALUATED(candidate.getPromotionDecision(
                config.promotionThreshold, config.rejectionThreshold
            ))
        }

        // Final evaluation with current state — decide from the score directly,
        // NOT via getPromotionDecision (which gates on the `evaluated` flag that
        // has not been set yet at this point).
        val decision = when {
            candidate.promotionScore >= config.promotionThreshold -> PromotionDecision.PROMOTE
            candidate.promotionScore < config.rejectionThreshold -> PromotionDecision.REJECT
            else -> PromotionDecision.DEFERRED
        }

        val evaluatedCandidate = candidate.copy(
            evaluated = true,
            evaluatedAt = System.currentTimeMillis(),
            evaluationReason = buildEvaluationReason(candidate, decision)
        )

        candidateQueue[experienceId] = evaluatedCandidate

        return when (decision) {
            PromotionDecision.PROMOTE -> {
                val consolidated = promoteToConsolidated(evaluatedCandidate)
                promotionCount.incrementAndGet()
                onPromotion(consolidated)
                candidateQueue.remove(experienceId)
                updateStats()
                EvaluationResult.PROMOTED(consolidated.memoryId)
            }
            PromotionDecision.REJECT -> {
                rejectionCount.incrementAndGet()
                onRejection(evaluatedCandidate, evaluatedCandidate.evaluationReason)
                candidateQueue.remove(experienceId)
                updateStats()
                EvaluationResult.REJECTED(evaluatedCandidate.evaluationReason)
            }
            PromotionDecision.DEFERRED -> {
                // Keep the evaluated candidate in the queue for later re-evaluation.
                candidateQueue[experienceId] = evaluatedCandidate.copy(
                    evaluationReason = "Deferred for re-evaluation: ${evaluatedCandidate.evaluationReason}"
                )
                updateStats()
                EvaluationResult.DEFERRED(evaluatedCandidate.evaluationReason)
            }
        }
    }

    /** Build human-readable evaluation reason */
    private fun buildEvaluationReason(candidate: MemoryCandidate, decision: PromotionDecision): String {
        val factors = candidate.promotionFactors
        val topFactors = mapOf(
            "recurrence" to factors.recurrence,
            "importance" to factors.importance,
            "userRelevance" to factors.userRelevance,
            "goalRelevance" to factors.goalRelevance,
            "futureUtility" to factors.futureUtility,
            "novelty" to factors.novelty,
            "reliability" to factors.reliability,
            "explicitInstruction" to factors.explicitInstruction,
            "failureLearningValue" to factors.failureLearningValue
        ).toList().sortedByDescending { it.second }.take(3)

        val factorStr = topFactors.map { "${it.first}=${String.format("%.2f", it.second)}" }.joinToString(", ")

        return when (decision) {
            PromotionDecision.PROMOTE -> "Promoted (score=${String.format("%.2f", candidate.promotionScore)}). Top factors: $factorStr"
            PromotionDecision.REJECT -> "Rejected (score=${String.format("%.2f", candidate.promotionScore)} < ${config.rejectionThreshold}). Top factors: $factorStr"
            PromotionDecision.DEFERRED -> "Deferred (score=${String.format("%.2f", candidate.promotionScore)} in [${config.rejectionThreshold}, ${config.promotionThreshold}]). Top factors: $factorStr"
        }
    }

    /** Promote candidate to consolidated memory */
    private fun promoteToConsolidated(candidate: MemoryCandidate): ConsolidatedMemory {
        val exp = candidate.experience

        // Create provenance
        val provenance = MemoryProvenance(
            originatingExperienceId = exp.experienceId,
            experienceSource = exp.source.name,
            experienceTimestamp = exp.timestamp,
            experienceConfidence = exp.confidence,
            derivationHistory = listOf(DerivationStep(
                stepType = DerivationStepType.INITIAL_CONSOLIDATION,
                description = "Initial consolidation from experience",
                confidenceBefore = exp.confidence,
                confidenceAfter = exp.confidence,
                source = "consolidator",
                metadata = mapOf("promotionScore" to candidate.promotionScore.toString())
            )),
            currentConfidence = exp.confidence
        )

        // Check for conflicts if enabled
        var conflictIds = emptyList<String>()
        if (config.detectConflicts) {
            val content = buildConsolidatedContent(exp)
            val conflicts = detectConflicts(content, exp.candidateMemoryType, provenance)
            if (conflicts.isNotEmpty()) {
                conflictIds = conflicts.map { it.conflictId }
                conflictCount.addAndGet(conflicts.size.toLong())
            }
        }

        val consolidated = ConsolidatedMemory(
            content = buildConsolidatedContent(exp),
            memoryType = exp.candidateMemoryType,
            lifecycleState = MemoryLifecycleState.CONSOLIDATED,
            provenance = provenance,
            tags = exp.tags,
            confidence = exp.confidence,
            relevance = 0.5f,
            goalAlignment = if (exp.relatedGoalId != null) 0.7f else 0.0f,
            uncertainty = 1.0f - exp.confidence,
            source = "consolidation",
            conflictIds = conflictIds,
            metadata = mapOf(
                "experienceId" to exp.experienceId,
                "promotionScore" to candidate.promotionScore.toString()
            )
        )

        // Store
        consolidatedMemories[consolidated.memoryId] = consolidated
        memoriesByType.getOrPut(exp.candidateMemoryType) { mutableListOf() }.add(consolidated.memoryId)

        return consolidated
    }

    /** Build consolidated content from experience */
    private fun buildConsolidatedContent(exp: ExperienceRecord): String {
        val sb = StringBuilder()
        sb.append(exp.action.description)
        if (exp.result.output != null) {
            sb.append(" -> ").append(exp.result.output)
        }
        if (exp.observations.isNotEmpty()) {
            sb.append(" [Observations: ").append(exp.observations.joinToString("; ")).append("]")
        }
        return sb.toString()
    }

    /** Detect conflicts with existing memories */
    private fun detectConflicts(
        content: String,
        memoryType: MemoryType,
        provenance: MemoryProvenance
    ): List<MemoryConflict> {
        val conflicts = mutableListOf<MemoryConflict>()

        for (mem in consolidatedMemories.values) {
            if (mem.memoryType != memoryType) continue
            if (mem.isCurrentTruth() && calculateContentSimilarity(mem.content, content) > 0.8f) {
                // Potential conflict - same type, high similarity, both claim to be current
                val conflict = MemoryConflict(
                    memoryAId = mem.memoryId,
                    memoryBId = "new_${System.currentTimeMillis()}",
                    memoryAContent = mem.content,
                    memoryBContent = content,
                    conflictType = ConflictType.CONTENT_CONTRADICTION,
                    evidenceA = mem.provenance.derivationHistory.map { it.description },
                    evidenceB = listOf("New experience: ${provenance.originatingExperienceId}"),
                    timestampA = mem.createdAt,
                    timestampB = provenance.experienceTimestamp,
                    confidenceA = mem.confidence,
                    confidenceB = provenance.currentConfidence,
                    sourceReliabilityA = mem.provenance.sourceReliability,
                    sourceReliabilityB = provenance.sourceReliability
                )
                conflicts.add(conflict)
                onConflict(conflict)
            }
        }

        return conflicts
    }

    /** Initialize memory type index */
    private fun initializeMemoryTypeIndex() {
        for (type in MemoryType.values()) {
            memoriesByType[type] = mutableListOf()
        }
    }

    /** Get a consolidated memory by ID */
    fun getMemory(memoryId: String): ConsolidatedMemory? = consolidatedMemories[memoryId]

    /** Get all consolidated memories of a type */
    fun getMemoriesByType(type: MemoryType): List<ConsolidatedMemory> {
        return memoriesByType[type]?.mapNotNull { consolidatedMemories[it] } ?: emptyList()
    }

    /** Get all current truth memories */
    fun getCurrentTruths(): List<ConsolidatedMemory> {
        return consolidatedMemories.values.filter { it.isCurrentTruth() }.toList()
    }

    /** Get all historical (superseded) memories */
    fun getHistoricalMemories(): List<ConsolidatedMemory> {
        return consolidatedMemories.values.filter { it.isHistorical() }.toList()
    }

    /** Get all conflicted memories */
    fun getConflictedMemories(): List<ConsolidatedMemory> {
        return consolidatedMemories.values.filter { it.hasUnresolvedConflict() }.toList()
    }

    /** Re-evaluate deferred candidates */
    fun reevaluateDeferred(): Int {
        var count = 0
        val now = System.currentTimeMillis()
        for ((id, candidate) in candidateQueue) {
            if (!candidate.evaluated ||
                (candidate.evaluated && candidate.getPromotionDecision(config.promotionThreshold, config.rejectionThreshold) == PromotionDecision.DEFERRED &&
                 now - candidate.evaluatedAt > config.reevaluationCooldownMs)) {
                evaluateCandidate(id)
                count++
            }
        }
        return count
    }

    /** Get candidate queue snapshot */
    fun getCandidateQueue(): List<MemoryCandidate> = candidateQueue.values.toList()

    /** Get all consolidated memories */
    fun getAllConsolidated(): List<ConsolidatedMemory> = consolidatedMemories.values.toList()

    /**
     * Restore consolidated memories loaded from persistence (restart continuity).
     * Rebuilds the in-memory type index so retrieval works after a restart.
     */
    fun restoreAll(memories: List<ConsolidatedMemory>) {
        for (mem in memories) {
            consolidatedMemories[mem.memoryId] = mem
            memoriesByType.getOrPut(mem.memoryType) { mutableListOf() }.add(mem.memoryId)
        }
        updateStats()
    }

    /** Persist the current consolidated store through the given adapter. */
    fun persistTo(persistence: ContinuityPersistence): ContinuityPersistence.PersistResult =
        persistence.save(getAllConsolidated())

    /** Load consolidated memories from the given adapter into this store. */
    fun loadFrom(persistence: ContinuityPersistence): Int {
        val memories = persistence.load()
        if (memories.isNotEmpty()) restoreAll(memories)
        return memories.size
    }

    /** Update statistics */
    private fun updateStats() {
        _stats.value = Stats(
            queuedCandidates = candidateQueue.size,
            consolidatedMemories = consolidatedMemories.size,
            totalSubmissions = insertCount.get(),
            totalPromotions = promotionCount.get(),
            totalRejections = rejectionCount.get(),
            totalConflicts = conflictCount.get(),
            currentTruthCount = consolidatedMemories.values.count { it.isCurrentTruth() },
            historicalCount = consolidatedMemories.values.count { it.isHistorical() },
            conflictedCount = consolidatedMemories.values.count { it.hasUnresolvedConflict() }
        )
    }

    /** Get current stats */
    fun getStats(): Stats = _stats.value

    /** Result types */
    sealed interface SubmitResult {
        data class ACCEPTED(val experienceId: String) : SubmitResult
        object QUEUE_FULL : SubmitResult
    }

    sealed interface EvaluationResult {
        data class PROMOTED(val memoryId: String) : EvaluationResult
        data class REJECTED(val reason: String) : EvaluationResult
        data class DEFERRED(val reason: String) : EvaluationResult
        object NOT_FOUND : EvaluationResult
        data class ALREADY_EVALUATED(val decision: PromotionDecision) : EvaluationResult
    }

    data class Stats(
        val queuedCandidates: Int = 0,
        val consolidatedMemories: Int = 0,
        val totalSubmissions: Long = 0,
        val totalPromotions: Long = 0,
        val totalRejections: Long = 0,
        val totalConflicts: Long = 0,
        val currentTruthCount: Int = 0,
        val historicalCount: Int = 0,
        val conflictedCount: Int = 0
    )
}