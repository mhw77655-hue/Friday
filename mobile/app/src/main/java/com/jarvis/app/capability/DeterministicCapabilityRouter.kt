package com.jarvis.app.capability

/**
 * Deterministic / rule-based [CapabilityRouter] over the REAL [CapabilityRegistry].
 *
 * Dispatch rules:
 *  1. Ask the registry for the best healthy, available capability matching the
 *     requested category (and language/quality/RAM/latency constraints).
 *  2. If none is available for the category, walk the registry's fallback chain
 *     (getFallbackChain) and pick the first healthy, available member.
 *  3. If multiple candidates have the same top confidence * quality score, the
 *     result is AMBIGUOUS (ranked candidates returned).
 *  4. If nothing matches at all, NO_MATCH returns the registry's healthy
 *     candidates ranked by confidence * quality.
 *
 * No model weights and no native loading are involved. This is the
 * deterministic default; a learned router may substitute later behind the same
 * [CapabilityRouter] interface.
 */
class DeterministicCapabilityRouter(
    private val registry: CapabilityRegistry
) : CapabilityRouter {

    override suspend fun route(request: CapabilityRouter.RouteRequest): CapabilityRouter.RouteResult {
        val exact = registry.findBest(
            category = request.category,
            language = request.language,
            minQuality = request.minQuality,
            maxRamMb = request.maxRamMb,
            maxLatencyMs = request.maxLatencyMs,
            requireHealthy = request.requireHealthy
        )
        if (exact != null) {
            if (isAmbiguous(request, exact)) {
                return CapabilityRouter.RouteResult.Ambiguous(request, rankedCandidates(request))
            }
            return CapabilityRouter.RouteResult.Matched(exact, CapabilityRouter.MatchTactic.EXACT_CATEGORY)
        }

        // No healthy available primary for the category. Try each category member's
        // declared fallback chain (spanning other categories) and pick the first
        // healthy, available fallback.
        val fallback = registry.getByCategory(request.category)
            .flatMap { registry.getFallbackChain(it.id) }
            .distinctBy { it.id }
            .filter { !request.requireHealthy || it.health == CapabilityRegistry.Health.HEALTHY }
            .sortedByDescending { it.confidence * it.quality.ordinal.toFloat() }
            .firstOrNull()
        if (fallback != null) {
            return CapabilityRouter.RouteResult.Matched(fallback, CapabilityRouter.MatchTactic.FALLBACK_AFTER_CATEGORY)
        }

        val anyHealthy = registry.getHealthy()
            .sortedByDescending { it.confidence * it.quality.ordinal.toFloat() }
        if (anyHealthy.isEmpty()) {
            return CapabilityRouter.RouteResult.NoMatch(request, emptyList())
        }

        return CapabilityRouter.RouteResult.NoMatch(request, rankedCandidates(request))
    }

    private fun isAmbiguous(request: CapabilityRouter.RouteRequest, chosen: CapabilityRegistry.Capability): Boolean {
        val sameCategory = registry.getByCategory(request.category)
            .filter { !request.requireHealthy || it.health == CapabilityRegistry.Health.HEALTHY }
        val topScore = chosen.confidence * chosen.quality.ordinal.toFloat()
        val tied = sameCategory.count { it.confidence * it.quality.ordinal.toFloat() == topScore }
        return tied > 1
    }

    private fun rankedCandidates(request: CapabilityRouter.RouteRequest): List<CapabilityRegistry.Capability> {
        return registry.getAll()
            .filter { !request.requireHealthy || it.health == CapabilityRegistry.Health.HEALTHY }
            .sortedByDescending { it.confidence * it.quality.ordinal.toFloat() }
    }

    /** Ranked healthy candidates across the whole registry, for no-match reporting. */
    fun rankedNoMatchCandidates(): List<CapabilityRegistry.Capability> {
        return registry.getHealthy()
            .sortedByDescending { it.confidence * it.quality.ordinal.toFloat() }
    }
}
