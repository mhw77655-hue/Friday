package ai.friday.assistant.cloud

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
        val failures: List<CloudResult.Failure>,
        val usage: CloudTokenUsage? = null,
        val requestId: String? = null,
        val model: String? = null
    ) {
        val succeeded: Boolean get() = text != null
    }

    @Synchronized
    fun submit(request: CloudReasoningRequest): PoolResult {
        val failures = mutableListOf<CloudResult.Failure>()
        if (pool.isEmpty()) {
            return PoolResult(
                text = null,
                succeededProviderId = null,
                failures = listOf(CloudResult.Failure("cloud-pool", "No cloud providers are configured.", false))
            )
        }

        // Advance once per request, including requests where every provider
        // fails. Otherwise an exhausted pool retries the same first member on
        // every subsequent request and never changes its starting point.
        val startCursor = rotationCursor
        rotationCursor = (rotationCursor + 1) % pool.size

        // Rotate through the pool round-robin, starting at the current cursor,
        // mirroring ralph.sh's (i-1) % POOL_SIZE walk.
        val order = (0 until pool.size).map { rotationIndex ->
            pool[(startCursor + rotationIndex) % pool.size]
        }

        for (provider in order) {
            val result = try {
                provider.generate(request.prompt)
            } catch (e: Exception) {
                CloudResult.Failure(provider.id, e.message ?: "provider threw", rateLimited = false)
            }

            when (result) {
                is CloudResult.Success -> {
                    if (result.providerId != provider.id || result.text.isBlank()) {
                        failures.add(
                            CloudResult.Failure(
                                provider.id,
                                if (result.providerId != provider.id) "provider identity mismatch" else "provider returned blank reasoning",
                                rateLimited = false
                            )
                        )
                        continue
                    }
                    return PoolResult(result.text, result.providerId, failures, result.usage, result.requestId, result.model)
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