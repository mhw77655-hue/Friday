package com.jarvis.app.resolution

import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.capability.CapabilityRouter
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.RankedMemory

/**
 * Fuzzy command resolution generalized across domains.
 *
 * Given an underspecified reference ("the one that goes like...", "that chat
 * from yesterday", "resume the tune"), this layer retrieves RANKED candidates
 * from the REAL Galaxy Memory via [BlendedMemoryRetriever] — binary-quantized
 * Hamming seeding + weighted graph traversal + salience ranking — and hands
 * the winning candidate to the REAL [CapabilityRouter] for final dispatch.
 * It never string-matches a capability name/title, never keeps a per-domain
 * lookup table, and contains zero domain logic: voice, messaging, media
 * continuity, screen/session resumption all flow through this one path.
 *
 * Candidate surface: only Galaxy Memory facts written by [CapabilityMemoryIndex]
 * (predicate == [CAPABILITY_PREDICATE]) are routable. Any other memory (a user's
 * chat, playlist, note) may rank higher in retrieval but carries no capability
 * target and is ignored by the resolver. Ambiguity is never guessed away: when
 * the top-ranked candidate's [CapabilityRouter.RouteResult] is AMBIGUOUS, the
 * resolution stays [ResolveOutcome.Unresolved] with
 * [UnresolvedReason.ROUTER_AMBIGUOUS].
 */
class FuzzyCommandResolver(
    private val blendedRetriever: BlendedMemoryRetriever,
    private val capabilityRouter: CapabilityRouter,
    private val registry: CapabilityRegistry,
    /** Upper bound on candidates retrieved per resolution. */
    private val maxCandidates: Int = 3
) {

    companion object {
        /** Predicate under which capability declarations live in Galaxy Memory. */
        const val CAPABILITY_PREDICATE = "capability"
    }

    /** One Galaxy Memory candidate tied to the capability it targets. */
    data class ResolutionCandidate(
        val rankedMemory: RankedMemory,
        val capability: CapabilityRegistry.Capability,
        /** The real router's outcome for this candidate. */
        val routed: CapabilityRouter.RouteResult
    )

    /** Outcome of resolving an underspecified reference. */
    sealed class ResolveOutcome {
        /** The top routable candidate was handed to the router and matched. */
        data class Resolved(
            val selected: ResolutionCandidate,
            val rankedCandidates: List<ResolutionCandidate>
        ) : ResolveOutcome()

        /** Candidates were retrieved but no capability could be routed. */
        data class Unresolved(
            val rankedCandidates: List<ResolutionCandidate>,
            val reason: UnresolvedReason
        ) : ResolveOutcome()
    }

    enum class UnresolvedReason {
        /** No capability candidates were retrievable from Galaxy Memory. */
        NO_CANDIDATES,
        /** The real router refused the request (no match, no fallback). */
        NO_ROUTABLE_MATCH,
        /** The top candidate is AMBIGUOUS in the router — never silently picked. */
        ROUTER_AMBIGUOUS
    }

    /**
     * Resolve [reference] to the best routable capability.
     *
     * @param reference the underspecified command reference, e.g. "the one
     *   that goes like a clear spoken voice".
     * @param context optional surrounding conversation context; when absent the
     *   reference itself is used as the salience-relevance context so ranking
     *   reflects how semantically close each candidate is to the query.
     * @param now current timestamp (injectable for testing).
     */
    suspend fun resolve(
        reference: String,
        context: String = "",
        now: Long = System.currentTimeMillis()
    ): ResolveOutcome {
        val ranked = blendedRetriever
            .retrieve(reference, context = context.ifBlank { reference }, now = now)
            .filter { it.node.predicate == CAPABILITY_PREDICATE }
            .take(maxCandidates)
        if (ranked.isEmpty()) {
            return ResolveOutcome.Unresolved(emptyList(), UnresolvedReason.NO_CANDIDATES)
        }

        val candidates = ranked.mapNotNull { rankedMemory ->
            val capability = registry.get(rankedMemory.node.subject)
                ?: return@mapNotNull null
            ResolutionCandidate(
                rankedMemory = rankedMemory,
                capability = capability,
                routed = capabilityRouter.route(
                    CapabilityRouter.RouteRequest(
                        category = capability.category,
                        action = reference,
                        requireHealthy = true
                    )
                )
            )
        }
        if (candidates.isEmpty()) {
            return ResolveOutcome.Unresolved(emptyList(), UnresolvedReason.NO_CANDIDATES)
        }

        val top = candidates.first()
        when (top.routed) {
            is CapabilityRouter.RouteResult.Matched -> return ResolveOutcome.Resolved(top, candidates)
            is CapabilityRouter.RouteResult.Ambiguous ->
                return ResolveOutcome.Unresolved(candidates, UnresolvedReason.ROUTER_AMBIGUOUS)
            else -> {
                // Top candidate not routable — fall through the next candidates
                // in rank order before concluding.
                candidates.drop(1).firstOrNull { it.routed is CapabilityRouter.RouteResult.Matched }?.let {
                    return ResolveOutcome.Resolved(it, candidates)
                }
                return ResolveOutcome.Unresolved(candidates, UnresolvedReason.NO_ROUTABLE_MATCH)
            }
        }
    }
}