package com.jarvis.app.organism

import com.jarvis.app.microsystem.MicroSystemContract
import com.jarvis.app.microsystem.MicroSystemStatus

/**
 * The four explicit manifests every organism must expose so the nervous
 * system can ask:
 *
 *   «What are you?»            → [CapabilityManifest]
 *   «What can you do?»         → [CapabilityManifest.capabilities]
 *   «Are you healthy?»         → [HealthManifest]
 *   «What resources do you need?» → [ResourceManifest]
 *   «What failed? Can you recover?» → [FailureContract]
 *
 * These are the vocabulary of the federation layer. The organism never
 * performs the nervous system's work — it only answers.
 */

/** Answers «What are you? / What can you do?» — identity + version + lineage. */
data class CapabilityManifest(
    val organismId: String,
    val version: Int,
    val lineage: List<String>,
    val capabilities: Set<String>,
    val requiredCapabilities: Set<String>,
    val description: String = ""
)

/** Answers «Are you healthy? Can you operate in degraded mode?» */
data class HealthManifest(
    val organismId: String,
    val status: String,
    val ready: Boolean,
    val uptimeMs: Long,
    val consecutiveFailures: Int,
    val lastError: String?,
    val degradedMode: Boolean
)

/** Answers «What resources do you need?» — the admission-control declaration. */
data class ResourceManifest(
    val organismId: String,
    val expectedMemoryMb: Long,
    val expectedCpuPercent: Double,
    val expectedRuntimeMs: Long,
    val priority: Int,
    val latencySensitive: Boolean
)

/** Answers «What failed? / Can you recover? / Can another replace you?» */
data class FailureContract(
    val organismId: String,
    val recoverability: String,
    val recoveryActions: List<String>,
    /** §11: a candidate that fails silently is automatically invalid. */
    val silentFailureInvalid: Boolean = true
)

/** All four manifests in one immutable snapshot. */
data class OrganismManifests(
    val capability: CapabilityManifest,
    val health: HealthManifest,
    val resource: ResourceManifest,
    val failure: FailureContract
)

/**
 * Default snapshot derivation for every [MicroSystemContract]. Adding a
 * default member means existing organisms (and tests) keep compiling while
 * gaining the manifest surface the nervous system queries.
 */
fun MicroSystemContract.manifests(): OrganismManifests = OrganismManifests(
    capability = CapabilityManifest(
        organismId = id,
        version = version,
        lineage = genome.parentLineage,
        capabilities = capabilities,
        requiredCapabilities = requiredCapabilities,
        description = genome.provenance.lastMutation
    ),
    health = HealthManifest(
        organismId = id,
        status = health.value.status.name,
        ready = isReady,
        uptimeMs = health.value.uptimeMs,
        consecutiveFailures = health.value.consecutiveFailures,
        lastError = health.value.lastError,
        degradedMode = health.value.status == MicroSystemStatus.DEGRADED
    ),
    resource = ResourceManifest(
        organismId = id,
        expectedMemoryMb = resourceRequirements.expectedMemoryMb,
        expectedCpuPercent = resourceRequirements.expectedCpuPercent,
        expectedRuntimeMs = resourceRequirements.expectedRuntimeMs,
        priority = resourceRequirements.priority,
        latencySensitive = resourceRequirements.latencySensitive
    ),
    failure = FailureContract(
        organismId = id,
        recoverability = "RETRYABLE",
        recoveryActions = listOf("RETRY", "FALLBACK"),
        silentFailureInvalid = true
    )
)
