package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.engine.HumanCoreBinding
import com.jarvis.app.companioncore.engine.RawCoreState

/** Deterministic in-memory binding for integration tests. */
class FakeBinding(
    var state: RawCoreState = RawCoreState.empty(1_700_000_000_000L),
    var healthy: Boolean = true
) : HumanCoreBinding {
    override val label: String = "fake"
    override val schemaVersion: Int = 1
    override fun read(): RawCoreState = state
    override fun isHealthy(): Boolean = healthy
}
