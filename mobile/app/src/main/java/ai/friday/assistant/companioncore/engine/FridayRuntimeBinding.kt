package ai.friday.assistant.companioncore.engine

/** Non-blocking, read-only source of Friday runtime observations. */
interface FridayRuntimeBinding {
    val label: String
    val schemaVersion: Int
    fun read(): FridayRuntimeSnapshot
    fun isHealthy(): Boolean
}

