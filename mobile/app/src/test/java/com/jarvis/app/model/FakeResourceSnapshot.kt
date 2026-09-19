package com.jarvis.app.model

/**
 * Injectable fake [ResourceSnapshot] for deterministic governor tests.
 */
data class FakeResourceSnapshot(
    override val availableMemoryMb: Long,
    override val cpuLoadPercent: Double,
    override val thermalLevel: Int,
    override val batteryPercent: Int,
    override val isCharging: Boolean
) : ResourceSnapshot