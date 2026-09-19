package com.jarvis.app.cloud

/**
 * Cloud model router — a faithful port of ralph.sh's MODEL_POOL rotation
 * pattern into the live app. The pool is an ordered list of free zero-signup
 * model providers; on a failure or rate-limit the router rotates to the next
 * pool member and retries. Returns which provider succeeded.
 *
 * ralph.sh pattern (as audited):
 *   - MODEL_POOL: ordered array of ~9 free zero-signup model ids
 *   - rotation: round-robin index per iteration (MODEL_IDX = (i-1) % POOL_SIZE)
 *   - failure detection: grep output for quota|rate.limit|capacity is busy|
 *     Cannot connect|exhausted|429, then the next call uses a different member
 *
 * This port makes the rotation explicit and typed: each [CloudProvider] is one
 * pool member, and [CloudModelRouter.submit] tries members in order, rotating
 * on failure/rate-limit, returning the provider that succeeded (or an aggregate
 * failure when the whole pool is exhausted).
 */
class CloudModelRouter(
    providers: List<CloudProvider>
) {

    private val pool: List<CloudProvider> = providers.toList()
    private var rotationCursor: Int = 0

    companion object {
        val RATE_LIMIT_MARKERS = listOf(
            "quota",
            "rate.limit",
            "capacity is busy",
            "cannot connect",
            "exhausted",
            "429"
        )
    }

    class PoolResult(
        val text: String?,
        val succeededProviderId: String?,
        val failures: List<CloudResult.Failure>
    ) {
        val succeeded: Boolean get() = text != null
    }

    fun submit(request: CloudReasoningRequest): PoolResult {
        val failures = mutableListOf<CloudResult.Failure>()

        // Rotate through the pool round-robin, starting at the current cursor,
        // mirroring ralph.sh's (i-1) % POOL_SIZE walk.
        val order = (0 until pool.size).map { rotationIndex ->
            pool[(rotationCursor + rotationIndex) % pool.size]
        }

        for (provider in order) {
            val result = try {
                provider.generate(request.prompt)
            } catch (e: Exception) {
                CloudResult.Failure(provider.id, e.message ?: "provider threw", rateLimited = false)
            }

            when (result) {
                is CloudResult.Success -> {
                    // ralph.sh rotates round-robin per iteration: the cursor
                    // advances one step every submit, so the next request starts
                    // from the next pool member.
                    rotationCursor = (rotationCursor + 1) % pool.size
                    return PoolResult(result.text, result.providerId, failures)
                }
                is CloudResult.Failure -> {
                    failures.add(result)
                }
            }
        }

        return PoolResult(null, null, failures)
    }

    fun poolMembers(): List<String> = pool.map { it.id }

    /**
     * Whether [text] carries one of the exhaustion/rate-limit markers that
     * ralph.sh greps for. Mirrors ralph.sh's `grep -qi "quota\|rate.limit\|..."
     * exactly: the markers are joined into one case-insensitive regex, so
     * `rate.limit` matches "rate limit", "rate.limit", etc.
     */
    fun isRateLimited(text: String): Boolean =
        Regex(RATE_LIMIT_MARKERS.joinToString("|"), RegexOption.IGNORE_CASE).containsMatchIn(text)
}