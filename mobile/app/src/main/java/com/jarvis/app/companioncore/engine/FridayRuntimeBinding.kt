package com.jarvis.app.companioncore.engine

/** Non-blocking, read-only source of Friday runtime observations. */
interface FridayRuntimeBinding {
    val label: String
    val schemaVersion: Int
    fun read(): FridayRuntimeSnapshot
    fun isHealthy(): Boolean
}

/** Transitional source alias for existing pure test fixtures. */
@Deprecated("Use FridayRuntimeBinding; no Human Core binding exists in production.")
typealias HumanCoreBinding = FridayRuntimeBinding
