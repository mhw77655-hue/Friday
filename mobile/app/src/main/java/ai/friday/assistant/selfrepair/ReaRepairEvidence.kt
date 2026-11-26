package ai.friday.assistant.selfrepair

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * A built Android APK that REA can actually inspect. REA is not used as a
 * substitute for ordinary source-repository analysis: its Android workflow
 * operates on the packaged APK. The caller must supply the exact artifact and
 * the source revision from which that artifact was built.
 */
data class ReaTargetArtifact(
    val file: File,
    val sourceRevision: String,
    val packageName: String,
    val sha256: String
) {
    init {
        require(file.isFile && file.extension.equals("apk", ignoreCase = true)) {
            "REA Android target must be an existing APK file"
        }
        require(Regex("[0-9a-f]{40}").matches(sourceRevision)) {
            "APK source revision must be a full lowercase Git commit SHA"
        }
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(packageName)) {
            "APK package name is invalid"
        }
        require(Regex("[0-9a-f]{64}").matches(sha256)) {
            "APK SHA-256 must be 64 lowercase hexadecimal characters"
        }
    }

    /** Detect replacement or mutation between build handoff and REA invocation. */
    fun verifyUnchanged(): Boolean = runCatching { sha256(file) == sha256 }.getOrDefault(false)

    companion object {
        fun fromBuiltApk(file: File, sourceRevision: String, packageName: String): ReaTargetArtifact {
            require(file.isFile && file.extension.equals("apk", ignoreCase = true)) {
                "REA Android target must be an existing APK file"
            }
            return ReaTargetArtifact(file, sourceRevision, packageName, sha256(file))
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

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
    /** SHA-256 of the exact built APK REA inspected. */
    val targetSha256: String,
    /** Exact evidence_id returned by REA structuredContent. */
    val evidenceId: String,
    /** Complete canonical REA structuredContent JSON; never replaced by a summary. */
    val canonicalEvidenceJson: String,
    val findings: List<ReaFinding>,
    val limitations: List<String> = emptyList()
) {
    init {
        require(toolName.isNotBlank()) { "REA tool name is required" }
        require(toolVersion.isNotBlank()) { "REA version is required" }
        require(target.isNotBlank()) { "REA target is required" }
        require(Regex("[0-9a-f]{40}").matches(targetRevision)) { "REA target revision must be a full lowercase Git commit SHA" }
        require(Regex("[0-9a-f]{64}").matches(targetSha256)) { "REA target SHA-256 must be 64 lowercase hexadecimal characters" }
        require(Regex("ev_[0-9a-f]{64}").matches(evidenceId)) {
            "REA evidence ID must be the canonical ev_<64 lowercase hex> identifier"
        }
        require(canonicalEvidenceJson.trim().let { it.startsWith("{") && it.endsWith("}") }) {
            "complete canonical REA evidence JSON must be retained"
        }
        require(canonicalEvidenceJson.contains(evidenceId)) {
            "canonical REA evidence JSON must contain its exact evidence ID"
        }
        require(canonicalEvidenceJson.contains(targetSha256)) {
            "canonical REA evidence JSON must contain the inspected APK digest"
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
        appendLine("TARGET SHA-256: $targetSha256")
        appendLine("REA EVIDENCE ID: $evidenceId")
        appendLine("CANONICAL EVIDENCE RETAINED: yes (full structuredContent JSON)")
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
 * adapter must inspect the supplied built APK, not the source repository, and
 * return evidence for its exact source revision and byte digest.
 */
fun interface ReaRepairEvidenceProvider {
    suspend fun investigate(
        target: ReaTargetArtifact,
        requirement: RepairRequirement,
        observation: TestSuiteObservation
    ): ReaRepairEvidence
}
