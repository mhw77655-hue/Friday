package com.jarvis.app.memory.provenance

import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.VectorStore
import com.jarvis.app.trace.TurnTraceStore

/**
 * FORGET-PROPAGATION: a sink that can purge its durable content of a forgotten
 * memory. The only wired production implementation is [com.jarvis.app.memory.ConsolidationDaemon]
 * (the pending consolidation queue), so the forget turn's byte scan also covers
 * the queue artifact family.
 */
interface ForgetPurgeable {
    /** Remove every locally-held item whose text carries [needle]. Returns count removed. */
    fun purgeContaining(needle: String): Int
}

/** FORGET-PROPAGATION: outcome of one [MemoryForgetter.forget] call. */
data class ForgetResult(
    val sourceId: String?,
    val suppressed: Boolean,
    val derivedArtifactsHandled: Int
) {
    val completed: Boolean get() = !suppressed
}

/**
 * FORGET-PROPAGATION: the propagation engine behind "forget X" / "انسى X".
 *
 * [forget] tombstones a source memory, then propagates the tombstone through the
 * ProvenanceLedger to every derived artifact family using exactly what the ledger
 * recorded up front at the real creation points:
 *
 *  - SUMMARY: re-derived to its remaining sources when another source still feeds
 *    it (so sourcesOf() no longer lists the forgotten id — AC3), or dropped
 *    entirely (ledger redact) when the forgotten memory was its only source;
 *  - INDEX_ENTRY: the vector-index row is removed from the real VectorStore;
 *  - TRACE_RECORD / GRAPH_EDGE / CACHE_ENTRY / ADAPTER_BATCH: handled by the
 *    content sweep below (rewrites the trace file, expires graph nodes, purges
 *    the queue).
 *
 * Independent of the ledger, every content-bearing sink is swept of the forgotten
 * content (graph nodes, vector rows, trace text, pending-consolidation queue) so
 * a byte scan of every memory/index/cache/queue/trace artifact finds zero plaintext
 * after the turn (AC1/AC2).
 *
 * [propagationEnabled] is the AC5 negative control: disabled forget is accepted but
 * removes nothing, so the content remains discoverable by byte scan.
 *
 * All work is local. Calls reach no network or socket; the package-level
 * no-network grep stays satisfied by code and comments alike.
 */
class MemoryForgetter(
    private val tombstoneStore: TombstoneStore,
    private val ledger: ProvenanceLedger? = null,
    private val traceStore: TurnTraceStore? = null,
    private val graphStore: MemoryGraphStore? = null,
    private val vectorStore: VectorStore? = null,
    private val purgeables: List<ForgetPurgeable> = emptyList(),
    private var propagationEnabled: Boolean = true
) {

    val enabled: Boolean get() = propagationEnabled

    fun setPropagationEnabled(enabled: Boolean) {
        propagationEnabled = enabled
    }

    fun forget(sourceId: String, content: String): ForgetResult {
        if (!propagationEnabled) return ForgetResult(sourceId, suppressed = true, derivedArtifactsHandled = 0)
        tombstoneStore.tombstone(sourceId, content)
        var handled = 0
        if (ledger != null) {
            val derivedRefs = ledger.derivedFrom(sourceId)
            for (ref in derivedRefs) {
                when (ref.kind) {
                    ProvenanceKind.SUMMARY -> {
                        val remaining = ledger.sourcesOf(ref.derivedId) - sourceId
                        if (remaining.isEmpty()) {
                            ledger.redact(ref.derivedId)
                        } else {
                            ledger.redactSource(ref.derivedId, sourceId)
                        }
                        handled++
                    }
                    ProvenanceKind.INDEX_ENTRY -> {
                        vectorStore?.remove(ref.derivedId.removePrefix("index-"))
                        handled++
                    }
                    ProvenanceKind.TRACE_RECORD,
                    ProvenanceKind.GRAPH_EDGE,
                    ProvenanceKind.CACHE_ENTRY,
                    ProvenanceKind.ADAPTER_BATCH -> handled++
                }
            }
        }
        graphStore?.removeContaining(content)
        vectorStore?.removeContaining(content)
        traceStore?.redactContaining(content)
        purgeables.forEach { it.purgeContaining(content) }
        return ForgetResult(sourceId, suppressed = false, derivedArtifactsHandled = handled)
    }

    /**
     * Explicit "remember X": lift the tombstone for that content so the memory
     * can genuinely be re-learned on the next sub-threshold turn (AC4).
     */
    fun remember(content: String): Int = tombstoneStore.remember(content)

    /** Re-learning guard: does [text] carry any tombstoned content token (AC4)? */
    fun isTombstoned(text: String): Boolean = tombstoneStore.matchesAny(text)

    /** Exact-carrier guard: is the memory [sourceId] itself tombstoned? */
    fun isTombstonedId(sourceId: String): Boolean = tombstoneStore.isTombstoned(sourceId)

    companion object {
        /**
         * "forget X" in English (optionally prefixed with please/jarvis, and
         * accepting "forget about X") and "انسى X" / "انسي X" in Egyptian
         * Arabic → the target fragment, or null when the utterance is not a
         * forget directive.
         */
        fun forgetFragment(userText: String): String? {
            val text = userText.trim()
            val english = Regex("""^(?:please\s+)?(?:jarvis\s+)?forget\s+(?:about\s+)?(.+)$""", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.trim()
            if (!english.isNullOrBlank()) return english
            for (prefix in listOf("انسى", "انسي")) {
                if (text.startsWith(prefix)) {
                    val fragment = text.removePrefix(prefix).trim()
                    if (fragment.isNotBlank()) return fragment
                }
            }
            return null
        }

        /** "remember that X" / "remember X" in English → the content, or null. */
        fun rememberFragment(userText: String): String? {
            val text = userText.trim()
            return Regex("""^(?:please\s+)?remember\s+(?:that\s+)?(.+)$""", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}