package com.jarvis.app.voice

import com.jarvis.app.failure.FailureReport
import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.ResourceBudget
import com.jarvis.app.microsystem.CurrentResourceUsage
import com.jarvis.app.microsystem.MicroSystemContract
import com.jarvis.app.microsystem.MicroSystemHealth
import com.jarvis.app.microsystem.MicroSystemStatus
import com.jarvis.app.microsystem.SyncMessage
import com.jarvis.app.resource.ResourceRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * R2 — the voice organism as the nervous system sees it.
 *
 * Wraps [VoiceOrganismHost] in the [MicroSystemContract] so the habitat
 * lifecycle participates in the existing organism vocabulary (registry,
 * admission, failure surface, telemetry) without baking contract plumbing into
 * the host itself. The host stays a pure process supervisor; this adapter is
 * the capability wrapper.
 *
 * Capabilities declared truthfully: this milestone's habitat synthesizes speech
 * (voice_synthesis) and manages voice profiles — nothing else is claimed.
 */
class VoiceOrganismMicroSystem(
    private val host: VoiceOrganismHost
) : MicroSystemContract {

    override val id = "voice_organism"

    override val genome = Genome(
        id = id,
        capabilities = setOf("voice_synthesis", "voice_profile"),
        resourceBudget = ResourceBudget(maxMemoryMb = 350, maxCpuPercent = 35.0)
    )

    override val capabilities: Set<String> = genome.capabilities
    override val requiredCapabilities: Set<String> = emptySet()

    private val _health = MutableStateFlow(
        MicroSystemHealth(status = MicroSystemStatus.INITIALIZING)
    )
    override val health: StateFlow<MicroSystemHealth> = _health.asStateFlow()

    override val isReady: Boolean get() = host.state.value.state == VoiceOrganismHost.State.BOUND

    override val resourceRequirements = ResourceRequest(
        expectedMemoryMb = 350,
        expectedCpuPercent = 35.0,
        expectedRuntimeMs = 300,
        latencySensitive = true
    )

    private val _telemetry = MutableStateFlow<Map<String, Any>>(emptyMap())
    override val telemetry: StateFlow<Map<String, Any>> = _telemetry.asStateFlow()

    private val _currentResources = MutableStateFlow(
        CurrentResourceUsage(memoryMb = 350, cpuPercent = 35.0, lastActivityMs = System.currentTimeMillis())
    )
    override val currentResources: StateFlow<CurrentResourceUsage> = _currentResources.asStateFlow()

    private val _incoming = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 16)
    override val incoming: SharedFlow<SyncMessage> = _incoming.asSharedFlow()

    private val _outgoing = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 16)
    override val outgoing: SharedFlow<SyncMessage> = _outgoing.asSharedFlow()

    /** Route an incoming sync-fabric message; the habitat has no consumers yet. */
    override suspend fun deliver(message: SyncMessage) {
        _incoming.emit(message)
    }

    override suspend fun initialize() {
        host.start()
        host.state.first { it.state == VoiceOrganismHost.State.BOUND || it.state == VoiceOrganismHost.State.DISABLED }
        refreshHealth()
    }

    override suspend fun onPromoted() {
        refreshHealth()
    }

    override suspend fun shutdown() {
        host.shutdown()
    }

    override suspend fun checkHealth(): MicroSystemHealth = refreshHealth()

    override fun emit(message: SyncMessage) {
        // No sync-fabric consumers yet in this milestone; the emission is
        // acknowledged through telemetry so wiring appears in the report.
        _telemetry.value = _telemetry.value + ("last_sync" to message.topic)
    }

    override fun reportFailure(report: FailureReport) {
        host.onFailure(report)
    }

    // ── internal ──

    private fun refreshHealth(): MicroSystemHealth {
        val substrate = host.state.value
        val voice = host.voiceState.value
        val status = when {
            substrate.state == VoiceOrganismHost.State.DISABLED -> MicroSystemStatus.DISABLED
            substrate.state == VoiceOrganismHost.State.BOUND && voice == VoiceOrganismHost.VoiceState.READY ->
                MicroSystemStatus.HEALTHY
            substrate.state == VoiceOrganismHost.State.REBINDING -> MicroSystemStatus.DEGRADED
            substrate.state == VoiceOrganismHost.State.OFFLINE -> MicroSystemStatus.FAILING
            else -> MicroSystemStatus.INITIALIZING
        }
        val updated = _health.value.copy(
            status = status,
            uptimeMs = if (substrate.voicePid != null) System.currentTimeMillis() else 0,
            consecutiveFailures = substrate.restartCount
        )
        _health.value = updated
        _telemetry.value = mapOf(
            "substrate" to substrate.state.name,
            "voiceState" to voice.name,
            "voicePid" to (substrate.voicePid ?: -1),
            "restartCount" to substrate.restartCount,
            "pingLatencyMs" to (substrate.pingLatencyMs ?: -1L)
        )
        return updated
    }
}
