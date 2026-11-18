package com.jarvis.app.builder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

data class GitHubDispatchResponse(
    val statusCode: Int,
    val body: String = ""
)

interface GitHubDispatchTransport {
    fun post(url: String, token: String, body: String): GitHubDispatchResponse
    fun get(url: String, token: String): GitHubDispatchResponse
}

class HttpUrlConnectionGitHubDispatchTransport : GitHubDispatchTransport {
    override fun post(url: String, token: String, body: String): GitHubDispatchResponse =
        execute(url, token, "POST", body)

    override fun get(url: String, token: String): GitHubDispatchResponse =
        execute(url, token, "GET", null)

    private fun execute(url: String, token: String, method: String, body: String?): GitHubDispatchResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = body != null
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (body != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText().take(16_000) }.orEmpty()
            GitHubDispatchResponse(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

data class GitHubWorkflowDispatchResult(
    val accepted: Boolean,
    val requestId: String,
    val httpStatus: Int?,
    val workflowUrl: String,
    val message: String
)

data class GitHubWorkflowRunStatus(
    val requestId: String,
    val found: Boolean,
    val state: String,
    val conclusion: String? = null,
    val runUrl: String? = null,
    val message: String
)

/**
 * Dispatches and observes only the Friday proposal workflow. The phone token
 * has Actions:write but no Contents:write; repository edits happen in the
 * isolated Actions worker and trusted publisher jobs.
 */
class GitHubWorkflowDispatchClient(
    private val tokenProvider: () -> String?,
    private val transport: GitHubDispatchTransport = HttpUrlConnectionGitHubDispatchTransport(),
    private val owner: String = DEFAULT_OWNER,
    private val repository: String = DEFAULT_REPOSITORY,
    private val workflowFile: String = DEFAULT_WORKFLOW
) {
    suspend fun dispatch(requestId: String, requestJson: String): GitHubWorkflowDispatchResult =
        withContext(Dispatchers.IO) {
            val workflowUrl = workflowUrl()
            if (!REQUEST_ID.matches(requestId)) {
                return@withContext GitHubWorkflowDispatchResult(
                    false, requestId, null, workflowUrl, "Invalid development request identifier."
                )
            }
            val encodedRequest = Base64.getEncoder().encodeToString(requestJson.toByteArray(StandardCharsets.UTF_8))
            if (encodedRequest.toByteArray(StandardCharsets.UTF_8).size > MAX_ENCODED_REQUEST_BYTES) {
                return@withContext GitHubWorkflowDispatchResult(
                    false, requestId, null, workflowUrl, "Base64 development request exceeds the 32 KiB workflow input limit."
                )
            }
            val token = tokenProvider()?.takeIf { it.startsWith("github_pat_") }
                ?: return@withContext GitHubWorkflowDispatchResult(
                    false, requestId, null, workflowUrl, "A fine-grained GitHub Actions token is not configured."
                )

            val body = JSONObject()
                .put("ref", "main")
                .put(
                    "inputs",
                    JSONObject()
                        .put("request_id", requestId)
                        .put("request_json_b64", encodedRequest)
                )
                .toString()
            val endpoint = "https://api.github.com/repos/$owner/$repository/actions/workflows/$workflowFile/dispatches"

            try {
                val response = transport.post(endpoint, token, body)
                if (response.statusCode == 204) {
                    GitHubWorkflowDispatchResult(
                        true,
                        requestId,
                        response.statusCode,
                        workflowUrl,
                        "Request accepted by GitHub Actions. This confirms dispatch only, not build success."
                    )
                } else {
                    GitHubWorkflowDispatchResult(
                        false,
                        requestId,
                        response.statusCode,
                        workflowUrl,
                        "GitHub rejected the workflow dispatch (HTTP ${response.statusCode}). Check token scope and repository-owner authorization."
                    )
                }
            } catch (_: Exception) {
                GitHubWorkflowDispatchResult(
                    false,
                    requestId,
                    null,
                    workflowUrl,
                    "Could not reach GitHub Actions. The credential was not written to logs."
                )
            }
        }

    suspend fun checkStatus(requestId: String): GitHubWorkflowRunStatus = withContext(Dispatchers.IO) {
        if (!REQUEST_ID.matches(requestId)) {
            return@withContext GitHubWorkflowRunStatus(
                requestId, false, "INVALID_REQUEST", message = "Invalid development request identifier."
            )
        }
        val token = tokenProvider()?.takeIf { it.startsWith("github_pat_") }
            ?: return@withContext GitHubWorkflowRunStatus(
                requestId, false, "NOT_CONFIGURED", message = "Repository-builder credential is not configured."
            )
        val url = "https://api.github.com/repos/$owner/$repository/actions/runs?event=workflow_dispatch&branch=main&per_page=30"
        try {
            val response = transport.get(url, token)
            if (response.statusCode != 200) {
                return@withContext GitHubWorkflowRunStatus(
                    requestId, false, "QUERY_FAILED", message = "GitHub run lookup failed (HTTP ${response.statusCode})."
                )
            }
            val runs = JSONObject(response.body).optJSONArray("workflow_runs")
                ?: return@withContext GitHubWorkflowRunStatus(
                    requestId, false, "NOT_FOUND", message = "No workflow run has appeared for this request yet."
                )
            val run = (0 until runs.length())
                .mapNotNull { runs.optJSONObject(it) }
                .firstOrNull { candidate ->
                    val title = candidate.optString("display_title")
                    title.contains(requestId, ignoreCase = true)
                }
                ?: return@withContext GitHubWorkflowRunStatus(
                    requestId, false, "NOT_FOUND", message = "No workflow run has appeared for this request yet."
                )
            val status = run.optString("status", "unknown")
            val conclusion = run.optString("conclusion").takeIf { it.isNotBlank() && it != "null" }
            val state = when {
                status != "completed" -> "RUNNING"
                conclusion == "success" -> "SUCCEEDED"
                conclusion == "failure" -> "FAILED"
                conclusion == "cancelled" -> "CANCELLED"
                else -> "COMPLETED"
            }
            GitHubWorkflowRunStatus(
                requestId = requestId,
                found = true,
                state = state,
                conclusion = conclusion,
                runUrl = run.optString("html_url").takeIf { it.isNotBlank() },
                message = "GitHub workflow state: $state."
            )
        } catch (_: Exception) {
            GitHubWorkflowRunStatus(
                requestId, false, "QUERY_FAILED", message = "Could not query GitHub Actions. The credential was not written to logs."
            )
        }
    }

    private fun workflowUrl() =
        "https://github.com/$owner/$repository/actions/workflows/$workflowFile"

    companion object {
        const val DEFAULT_OWNER = "mhw77655-hue"
        const val DEFAULT_REPOSITORY = "Friday"
        const val DEFAULT_WORKFLOW = "friday-builder-proposal.yml"
        const val MAX_ENCODED_REQUEST_BYTES = 32 * 1024
        val REQUEST_ID = Regex("^[a-f0-9]{32}$")
    }
}
