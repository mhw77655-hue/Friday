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
        val evidence = buildList {
            add("Source Android package: " + packageName)
            reports.take(8).forEach { report ->
                add("APK " + report.fileName + " sha256=" + report.sha256 +
                    " size=" + report.sizeBytes + " dex=" + report.dexFiles.joinToString(",") +
                    " abis=" + report.nativeAbis.joinToString(","))
            }
            add("Transferable principle: " + recipe.transferablePrinciple)
            recipe.evidenceAndUnknowns.take(4).forEach { add("Evidence/unknown: " + it) }
        }.take(12)
        val request = JSONObject()
            .put("schema_version", 1)
            .put("request_id", id)
            .put("created_at", now().toString())
            .put("source_revision", "main")
            .put("intended_outcome",
                "Implement a Friday-compatible, independently validated integration derived from this reviewed recipe: " + recipe.title)
            .put("evidence", JSONArray(evidence))
            .put("acceptance_criteria", JSONArray(listOf(
                "Translate the recipe into Friday's existing capability and runtime interfaces; do not copy proprietary implementation code.",
                "Add regression tests for the discovered behaviour and the Friday adapter.",
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
            .put("rea_recipe", recipe.toJson())
            .put("privacy_review", JSONObject()
                .put("status", "owner_opted_in")
                .put("binary_apk_uploaded", false)
                .put("chat_history_included", false)
                .put("provider_credentials_included", false)
                .put("app_private_data_included", false))
        val result = client.dispatch(id, request.toString())
        return FridayCloudProposalResult(
            accepted = result.accepted,
            requestId = id,
            workflowUrl = result.workflowUrl,
            message = result.message
        )
    }
}
