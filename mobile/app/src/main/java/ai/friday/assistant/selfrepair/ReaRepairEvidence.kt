package ai.friday.assistant.selfrepair

/**
 * Provenance-bearing findings returned by the upstream REA (Reverse Engineer
 * Anything) tool. REA runs on a suitable MCP/CLI host; this port deliberately
 * keeps that host outside the Android process and makes the bridge injectable.
 */
data class ReaFinding(
    val title: String,
    val finding: String,
    val evidenceRefs: List<String>,
    val confidence: Double
)

/**
 * One REA investigation tied to the exact target revision. Findings are
 * observations, not instructions: callers must still correlate them with
 * Friday's test failures and source tree before proposing a patch.
 */
data class ReaRepairEvidence(
    val toolName: String,
    val toolVersion: String,
    val target: String,
    val targetRevision: String,
    /** Exact evidence_id returned by REA structuredContent. */
    val evidenceId: String,
    val findings: List<ReaFinding>,
    val limitations: List<String> = emptyList()
) {
    init {
        require(toolName.isNotBlank()) { "REA tool name is required" }
        require(toolVersion.isNotBlank()) { "REA version is required" }
        require(target.isNotBlank()) { "REA target is required" }
        require(targetRevision.isNotBlank()) { "REA target revision is required" }
        require(Regex("ev_[0-9a-f]{64}").matches(evidenceId)) {
            "REA evidence ID must be the canonical ev_<64 lowercase hex> identifier"
        }
        require(findings.isNotEmpty()) { "REA evidence must contain findings" }
        require(findings.all { it.title.isNotBlank() && it.finding.isNotBlank() }) {
            "REA findings must have titles and descriptions"
        }
        require(findings.all { it.evidenceRefs.isNotEmpty() }) {
            "every REA finding must cite at least one evidence reference"
        }
        require(findings.all { it.confidence in 0.0..1.0 }) {
            "REA confidence must be between 0 and 1"
        }
    }

    fun render(): String = buildString {
        appendLine("SOURCE: $toolName $toolVersion")
        appendLine("TARGET: $target")
        appendLine("TARGET REVISION: $targetRevision")
        appendLine("REA EVIDENCE ID: $evidenceId")
        findings.forEachIndexed { index, item ->
            appendLine("FINDING ${index + 1}: ${item.title}")
            appendLine("DESCRIPTION: ${item.finding}")
            appendLine("CONFIDENCE: ${item.confidence}")
            appendLine("EVIDENCE: ${item.evidenceRefs.joinToString(", ")}")
        }
        if (limitations.isNotEmpty()) {
            appendLine("LIMITATIONS:")
            limitations.forEach { appendLine("- $it") }
        }
    }.trimEnd()
}

/**
 * Adapter boundary for the real upstream REA MCP/CLI integration. A host
 * adapter must return evidence for the same source revision being repaired.
 */
fun interface ReaRepairEvidenceProvider {
    suspend fun investigate(
        requirement: RepairRequirement,
        observation: TestSuiteObservation
    ): ReaRepairEvidence
}
