package ai.friday.assistant.resolution

import ai.friday.assistant.capability.CapabilityRegistry
import ai.friday.assistant.memory.MemoryGraphStore

/**
 * Writes capability declarations into Galaxy Memory so the fuzzy command
 * resolver can find commandable targets through the REAL retrieval engine
 * ([ai.friday.assistant.memory.BlendedMemoryRetriever]) instead of string matching.
 *
 * Each capability becomes an ordinary memory fact:
 *  - subject   = the capability id
 *  - predicate = [FuzzyCommandResolver.CAPABILITY_PREDICATE]
 *  - object    = the capability's searchable description (anything a user
 *    would naturally say to invoke it)
 *  - source    = "capability-index"
 *
 * [ai.friday.assistant.memory.RankedMemory] nodes are seeded/ranked on the object
 * description text, so an underspecified reference ("the one that goes like...")
 * surfaces the right capability even when it shares no literal token with the
 * capability id or name. The index adds zero domain logic — any capability of
 * any category is indexed identically, which is what keeps resolution
 * cross-domain in [FuzzyCommandResolver].
 *
 * Storage is the real Galaxy Memory graph ([MemoryGraphStore]); on the JVM
 * tests use the pure-Kotlin reference store, in the APK this is
 * [ai.friday.assistant.memory.AndroidMemoryGraphStore].
 */
class CapabilityMemoryIndex(private val graphStore: MemoryGraphStore) {

    /**
     * Index [capability] under [description]. Re-indexing the same capability
     * supersedes its previous description (the store's normal bi-temporal
     * supersession — the old entry stays in history, the new one is the only
     * valid one).
     */
    fun indexCapability(
        capability: CapabilityRegistry.Capability,
        description: String
    ) {
        graphStore.addFact(
            subject = capability.id,
            predicate = FuzzyCommandResolver.CAPABILITY_PREDICATE,
            `object` = description,
            source = "capability-index"
        )
    }
}