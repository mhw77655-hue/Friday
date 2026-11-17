package com.jarvis.app.selfstudy

import com.jarvis.app.CapabilityManifest
import com.jarvis.app.selfreconfig.SystemGraph
import com.jarvis.app.selfreconfig.WiringDiagnostics
import com.jarvis.app.symbiosis.HostStudyRecord
import com.jarvis.app.symbiosis.LiquidSymbiosisSnapshot
import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.Message
import com.jarvis.app.model.MessageRole
import com.jarvis.app.model.ModelManager
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.Properties

/**
 * A bounded study of Friday's own installed architecture and observed runtime.
 *
 * Android packages do not contain the Kotlin source tree. This organ therefore
 * studies the app's packaged architecture graph, declared capability manifest,
 * wiring diagnostics and measured host/organ outcomes. It never pretends that
 * reading metadata is equivalent to reading source code.
 */
class ApplicationSelfStudy(
    private val graph: SystemGraph,
    private val reportFile: File,
    private val capabilitySnapshot: () -> CapabilityManifest?,
    private val hostSnapshot: () -> HostStudyRecord?,
    private val liquidSnapshot: () -> LiquidSymbiosisSnapshot?,
    private val sourceIndexSnapshot: () -> ApplicationSourceIndex.Summary? = { null }
) {
    data class Report(
        val createdAtMs: Long,
        val nodeCount: Int,
        val edgeCount: Int,
        val reachableCount: Int,
        val unreachableNodeIds: List<String>,
        val missingInvariantIds: List<String>,
        val declaredMissingCapabilities: List<String>,
        val knownLimitationIds: List<String>,
        val hostSamples: Long,
        val hostFingerprint: String,
        val minimumAvailableMemoryMb: Long,
        val averageCpuLoadEmaPercent: Double,
        val thermalPressureSamples: Long,
        val lowMemorySamples: Long,
        val additionalMemoryReserveMb: Long,
        val maximumObservedProcessPssMb: Long,
        val minimumBatteryPercent: Int,
        val liquidGeneration: Long,
        val liquidLastAdaptation: String,
        val liquidPredictionError: Double,
        val organStrategies: Map<String, String>,
        val sourceRevision: String,
        val sourceFileCount: Int,
        val sourceSymbolCount: Int,
        val sourcePackageCount: Int,
        val sourceDependencyEdgeCount: Int,
        val sourcePackageDependencies: Map<String, List<String>>,
        val sourceFilePaths: List<String>,
        val organFailureCounts: Map<String, Long>,
        val nextFocus: List<String>
    ) {
        val structurallyHealthy: Boolean
            get() = unreachableNodeIds.isEmpty() && missingInvariantIds.isEmpty()
    }

    data class Reflection(
        val createdAtMs: Long,
        val sourceRevision: String,
        val summary: String,
        val findings: List<String>,
        val nextActions: List<String>,
        val developmentRequest: String?,
        val constraints: List<String>,
        val evidenceGaps: List<String>
    ) {
        val hasDevelopmentRequest: Boolean
            get() = !developmentRequest.isNullOrBlank()
    }

    @Volatile private var latestReflection: Reflection? = readReflection()

    fun latestReflection(): Reflection? = latestReflection

    private fun reflectionFile(): File =
        File(reportFile.parentFile ?: File("."), "latest-reflection.properties")

    private fun readReflection(): Reflection? {
        val file = reflectionFile()
        if (!file.isFile) return null
        return runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            val developmentRequest = props.getProperty("developmentRequest").orEmpty()
                .takeIf { it.isNotBlank() && it.length <= 1600 }
            Reflection(
                createdAtMs = props.getProperty("createdAtMs").toLong(),
                sourceRevision = props.getProperty("sourceRevision").orEmpty(),
                summary = props.getProperty("summary").orEmpty(),
                findings = parseStoredList(props.getProperty("findings").orEmpty()),
                nextActions = parseStoredList(props.getProperty("nextActions").orEmpty()),
                developmentRequest = developmentRequest,
                constraints = parseStoredList(props.getProperty("constraints").orEmpty()),
                evidenceGaps = parseStoredList(props.getProperty("evidenceGaps").orEmpty())
            )
        }.getOrNull()
    }

    /**
     * Ask Friday's existing model authority to reason over her own measured
     * architecture report. This produces a proposal only; it cannot mutate
     * source, grant permissions, or promote a tool.
     */
    suspend fun reflect(
        report: Report,
        modelManager: ModelManager,
        nowMs: Long = System.currentTimeMillis()
    ): Reflection? = reflect(report, { request -> modelManager.generate(request) }, nowMs)

    suspend fun reflect(
        report: Report,
        complete: suspend (GenerateRequest) -> GenerateResult,
        nowMs: Long = System.currentTimeMillis()
    ): Reflection? {
        val prompt = buildString {
            appendLine("FRIDAY SELF-STUDY EVIDENCE")
            appendLine("source_revision=${report.sourceRevision}")
            appendLine("source_files=${report.sourceFileCount}; declared_symbols=${report.sourceSymbolCount}; packages=${report.sourcePackageCount}")
            appendLine("source_dependency_edges=${report.sourceDependencyEdgeCount}")
            appendLine("source_package_dependencies=${report.sourcePackageDependencies.entries.take(30).joinToString { "${it.key}->${it.value.joinToString()}" }}")
            appendLine("source_file_paths=${report.sourceFilePaths.take(60).joinToString()}")
            appendLine("runtime_graph_nodes=${report.nodeCount}; edges=${report.edgeCount}; reachable=${report.reachableCount}")
            appendLine("unreachable_nodes=${report.unreachableNodeIds.joinToString()}")
            appendLine("missing_invariants=${report.missingInvariantIds.joinToString()}")
            appendLine("missing_capabilities=${report.declaredMissingCapabilities.joinToString()}")
            appendLine("known_limitations=${report.knownLimitationIds.joinToString()}")
            appendLine("host_fingerprint=${report.hostFingerprint}")
            appendLine("host_samples=${report.hostSamples}; min_available_memory_mb=${report.minimumAvailableMemoryMb}")
            appendLine("cpu_ema_percent=${report.averageCpuLoadEmaPercent}; max_process_pss_mb=${report.maximumObservedProcessPssMb}")
            appendLine("thermal_samples=${report.thermalPressureSamples}; low_memory_samples=${report.lowMemorySamples}")
            appendLine("memory_reserve_mb=${report.additionalMemoryReserveMb}; minimum_battery_percent=${report.minimumBatteryPercent}")
            appendLine("liquid_generation=${report.liquidGeneration}; prediction_error=${report.liquidPredictionError}")
            appendLine("liquid_last_adaptation=${report.liquidLastAdaptation}")
            appendLine("organ_failures=${report.organFailureCounts}")
            appendLine("organ_strategies=${report.organStrategies}")
            appendLine("deterministic_next_focus=${report.nextFocus}")
            appendLine()
            appendLine("Analyze only this evidence. Do not pretend you read source text; the source index is structural metadata.")
            appendLine("Identify the most important mismatch between this architecture and Friday's canonical vision:")
            appendLine("one persistent individual, Android as her body, one intelligence, Jev as an optional decision faculty,")
            appendLine("continuous presence, phone-specific liquid symbiosis, experience-driven learning, and repository-native self-development.")
            appendLine("The Android app is not a development environment. Do not propose executable code or an in-app tool.")
            appendLine("If evidence justifies source-level work, write a concise repository development request: desired outcome, affected area if known, evidence, acceptance criteria, risks and unresolved questions.")
            appendLine("Do not invent source details; explicitly name missing evidence and uncertainty.")
            appendLine("Return exactly JSON with fields: summary, findings[], next_actions[], development_request, constraints[], evidence_gaps[].")
            appendLine("Use null for development_request when no source-level change is justified.")
        }
        val result = try {
            complete(
                GenerateRequest(
                    messages = listOf(
                        Message(MessageRole.SYSTEM, REFLECTION_SYSTEM_PROMPT),
                        Message(MessageRole.USER, prompt)
                    ),
                    maxTokens = 384,
                    temperature = 0.15f
                )
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        if (result.finishReason == FinishReason.ERROR || result.content.isBlank()) return null
        val json = extractJson(result.content) ?: return null
        val developmentRequest = json.optString("development_request").trim()
            .takeIf { it.isNotBlank() && it.length <= 1600 }
        val reflection = Reflection(
            createdAtMs = nowMs,
            sourceRevision = report.sourceRevision,
            summary = json.optString("summary").trim().take(1200),
            findings = jsonArrayStrings(json.optJSONArray("findings"), 8),
            nextActions = jsonArrayStrings(json.optJSONArray("next_actions"), 8),
            developmentRequest = developmentRequest,
            constraints = jsonArrayStrings(json.optJSONArray("constraints"), 8),
            evidenceGaps = jsonArrayStrings(json.optJSONArray("evidence_gaps"), 8)
        )
        persistReflection(reflection)
        latestReflection = reflection
        return reflection
    }

    /**
     * Form a conservative development goal using only deterministic, locally
     * observed evidence. This path is always available without a language model.
     * It does not claim source comprehension: the repository worker must inspect
     * actual source and tests before proposing a patch.
     */
    fun reflectModelFree(
        report: Report,
        nowMs: Long = System.currentTimeMillis()
    ): Reflection {
        val findings = buildList {
            if (report.missingInvariantIds.isNotEmpty()) {
                add("Observed missing runtime invariants: ${report.missingInvariantIds.joinToString()}")
            }
            if (report.unreachableNodeIds.isNotEmpty()) {
                add("Observed unreachable runtime components: ${report.unreachableNodeIds.joinToString()}")
            }
            if (report.organFailureCounts.isNotEmpty()) {
                add("Observed organ failures: ${report.organFailureCounts.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" }}")
            }
            if (report.declaredMissingCapabilities.isNotEmpty()) {
                add("Capability manifest declares unavailable capabilities: ${report.declaredMissingCapabilities.joinToString()}")
            }
            if (report.knownLimitationIds.isNotEmpty()) {
                add("Capability manifest records known limitations: ${report.knownLimitationIds.joinToString()}")
            }
            if (report.sourceRevision.isBlank() || report.sourceFileCount <= 0) {
                add("Packaged source architecture metadata is missing or incomplete")
            } else if (report.sourceDependencyEdgeCount <= 0) {
                add("Packaged source architecture metadata contains no dependency edges")
            }
        }

        val request = when {
            report.missingInvariantIds.isNotEmpty() ->
                "Inspect the actual repository source and tests for the observed missing runtime invariants ${report.missingInvariantIds.joinToString()}. Identify the real cause, implement the smallest coherent repair that preserves Friday's full individual architecture, and add production-composition regression tests. Do not infer source details from Android metadata."
            report.unreachableNodeIds.isNotEmpty() ->
                "Trace the observed unreachable runtime components ${report.unreachableNodeIds.joinToString()} through the actual repository source and production composition. Determine whether each is intentionally dormant, obsolete, or incorrectly disconnected; repair only proven wiring gaps and add integration tests. Do not activate every organ by default or create a fixed cognitive state machine."
            report.organFailureCounts.isNotEmpty() -> {
                val worst = report.organFailureCounts.entries
                    .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                    .take(3)
                "Investigate the observed organ failure counts ${worst.joinToString { "${it.key}=${it.value}" }} against actual source, traces and tests. Reproduce the failure, identify its cause, and implement a bounded repair with regression coverage; do not treat counts alone as proof of root cause."
            }
            report.declaredMissingCapabilities.isNotEmpty() ->
                "Inspect why the live system declares these capabilities unavailable: ${report.declaredMissingCapabilities.joinToString()}. Verify actual registration, permissions, health and callable production paths before changing code; implement only a proven missing vertical slice with tests."
            report.knownLimitationIds.isNotEmpty() ->
                "Investigate the recorded capability limitations ${report.knownLimitationIds.joinToString()} in actual source and tests. Select one limitation whose cause and acceptance criteria can be verified, then implement a coherent repair without narrowing Friday to a chatbot."
            report.sourceRevision.isBlank() || report.sourceFileCount <= 0 ->
                "Repair the build-time source architecture index so the installed app can identify its source revision and tracked source structure. Keep it metadata-only; the repository worker must inspect source text."
            report.sourceDependencyEdgeCount <= 0 ->
                "Inspect the build-time source-index generator: the packaged index reports no dependency edges. Verify the generator against actual imports and add deterministic tests before using the index for architectural conclusions."
            else -> null
        }

        val evidenceGaps = buildList {
            if (report.sourceRevision.isBlank()) add("No source revision is available in the packaged index.")
            if (report.sourceFileCount <= 0) add("No tracked source-file count is available.")
            if (report.sourceDependencyEdgeCount <= 0) add("No source dependency edges are available.")
            if (report.hostSamples < 20L) add("Phone resource observations are still below the 20-sample minimum.")
            add("The installed app contains structural metadata, not the Kotlin source text.")
            add("No target-device acceptance result is inferred from this report.")
        }
        val constraints = listOf(
            "Android is Friday's deployed body, not a source editor or build environment.",
            "A repository worker must inspect actual source and tests before implementing.",
            "No source patch, test run, build, merge, signing, release or installation is claimed.",
            "Preserve model-independent identity and continuity; models remain optional extra computation."
        )
        val reflection = Reflection(
            createdAtMs = nowMs,
            sourceRevision = report.sourceRevision,
            summary = if (request == null) {
                "No source-level change was selected: this study found no sufficiently specific structural gap."
            } else {
                "Model-free self-study selected one evidence-backed repository investigation from observed runtime and architecture facts."
            },
            findings = findings.take(8),
            nextActions = if (request == null) {
                listOf("Continue bounded observation and reassess when new evidence appears.")
            } else {
                listOf("Send the bounded request to the repository development environment.", "Require source inspection, regression tests and independent CI.")
            },
            developmentRequest = request?.take(1600),
            constraints = constraints,
            evidenceGaps = evidenceGaps
        )
        persistReflection(reflection)
        latestReflection = reflection
        return reflection
    }

    private fun extractJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    private fun jsonArrayStrings(array: org.json.JSONArray?, limit: Int): List<String> =
        if (array == null) emptyList() else buildList {
            for (index in 0 until minOf(array.length(), limit)) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it.take(400)) }
            }
        }

    private fun parseStoredList(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until minOf(array.length(), 8)) {
                array.optString(index).takeIf(String::isNotBlank)?.let { add(it.take(400)) }
            }
        }
    }.getOrDefault(emptyList())

    private fun persistReflection(reflection: Reflection) {
        val file = reflectionFile()
        file.parentFile?.mkdirs()
        val props = Properties().apply {
            setProperty("createdAtMs", reflection.createdAtMs.toString())
            setProperty("sourceRevision", reflection.sourceRevision)
            setProperty("summary", reflection.summary)
            setProperty("findings", JSONArray(reflection.findings).toString())
            setProperty("nextActions", JSONArray(reflection.nextActions).toString())
            setProperty("developmentRequest", reflection.developmentRequest.orEmpty())
            setProperty("constraints", JSONArray(reflection.constraints).toString())
            setProperty("evidenceGaps", JSONArray(reflection.evidenceGaps).toString())
        }
        file.outputStream().use { props.store(it, "Friday self-study reflection; proposals only") }
    }

    @Volatile private var latest: Report? = null

    fun latestReport(): Report? = latest

    @Synchronized
    fun study(nowMs: Long = System.currentTimeMillis()): Report {
        val wiring = WiringDiagnostics(graph).diagnose()
        val manifest = capabilitySnapshot()
        val host = hostSnapshot()
        val liquid = liquidSnapshot()
        val sourceIndex = sourceIndexSnapshot()
        val missingCapabilities = manifest?.capabilities
            ?.filterValues { !it }?.keys?.sorted().orEmpty()
        val limitations = manifest?.knownLimitations?.keys?.sorted().orEmpty()
        val failures = liquid?.organs
            ?.mapValues { it.value.failedRuns }?.filterValues { it > 0L }
            ?.mapKeys { it.key.name }?.toSortedMap().orEmpty()

        val focus = buildList {
            if (wiring.missingInvariants.isNotEmpty()) add("Review missing fixed invariants: ${wiring.missingInvariants.joinToString()}")
            if (wiring.unreachableNodes.isNotEmpty()) add("Review unreachable organs: ${wiring.unreachableNodes.joinToString { it.id }}")
            if (missingCapabilities.isNotEmpty()) add("Prioritize missing capabilities: ${missingCapabilities.take(8).joinToString()}")
            if (limitations.isNotEmpty()) add("Review known limitations: ${limitations.take(5).joinToString()}")
            if (failures.isNotEmpty()) add("Study organ failures: ${failures.entries.joinToString { "${it.key}=${it.value}" }}")
            if (sourceIndex == null) add("Source architecture index unavailable; verify generated build metadata")
            if (sourceIndex != null && sourceIndex.dependencyEdgeCount == 0) add("Source dependency graph is empty; inspect generated import metadata before architectural conclusions")
            if ((host?.sampleCount ?: 0L) < 20L) add("Continue observing this phone before changing resource strategy")
            if ((host?.lowMemorySamples ?: 0L) > 0L) add("Preserve the learned memory reserve; low-memory pressure has been observed")
            if ((host?.thermalPressureSamples ?: 0L) > 0L) add("Keep background work subordinate to thermal headroom")
            if ((liquid?.lastPredictionError ?: 0.0) > 0.25) add("Collect more host transitions before trusting the current predictive model")
            if (isEmpty()) add("No structural issue detected; compare measured organ quality and cost before proposing changes")
        }

        val report = Report(
            createdAtMs = nowMs,
            nodeCount = graph.nodeCount(),
            edgeCount = graph.edgeCount(),
            reachableCount = wiring.reachableCount,
            unreachableNodeIds = wiring.unreachableNodes.map { it.id }.sorted(),
            missingInvariantIds = wiring.missingInvariants.sorted(),
            declaredMissingCapabilities = missingCapabilities,
            knownLimitationIds = limitations,
            hostSamples = host?.sampleCount ?: 0L,
            hostFingerprint = host?.fingerprint?.stableKey.orEmpty(),
            minimumAvailableMemoryMb = host?.minimumAvailableMemoryMb ?: -1L,
            averageCpuLoadEmaPercent = host?.averageCpuLoadEmaPercent ?: 0.0,
            thermalPressureSamples = host?.thermalPressureSamples ?: 0L,
            lowMemorySamples = host?.lowMemorySamples ?: 0L,
            additionalMemoryReserveMb = host?.additionalMemoryReserveMb ?: 0L,
            maximumObservedProcessPssMb = host?.maximumObservedProcessPssMb ?: 0L,
            minimumBatteryPercent = host?.minimumBatteryPercent ?: 100,
            liquidGeneration = liquid?.generation ?: 0L,
            liquidLastAdaptation = liquid?.lastAdaptation.orEmpty(),
            liquidPredictionError = liquid?.lastPredictionError ?: 0.0,
            organStrategies = liquid?.organs?.mapKeys { it.key.name }?.mapValues { (_, strategy) ->
                "context=${strategy.contextBudget};concurrency=${strategy.concurrency};chunk=${strategy.chunkSize};" +
                    "background=${strategy.backgroundWeight};success=${strategy.successfulRuns};" +
                    "failure=${strategy.failedRuns};latencyEmaMs=${strategy.latencyEmaMs};" +
                    "memoryPeakMb=${strategy.memoryPeakMb};rewardEma=${strategy.rewardEma}"
            }?.toSortedMap().orEmpty(),
            sourceRevision = sourceIndex?.sourceRevision.orEmpty(),
            sourceFileCount = sourceIndex?.sourceFileCount ?: 0,
            sourceSymbolCount = sourceIndex?.declaredSymbolCount ?: 0,
            sourcePackageCount = sourceIndex?.packageCount ?: 0,
            sourceDependencyEdgeCount = sourceIndex?.dependencyEdgeCount ?: 0,
            sourcePackageDependencies = sourceIndex?.packageDependencies.orEmpty(),
            sourceFilePaths = sourceIndex?.files?.take(500)?.map { it.path }.orEmpty(),
            organFailureCounts = failures,
            nextFocus = focus
        )
        persist(report)
        latest = report
        return report
    }

    companion object {
        private const val REFLECTION_SYSTEM_PROMPT = """
            You are Friday reviewing evidence from your own installed Android application.
            You are the same individual and model authority that serves normal conversation.
            Be skeptical: structural counts are not source comprehension, and a reported
            capability is not proof that it works. Separate observed facts, inferences,
            uncertainty and proposed work. Android is the deployed body, never a source
            development environment. Never propose in-app code synthesis, source editing,
            compilation, execution, testing or promotion. When evidence justifies source work,
            formulate at most one bounded repository development request with an intended
            outcome, supporting evidence, acceptance criteria, risks, rollback expectation
            and unresolved questions. This request is a proposal only; do not imply it was
            synchronized, assigned to a worker, implemented or verified. Never claim to have
            changed code or executed a build. Output JSON only.
        """
    }

    private fun persist(report: Report) {
        reportFile.parentFile?.mkdirs()
        val props = Properties().apply {
            setProperty("createdAtMs", report.createdAtMs.toString())
            setProperty("nodeCount", report.nodeCount.toString())
            setProperty("edgeCount", report.edgeCount.toString())
            setProperty("reachableCount", report.reachableCount.toString())
            setProperty("unreachableNodeIds", report.unreachableNodeIds.joinToString("|"))
            setProperty("missingInvariantIds", report.missingInvariantIds.joinToString("|"))
            setProperty("declaredMissingCapabilities", report.declaredMissingCapabilities.joinToString("|"))
            setProperty("knownLimitationIds", report.knownLimitationIds.joinToString("|"))
            setProperty("hostSamples", report.hostSamples.toString())
            setProperty("hostFingerprint", report.hostFingerprint)
            setProperty("minimumAvailableMemoryMb", report.minimumAvailableMemoryMb.toString())
            setProperty("averageCpuLoadEmaPercent", report.averageCpuLoadEmaPercent.toString())
            setProperty("thermalPressureSamples", report.thermalPressureSamples.toString())
            setProperty("lowMemorySamples", report.lowMemorySamples.toString())
            setProperty("additionalMemoryReserveMb", report.additionalMemoryReserveMb.toString())
            setProperty("maximumObservedProcessPssMb", report.maximumObservedProcessPssMb.toString())
            setProperty("minimumBatteryPercent", report.minimumBatteryPercent.toString())
            setProperty("liquidGeneration", report.liquidGeneration.toString())
            setProperty("liquidLastAdaptation", report.liquidLastAdaptation)
            setProperty("liquidPredictionError", report.liquidPredictionError.toString())
            report.organStrategies.forEach { (organ, summary) ->
                setProperty("organStrategy.$organ", summary)
            }
            setProperty("sourceRevision", report.sourceRevision)
            setProperty("sourceFileCount", report.sourceFileCount.toString())
            setProperty("sourceSymbolCount", report.sourceSymbolCount.toString())
            setProperty("sourcePackageCount", report.sourcePackageCount.toString())
            setProperty("sourceDependencyEdgeCount", report.sourceDependencyEdgeCount.toString())
            setProperty("sourcePackageDependencies", report.sourcePackageDependencies.entries.joinToString("|") { "${it.key}:${it.value.joinToString(",")}" })
            setProperty("sourceFilePaths", report.sourceFilePaths.joinToString("|"))
            setProperty("organFailureCounts", report.organFailureCounts.entries.joinToString("|") { "${it.key}:${it.value}" })
            setProperty("nextFocus", report.nextFocus.joinToString("\n"))
        }
        reportFile.outputStream().use { props.store(it, "Friday self-study; no user conversation content") }
    }
}
