package com.jarvis.app.cognitive.capability

/**
 * Structured metadata describing a capability. Purely declarative — no
 * implementation logic, no mutable runtime state. The fabric addresses a
 * capability by its stable [id]; everything else is for discovery, resolution,
 * permission-checking, and resource gating.
 */
data class CapabilityDescriptor(
    /** Stable identity. Must be unique in the registry and usable as an immune
     *  subsystem id in DependencyGraph / ContainmentRegistry. */
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0",
    val category: String,
    /** Permission tokens this capability requires to be invoked. */
    val requiredPermissions: Set<CapabilityPermission> = emptySet(),
    /** Ids of other capabilities this capability depends on. */
    val dependencies: Set<String> = emptySet(),
    val resourceProfile: CapabilityResourceProfile = CapabilityResourceProfile(),
    val riskLevel: RiskLevel = RiskLevel.LOW,
    /** The capability-agnostic action verbs this capability can perform. */
    val supportedOperations: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val availability: Availability = Availability.ONLINE
)

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

enum class Availability { ONLINE, DEGRADED, OFFLINE }

/** Expected resource characteristics (conceptual link to ResourceGovernor /
 *  ResourceState — no live telemetry in this milestone). */
data class CapabilityResourceProfile(
    /** Estimated CPU utilization 0..1. */
    val cpuCost: Float = 0.1f,
    /** Estimated memory footprint. */
    val memoryCostMb: Long = 0,
    val thermalCost: ThermalCost = ThermalCost.NONE,
    val latencyClass: LatencyClass = LatencyClass.MEDIUM,
    /** Optional capabilities may be paused/dropped under pressure; critical
     *  capabilities are never silently dropped. */
    val optional: Boolean = true
) {
    /** A coarse "expensive" classification for resource gating. */
    val expensive: Boolean
        get() = cpuCost >= 0.6f || memoryCostMb >= 512 || latencyClass == LatencyClass.HIGH

    /** A capability is critical exactly when it is not optional — the two are a
     *  single binary classification (the directive's optional/critical axis). */
    val critical: Boolean
        get() = !optional
}

enum class ThermalCost { NONE, LOW, MEDIUM, HIGH }

enum class LatencyClass { LOW, MEDIUM, HIGH, UNBOUNDED }

/** Minimal permission seam. The fabric carries tokens; the invocation boundary
 *  verifies them. Deliberately tiny — future capabilities extend this set. */
enum class CapabilityPermission { READ, WRITE, EXECUTE, NETWORK, SYSTEM, USER_DATA }
