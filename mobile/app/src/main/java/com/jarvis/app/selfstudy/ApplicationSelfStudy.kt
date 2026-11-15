package com.jarvis.app.selfstudy

import com.jarvis.app.CapabilityManifest
import com.jarvis.app.selfreconfig.SystemGraph
import com.jarvis.app.selfreconfig.WiringDiagnostics
import com.jarvis.app.symbiosis.HostStudyRecord
import com.jarvis.app.symbiosis.LiquidSymbiosisSnapshot
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
    private val liquidSnapshot: () -> LiquidSymbiosisSnapshot?
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
        val organFailureCounts: Map<String, Long>,
        val nextFocus: List<String>
    ) {
        val structurallyHealthy: Boolean
            get() = unreachableNodeIds.isEmpty() && missingInvariantIds.isEmpty()
    }

    @Volatile private var latest: Report? = null

    fun latestReport(): Report? = latest

    @Synchronized
    fun study(nowMs: Long = System.currentTimeMillis()): Report {
        val wiring = WiringDiagnostics(graph).diagnose()
        val manifest = capabilitySnapshot()
        val host = hostSnapshot()
        val liquid = liquidSnapshot()
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
            if ((host?.sampleCount ?: 0L) < 20L) add("Continue observing this phone before changing resource strategy")
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
            organFailureCounts = failures,
            nextFocus = focus
        )
        persist(report)
        latest = report
        return report
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
            setProperty("organFailureCounts", report.organFailureCounts.entries.joinToString("|") { "${it.key}:${it.value}" })
            setProperty("nextFocus", report.nextFocus.joinToString("\n"))
        }
        reportFile.outputStream().use { props.store(it, "Friday self-study; no user conversation content") }
    }
}
