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

fun interface GitHubDispatchTransport {
    fun post(url: String, token: String, body: String): GitHubDispatchResponse
}

class HttpUrlConnectionGitHubDispatchTransport : GitHubDispatchTransport {
    override fun post(url: String, token: String, body: String): GitHubDispatchResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText().take(2_000) }.orEmpty()
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

/**
 * Dispatches only the Friday proposal workflow. The phone token has Actions:write
 * but no Contents:write; all repository edits happen in the isolated Actions
 * worker and trusted publisher jobs.
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
            val workflowUrl = "https://github.com/$owner/$repository/actions/workflows/$workflowFile"
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

    companion object {
        const val DEFAULT_OWNER = "mhw77655-hue"
        const val DEFAULT_REPOSITORY = "Friday"
        const val DEFAULT_WORKFLOW = "friday-builder-proposal.yml"
        const val MAX_ENCODED_REQUEST_BYTES = 32 * 1024
        val REQUEST_ID = Regex("^[a-f0-9]{32}$")
    }
}
