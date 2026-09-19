package com.jarvis.app.builder

import com.jarvis.app.evolution.Behavior
import com.jarvis.app.evolution.CapabilityRequirement

/**
 * Tool Specification (§15) — the declarative input to the Tool Builder:
 *
 *   «Create a system capable of X.»
 *
 * Carries the capability requirement, the resource envelope, and an optional
 * research hook so the builder can search cross-domain mechanisms when a
 * direct implementation is not obvious. It converts to the
 * [CapabilityRequirement] the candidate synthesizers consume — no LLM is
 * assumed anywhere in the path.
 */
data class ToolSpecification(
    val capability: String,
    val description: String,
    val behavior: Behavior,
    val constraints: List<String> = emptyList(),
    val maxMemoryMb: Long = 64,
    val maxCpuPercent: Double = 10.0,
    /** When true, the builder runs a cross-domain research sweep first (§7). */
    val researchFirst: Boolean = false,
    /** Only promote verified mechanisms when researchFirst is true (§7). */
    val requireVerifiedResearch: Boolean = false
) {
    fun toRequirement(): CapabilityRequirement = CapabilityRequirement(
        capability = capability,
        description = description,
        behavior = behavior,
        constraints = constraints,
        maxMemoryMb = maxMemoryMb,
        maxCpuPercent = maxCpuPercent
    )
}
