package com.jarvis.app.body

/**
 * Minimal memory retrieval contract for subsystems that must not depend on the
 * Android-coupled [MemoryStore] (Context, disk I/O, persistence coroutines).
 *
 * The cognitive substrate (CognitiveEngine and friends) consumes this port for
 * pure keyword/tag retrieval and nothing else — no persistence side effects.
 * Keeping the boundary as an interface means the cognitive engine is
 * unit-testable with an in-memory fake and stays decoupled from Android.
 */
interface MemoryStorePort {
    /** Retrieve the most relevant memories for a query, best match first. */
    fun queryMemories(query: String, limit: Int = 20): List<MemoryItem>
}
