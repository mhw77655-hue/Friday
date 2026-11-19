package com.jarvis.app.rea

import com.jarvis.app.builder.GitHubWorkflowDispatchClient
import com.jarvis.app.builder.RepositoryBuildSettings
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class FridayCloudProposalResult(
    val accepted: Boolean,
    val requestId: String,
    val workflowUrl: String,
    val message: String
)

/**
 * Sends only the reviewed recipe and bounded APK metadata to Friday's isolated
 * repository worker. APK binaries, provider keys, chat history and app data are
 * deliberately excluded from this transport.
 */
class FridayRecipeCloudDispatcher(
    private val settings: RepositoryBuildSettings,
    private val client: GitHubWorkflowDispatchClient,
    private val now: () -> Instant = Instant::now
) {
    suspend fun submit(
        packageName: String,
        recipe: FridayCompatibilityRecipe,
        reports: List<ApkArtifactInspector.Report>
    ): FridayCloudProposalResult {
        val id = UUID.randomUUID().toString().replace("-", "").lowercase()
        val url = client.workflowUrl()
        if (!settings.hasToken() || !settings.automaticRequestsEnabled()) {
            return FridayCloudProposalResult(false, id, url,
                "Owner has not enabled automatic repository requests or configured the GitHub Actions token.")
        }
        val recipeEvidence = "FRIDAY_REA_COMPATIBILITY_RECIPE_JSON: " + recipe.toJson().toString()
        if (recipeEvidence.toByteArray(Charsets.UTF_8).size > 7_500) {
            return FridayCloudProposalResult(false, id, url, "The compatibility recipe exceeds the cloud request evidence limit.")
        }
        val evidence = buildList {
            add("Source Android package: " + packageName)
            reports.take(8).forEach { report ->
                add("APK " + report.fileName + " sha256=" + report.sha256 +
                    " size=" + report.sizeBytes + " entries=" + report.entryCount +
                    " dex=" + report.dexFiles.joinToString(",") +
                    " abis=" + report.nativeAbis.joinToString(",") +
                    " notable=" + report.notablePaths.take(80).joinToString(","))
                // Send the actual bounded DEX structure evidence used by the
                // REA analyst, not just the APK hash and filenames. This lets
                // the cloud worker inspect class/package/method/API-host clues
                // and independently decide what is transferable. No APK bytes,
                // app-private data, or credentials cross this boundary.
                report.dexStructure.take(1).forEach { dex ->
                    val dexEvidence = JSONObject()
                        .put("file", dex.fileName)
                        .put("parsed", dex.parsed)
                        .put("classCount", dex.classCount)
                        .put("methodCount", dex.methodCount)
                        .put("fieldCount", dex.fieldCount)
                        .put("packages", JSONArray(dex.packageNames.take(16).map { it.take(120) }))
                        .put("classes", JSONArray(dex.classDescriptors.take(16).map { it.take(180) }))
                        .put("methods", JSONArray(dex.interestingMethods.take(12).map { it.take(220) }))
                        .put("apiHosts", JSONArray(dex.apiHosts.take(12).map { it.take(180) }))
                        .put("methodEvidence", JSONArray(dex.methodEvidence.take(4).map { method ->
                            JSONObject()
                                .put("signature", method.signature.take(320))
                                .put("opcodes", JSONArray(method.opcodeSequence.take(40)))
                                .put("references", JSONArray(method.referencedSymbols.take(12).map { it.take(180) }))
                                .put("strings", JSONArray(method.stringLiterals.take(8).map { it.take(140) }))
                        }))
                        .put("warning", dex.warning ?: JSONObject.NULL)
                        .toString()
                    add("DEX_STRUCTURE_JSON: " + dexEvidence)
                }
            }
            add("Transferable principle: " + recipe.transferablePrinciple)
            recipe.evidenceAndUnknowns.take(4).forEach { add("Evidence/unknown: " + it) }
        }.take(11) + recipeEvidence
        val request = JSONObject()
            .put("schema_version", 1)
            .put("request_id", id)
            .put("created_at", now().toString())
            .put("source_revision", "main")
            .put("intended_outcome",
                "Implement a Friday-compatible, independently validated integration derived from this reviewed recipe: " + recipe.title)
            .put("evidence", JSONArray(evidence))
            .put("acceptance_criteria", JSONArray(listOf(
                "Use the supplied DEX structure, class/package names, method signatures, referenced API hosts and APK inventory as evidence; distinguish observed facts from hypotheses.",
                "Identify a concrete transferable mechanism or documented integration surface; do not infer runtime behaviour from a symbol name alone.",
                "Implement a working Friday-compatible adapter or host integration, not merely another research report or architecture proposal.",
                "Prefer reconstructing behaviour from public/documented interfaces and independently verified semantics; do not copy proprietary implementation code.",
                "Add regression tests proving the adapted behaviour and Friday integration, including malformed or unsupported source evidence.",
                "Run Builder safety contracts, JVM tests, Android build and disposable-device instrumentation.",
                "Keep platform permissions, identity, memory, credentials, signing and update control-plane changes owner-reviewed."
            )))
            .put("risks", JSONArray(listOf(
                "APK inventory is structural evidence, not proof of runtime behaviour.",
                "Respect the source application's licence, terms and documented public interfaces.",
                "Do not add arbitrary shell execution, hidden-data access or privilege escalation."
            )))
            .put("rollback_expectation",
                "Publish a bounded proposal, run independent CI and preserve a revert path; never install an APK or merge an unverified patch.")
            .put("unresolved_questions", JSONArray(listOf(
                "Which observed behaviours can be independently verified from the supplied metadata?",
                "Does this integration require Android host changes or can it remain a restricted runtime capability?"
            )))
            .put("privacy_review", JSONObject()
                .put("status", "approved")
                .put("reviewed_by", "owner-enabled-rea-cloud-analysis")
                .put("reviewed_at", now().toString())
                .put("aggregate_runtime_evidence_enabled", false)
                .put("aggregate_runtime_evidence_scope", "none"))
        val result = client.dispatch(id, request.toString())
        return FridayCloudProposalResult(
            accepted = result.accepted,
            requestId = id,
            workflowUrl = result.workflowUrl,
            message = result.message
        )
    }
}
