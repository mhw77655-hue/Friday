package com.jarvis.app.rea

import com.jarvis.app.builder.GitHubDispatchResponse
import com.jarvis.app.builder.GitHubDispatchTransport
import com.jarvis.app.builder.GitHubWorkflowDispatchClient
import com.jarvis.app.builder.RepositoryBuildSettings
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Base64

class FridayRecipeCloudDispatcherTest {
    @Test fun sendsOnlyTrustedSchemaAndNeverUploadsApkBinary() = runBlocking {
        var captured: JSONObject? = null
        val transport = object : GitHubDispatchTransport {
            override fun post(url: String, token: String, body: String): GitHubDispatchResponse {
                val outer = JSONObject(body)
                val encoded = outer.getJSONObject("inputs").getString("request_json_b64")
                captured = JSONObject(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
                return GitHubDispatchResponse(204)
            }
            override fun get(url: String, token: String) = GitHubDispatchResponse(500)
        }
        val settings = object : RepositoryBuildSettings {
            override fun hasToken() = true
            override fun token() = "github_pat_" + "A".repeat(32)
            override fun automaticRequestsEnabled() = true
        }
        val client = GitHubWorkflowDispatchClient({ settings.token() }, transport)
        val dispatcher = FridayRecipeCloudDispatcher(settings, client) { Instant.parse("2026-11-19T10:00:00Z") }
        val recipe = FridayCompatibilityRecipe(
            title = "Transfer a reusable transformation",
            integrationKind = FridayIntegrationKind.DATA_TRANSFORMER,
            transferablePrinciple = "Separate parsing from transformation and validate every boundary.",
            fridayInterfaces = listOf("Capability"),
            implementationPlan = listOf("Build adapter", "Add regression tests"),
            permissionRequirements = emptyList(),
            risk = "Low when kept inside the restricted interpreter.",
            evidenceAndUnknowns = listOf("Static APK inventory cannot prove runtime behaviour.")
        )
        val report = ApkArtifactInspector.Report("base.apk", 123, "a".repeat(64), 1,
            listOf("classes.dex"), listOf("arm64-v8a"), listOf("assets/config.json"), 100)
        val result = dispatcher.submit("com.example.source", recipe, listOf(report))

        assertTrue(result.accepted)
        val request = requireNotNull(captured)
        assertEquals(setOf(
            "schema_version", "request_id", "created_at", "source_revision", "intended_outcome",
            "evidence", "acceptance_criteria", "risks", "rollback_expectation",
            "unresolved_questions", "privacy_review"
        ), request.keys().asSequence().toSet())
        assertEquals("approved", request.getJSONObject("privacy_review").getString("status"))
        assertTrue(request.getJSONArray("evidence").toString().contains("FRIDAY_REA_COMPATIBILITY_RECIPE_JSON"))
        assertFalse(request.toString().contains("binary_apk"))
        assertFalse(request.toString().contains("base64_apk"))
    }

    @Test fun refusesWhenOwnerHasNotEnabledRepositoryRequests() = runBlocking {
        val settings = object : RepositoryBuildSettings {
            override fun hasToken() = false
            override fun token(): String? = null
            override fun automaticRequestsEnabled() = false
        }
        val client = GitHubWorkflowDispatchClient({ null }, object : GitHubDispatchTransport {
            override fun post(url: String, token: String, body: String) = error("must not dispatch")
            override fun get(url: String, token: String) = error("must not query")
        })
        val recipe = FridayCompatibilityRecipe("Test recipe", FridayIntegrationKind.RESEARCH_ONLY,
            "A transferable concept with clear constraints.", listOf("Research"), listOf("Study evidence"),
            emptyList(), "Unknown", listOf("Needs validation."))
        val result = FridayRecipeCloudDispatcher(settings, client).submit("com.example.app", recipe, emptyList())
        assertFalse(result.accepted)
        assertTrue(result.message.contains("Owner has not enabled"))
    }
}
