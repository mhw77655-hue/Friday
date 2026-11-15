package com.jarvis.app.onefriday

import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryNode

/**
 * Minimal in-memory graph used only by the JVM composition tests. The phone
 * always uses AndroidMemoryGraphStore; this class is never in app main sources.
 */
internal class FakeMemoryGraphStore : MemoryGraphStore {
    private val rows = mutableListOf<MemoryNode>()
    private var nextId = 0L

    @Synchronized
    override fun addFact(subject: String, predicate: String, `object`: String, source: String): String {
        val now = System.currentTimeMillis()
        rows.indices.forEach { index ->
            val old = rows[index]
            if (old.subject == subject && old.predicate == predicate && old.validUntil == null) {
                rows[index] = old.copy(validUntil = now)
            }
        }
        val id = "jvm-fake-${++nextId}"
        rows += MemoryNode(
            id = id,
            subject = subject,
            predicate = predicate,
            `object` = `object`,
            source = source,
            validFrom = now
        )
        return id
    }

    @Synchronized
    override fun query(subject: String?, predicate: String?, asOfTime: Long): List<MemoryNode> =
        rows.filter { node ->
            (subject == null || node.subject == subject) &&
                (predicate == null || node.predicate == predicate) &&
                node.validFrom <= asOfTime &&
                (node.validUntil == null || node.validUntil > asOfTime)
        }

    @Synchronized
    override fun getHistory(subject: String, predicate: String): List<MemoryNode> =
        rows.filter { it.subject == subject && it.predicate == predicate }.sortedBy { it.validFrom }

    @Synchronized
    override fun nodeCount(): Long = rows.size.toLong()

    @Synchronized
    override fun close() {
        rows.clear()
    }
}
