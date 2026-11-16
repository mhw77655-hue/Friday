package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.engine.FridayRuntimeBinding
import com.jarvis.app.companioncore.engine.FridayRuntimeSnapshot

/** Deterministic in-memory Friday runtime binding for integration tests. */
class FakeBinding(
    var state: FridayRuntimeSnapshot = FridayRuntimeSnapshot.empty(1_700_000_000_000L),
    var healthy: Boolean = true
) : FridayRuntimeBinding {
    override val label: String = "fake-friday-runtime"
    override val schemaVersion: Int = 1
    override fun read(): FridayRuntimeSnapshot = state
    override fun isHealthy(): Boolean = healthy
}
