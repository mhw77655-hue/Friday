package com.friday.app.companioncore

import com.friday.app.companioncore.engine.HumanCoreBinding
import com.friday.app.companioncore.engine.RawCoreState

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
