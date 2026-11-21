package ai.friday.assistant.symbiosis

/**
 * A measured compute configuration. Profiles are eligible only after paired
 * quality/performance evaluation against a named, versioned quality suite.
 * Scores from different suites must never be compared.
 */
data class ValidatedComputeProfile(
    val profileId: String,
    val modelId: String,
    val modelRevision: String,
    val quantization: String,
    val backend: String,
    val workloadClass: String,
    val qualitySuiteId: String,
    val qualityScore: Double,
    val criticalRegressionCount: Int,
    val validationRuns: Int,
    val contextTokens: Int,
    val p95LatencyMs: Long,
    val peakMemoryMb: Long,
    val energyMilliJoules: Long,
    val enabled: Boolean = true
)

/** Constraints for selecting among already validated profiles. */
data class ComputeProfileRequest(
    val workloadClass: String,
    val qualitySuiteId: String,
    val minimumQualityScore: Double,
    val minimumValidationRuns: Int,
    val requestedContextTokens: Int,
    val availableMemoryMb: Long,
    val protectedReserveMb: Long
)

/**
 * Selects the lowest-latency measured profile that satisfies the quality,
 * context, evidence and memory floor. It never invents a profile or silently
 * relaxes the quality threshold to fit a constrained device.
 */
object ValidatedComputeProfileSelector {
    fun select(
        profiles: Collection<ValidatedComputeProfile>,
        request: ComputeProfileRequest
    ): ValidatedComputeProfile? {
        require(request.workloadClass.isNotBlank())
        require(request.qualitySuiteId.isNotBlank())
        require(request.minimumQualityScore in 0.0..1.0)
        require(request.minimumValidationRuns >= 1)
        require(request.requestedContextTokens >= 1)
        require(request.availableMemoryMb >= 0L)
        require(request.protectedReserveMb >= 0L)

        val usableMemory = (request.availableMemoryMb - request.protectedReserveMb)
            .coerceAtLeast(0L)

        return profiles.asSequence()
            .filter { it.enabled }
            .filter { it.workloadClass == request.workloadClass }
            .filter { it.qualitySuiteId == request.qualitySuiteId }
            .filter { it.qualityScore.isFinite() && it.qualityScore >= request.minimumQualityScore }
            .filter { it.criticalRegressionCount == 0 }
            .filter { it.validationRuns >= request.minimumValidationRuns }
            .filter { it.contextTokens >= request.requestedContextTokens }
            .filter { it.peakMemoryMb > 0L && it.peakMemoryMb <= usableMemory }
            .filter { it.p95LatencyMs > 0L && it.energyMilliJoules >= 0L }
            .sortedWith(
                compareBy<ValidatedComputeProfile> { it.p95LatencyMs }
                    .thenBy { it.energyMilliJoules }
                    .thenBy { it.peakMemoryMb }
                    .thenBy { it.profileId }
            )
            .firstOrNull()
    }
}
