package com.jarvis.app.capability

/**
 * Real tool-routing / dispatch layer over the existing [CapabilityRegistry] (Stage 0).
 *
 * Same contract-now / real-closure-later template as ModelBackend: this defines the
 * routing contract plus a deterministic / rule-based backend now, so a future
 * learned router (e.g. Needle2) can slot in behind the same interface without
 * changing callers.
 *
 * The deterministic backend routes against the REAL registry (not a mocked or
 * duplicated capability list) via plain Kotlin dispatch rules.
 */
interface CapabilityRouter {

    /** A parsed request the router must map to a registered capability. */
    data class RouteRequest(
        val category: CapabilityRegistry.Category,
        val action: String = "",
        val language: String? = null,
        /** 0 for an exact category match; higher values tolerated by ranked results. */
        val minQuality: CapabilityRegistry.QualityTier = CapabilityRegistry.QualityTier.LOW,
        val maxRamMb: Long = Long.MAX_VALUE,
        val maxLatencyMs: Long = Long.MAX_VALUE,
        val requireHealthy: Boolean = true
    )

    sealed class RouteResult {
        data class Matched(
            val capability: CapabilityRegistry.Capability,
            val matchedBy: MatchTactic
        ) : RouteResult()

        data class NoMatch(
            val request: RouteRequest,
            val rankedCandidates: List<CapabilityRegistry.Capability>
        ) : RouteResult()

        data class Ambiguous(
            val request: RouteRequest,
            val candidates: List<CapabilityRegistry.Capability>
        ) : RouteResult()
    }

    enum class MatchTactic {
        EXACT_CATEGORY,
        FALLBACK_AFTER_CATEGORY
    }

    /** Route a request to the single best registered capability, or a ranked no-match/ambiguous result. */
    suspend fun route(request: RouteRequest): RouteResult
}
