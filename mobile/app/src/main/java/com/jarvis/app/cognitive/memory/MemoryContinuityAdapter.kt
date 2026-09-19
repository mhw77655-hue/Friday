package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.body.MemoryType
import com.jarvis.app.cognitive.WorkingMemory
import com.jarvis.app.cognitive.ActiveMemory
import com.jarvis.app.cognitive.MemorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * MemoryContinuityAdapter - Bridges existing MemoryStore with the new
 * Cognitive Continuity system.
 *
 * This adapter:
 * 1. Observes MemoryStore writes and creates ExperienceRecords
 * 2. Submits experiences to MemoryConsolidator for evaluation
 * 3. Provides a MemoryStorePort that reads from ContinuityManager
 * 4. Ensures memory survives process restart via existing persistence
 */
class MemoryContinuityAdapter(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val memoryStore: MemoryStorePort,
    private val consolidator: MemoryConsolidator,
    private val updateEngine: MemoryUpdateEngine,
    private val continuityManager: ContinuityManager,
    private val workingMemory: WorkingMemory,
    private val config: Config = Config()
) : MemoryStorePort {

    data class Config(
        /** Automatically create experiences from MemoryStore writes */
        val autoCreateExperiences: Boolean = true,

        /** Minimum relevance for experience creation */
        val minExperienceRelevance: Float = 0.5f,

        /** Whether to populate working memory from continuity reconstruction */
        val useContinuityForWorkingMemory: Boolean = true,

        /** Max memories to pull from continuity for working memory */
        val maxContinuityMemories: Int = 20
    )

    /** Process a MemoryStore write event and create experience if needed */
    fun onMemoryStored(item: MemoryItem, operation: StoreOperation) {
        if (!config.autoCreateExperiences) return

        // Only create experiences for significant writes
        if (item.relevance < config.minExperienceRelevance && operation != StoreOperation.PROMOTE_TO_EPISODIC) {
            return
        }

        val experience = createExperienceFromStore(item, operation)
        consolidator.submitExperience(experience)
    }

    /** Create an ExperienceRecord from a MemoryStore item */
    private fun createExperienceFromStore(item: MemoryItem, operation: StoreOperation): ExperienceRecord {
        val source = when (operation) {
            StoreOperation.STORE_FACT -> ExperienceSource.USER_INTERACTION
            StoreOperation.STORE_PREFERENCE -> ExperienceSource.USER_INTERACTION
            StoreOperation.STORE_EPISODIC -> ExperienceSource.COGNITIVE_CYCLE
            StoreOperation.PROMOTE_TO_EPISODIC -> ExperienceSource.COGNITIVE_CYCLE
            StoreOperation.STORE_CONVERSATION -> ExperienceSource.USER_INTERACTION
            StoreOperation.VOCABULARY_LEARNED -> ExperienceSource.USER_INTERACTION
        }

        return ExperienceRecord(
            experienceId = "exp_store_${System.currentTimeMillis()}_${item.id}",
            timestamp = item.timestamp,
            source = source,
            context = ExperienceContext(),
            relatedEntities = item.tags.map { tag ->
                RelatedEntity(
                    entityId = "tag_$tag",
                    name = tag,
                    type = EntityType.CONCEPT,
                    role = EntityRole.CONTEXT
                )
            },
            action = ExperienceAction(
                actionType = when (operation) {
                    StoreOperation.STORE_FACT -> ActionType.COMMUNICATE
                    StoreOperation.STORE_PREFERENCE -> ActionType.COMMUNICATE
                    StoreOperation.STORE_EPISODIC -> ActionType.OBSERVE
                    StoreOperation.PROMOTE_TO_EPISODIC -> ActionType.LEARN
                    StoreOperation.STORE_CONVERSATION -> ActionType.COMMUNICATE
                    StoreOperation.VOCABULARY_LEARNED -> ActionType.LEARN
                },
                description = "Memory stored: ${item.content.take(100)}",
                parameters = mapOf("operation" to operation.name, "memoryType" to item.type.name)
            ),
            result = ExperienceResult(
                outcome = Outcome.SUCCESS,
                output = item.content
            ),
            evidence = listOf(Evidence(
                evidenceType = EvidenceType.SYSTEM_LOG,
                description = "MemoryStore $operation: ${item.id}",
                source = "MemoryStore",
                strength = 0.9f
            )),
            confidence = item.relevance,
            importance = when (item.type) {
                MemoryType.FACT -> 0.9f
                MemoryType.PREFERENCE -> 0.8f
                MemoryType.VOCABULARY -> 0.7f
                MemoryType.EPISODIC -> 0.6f
                MemoryType.CONTEXT -> 0.3f
            },
            tags = item.tags,
            candidateMemoryType = item.type
        )
    }

    /** Query memories using continuity reconstruction (implements MemoryStorePort) */
    override fun queryMemories(query: String, limit: Int): List<MemoryItem> {
        if (!config.useContinuityForWorkingMemory) {
            return memoryStore.queryMemories(query, limit)
        }

        // Use continuity manager for richer reconstruction
        val continuityState = runBlocking {
            continuityManager.reconstructCurrentState(query)
        }

        // Convert active truths to MemoryItems
        val items = mutableListOf<MemoryItem>()

        for (truth in continuityState.activeTruths.take(limit)) {
            items.add(MemoryItem(
                id = truth.memoryId,
                type = truth.memoryType,
                content = truth.content,
                timestamp = truth.lastUpdated,
                tags = truth.tags,
                relevance = truth.confidence * truth.relevance
            ))
        }

        // If not enough, supplement with direct store query
        if (items.size < limit) {
            val storeItems = memoryStore.queryMemories(query, limit - items.size)
            for (storeItem in storeItems) {
                if (!items.any { it.id == storeItem.id }) {
                    items.add(storeItem)
                }
            }
        }

        return items.take(limit)
    }

    /** Populate working memory from continuity reconstruction */
    fun populateWorkingMemoryFromContinuity(
        query: String,
        currentGoal: String? = null,
        activeEntities: List<String> = emptyList()
    ) {
        val continuityState = runBlocking {
            continuityManager.reconstructCurrentState(
                query = query,
                currentGoal = currentGoal,
                activeEntities = activeEntities
            )
        }

        for (truth in continuityState.activeTruths.take(config.maxContinuityMemories)) {
            val memType = when (truth.memoryType) {
                MemoryType.FACT -> MemoryType.FACT
                MemoryType.PREFERENCE -> MemoryType.PREFERENCE
                MemoryType.VOCABULARY -> MemoryType.VOCABULARY
                MemoryType.EPISODIC -> MemoryType.EPISODIC
                MemoryType.CONTEXT -> MemoryType.CONTEXT
            }

            val source = when (truth.memoryType) {
                MemoryType.FACT -> MemorySource.FACT
                MemoryType.PREFERENCE -> MemorySource.PREFERENCE
                MemoryType.VOCABULARY -> MemorySource.VOCABULARY
                MemoryType.EPISODIC -> MemorySource.EPISODIC
                MemoryType.CONTEXT -> MemorySource.CONTEXT
            }

            workingMemory.insertFromBodyMemory(
                memoryItem = MemoryItem(
                    id = truth.memoryId,
                    type = memType,
                    content = truth.content,
                    timestamp = truth.lastUpdated,
                    tags = truth.tags,
                    relevance = truth.relevance
                ),
                source = source,
                initialActivation = 0.5f + truth.confidence * 0.3f,
                goalAlignment = truth.goalAlignment,
                relevance = truth.relevance,
                uncertainty = truth.uncertainty
            )
        }
    }

    /** Get the underlying memory store for direct access */
    fun getMemoryStore(): MemoryStorePort = memoryStore

    /** Get the consolidator for direct access */
    fun getConsolidator(): MemoryConsolidator = consolidator

    /** Get the continuity manager for direct access */
    fun getContinuityManager(): ContinuityManager = continuityManager
}

/** MemoryStore operations that trigger experience creation */
enum class StoreOperation {
    STORE_FACT,
    STORE_PREFERENCE,
    STORE_EPISODIC,
    PROMOTE_TO_EPISODIC,
    STORE_CONVERSATION,
    VOCABULARY_LEARNED
}