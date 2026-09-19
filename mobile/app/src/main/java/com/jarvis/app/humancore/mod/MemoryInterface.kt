package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.protocol.Exchange
import com.jarvis.app.humancore.protocol.IntegrationContext

/**
 * The Memory Interaction Interface (§16) — the ONLY sanctioned bridge between
 * the Human Core and the Memory subsystem.
 *
 * The Human Core does NOT own world memory (facts that would be true even if
 * JARVIS and the user had never met — §2). It annotates and tags what the
 * Memory subsystem stores, and it reads back what it needs. Everything the
 * Human Core produces that is relationship-relevant lives in the Human Core's
 * own stores; everything world-relevant is delegated here.
 *
 * This interface deliberately does NOT declare write access to a memory
 * store — it emits annotation *requests*. The owner of the actual memory
 * storage (currently the app's Obsidian vault via [com.jarvis.app.ObsidianSync];
 * a dedicated memory subsystem later) subscribes to those requests and is the
 * only party that persists anything (§16).
 *
 * Failure handling (§16): if no memory subsystem is attached, annotation
 * requests are simply dropped — the Human Core never depends on memory being
 * present, and never fakes a memory that wasn't stored (§20).
 */
class MemoryInterface {

    /** Functional sink that the app wires to its world-memory writer. */
    var annotationSink: ((MemoryAnnotation) -> Unit)? = null

    /**
     * Called by the Integration Pass for each completed exchange. Produces
     * relationship-tagged annotation requests for the world-memory side.
     */
    fun annotateExchange(exchange: Exchange, ctx: IntegrationContext) {
        val sink = annotationSink ?: return
        val tags = tagsFor(ctx)
        if (tags.isEmpty()) return
        // Annotation is a request, never a memory assertion: the sink decides
        // whether and where to persist (§16).
        sink(
            MemoryAnnotation(
                userText = exchange.userText.take(500),
                jarvisText = exchange.styled.outboundText.take(500),
                ts = exchange.ts,
                tags = tags
            )
        )
    }

    /**
     * Relationship-context tags derived purely from Human Core state. These
     * are what let world-memory entries later be recalled with their human
     * context ("shared with JARVIS while the user was stressed").
     */
    fun tagsFor(ctx: IntegrationContext): List<String> {
        val tags = mutableListOf<String>()
        val s = ctx.affect?.signals ?: emptyMap()
        if ((s["stress"] ?: 0.0) >= 0.5) tags += "user_stressed"
        if ((s["frustration"] ?: 0.0) >= 0.5) tags += "user_frustrated"
        if ((s["gratitude"] ?: 0.0) >= 0.4) tags += "user_grateful"
        if (ctx.vulnerabilityShared) tags += "user_vulnerable"
        if (ctx.deviation?.significant == true) tags += "deviation_from_baseline"
        if (ctx.mentionsJarvis) tags += "about_jarvis"
        return tags
    }
}

/** One memory-annotation request emitted by the Human Core (§16). */
data class MemoryAnnotation(
    val userText: String,
    val jarvisText: String,
    val ts: Long,
    val tags: List<String>
)
