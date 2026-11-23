package ai.friday.assistant.github.reporting

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Explicit, owner-authorized upload of sanitized phone diagnostics to this
 * repository. Each report is a GitHub issue; the companion workflow archives
 * its validated JSON as a short-lived Actions artifact.
 */
class FridayGitHubReportClient(
    private val token: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val apiRoot: String = API_ROOT
) {
    init {
        require(GitHubCredentialStore.isPlausibleToken(token)) { "GitHub token is not configured." }
    }

    /** Verifies repository and issue-list access without creating a test issue. */
    fun verifyRepositoryAccess(): String {
        val request = requestBuilder(apiRoot + "/issues?state=all&per_page=1").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(accessError(response.code))
            return "GitHub access verified for " + REPOSITORY +
                ". Friday can read the issue list; report creation will be verified on first upload."
        }
    }

    /**
     * Uploads a report as a GitHub issue. A stable report id in the title makes
     * retries idempotent when GitHub accepted a request but the phone lost the
     * response before marking its local operation complete.
     */
    fun submitDeviceReport(reportJson: String): String {
        val payload = FridayGitHubReportPayload.parse(reportJson)
        val title = FridayGitHubReportPayload.issueTitle(payload)
        findExistingIssue(title)?.let { return it }

        val body = FridayGitHubReportPayload.issueBody(payload)
        val requestBody = JSONObject()
            .put("title", title)
            .put("body", body)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = requestBuilder(apiRoot + "/issues").post(requestBody).build()

        http.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(accessError(response.code))
            val issue = runCatching { JSONObject(responseText) }.getOrNull()
                ?: throw IOException("GitHub accepted the request but returned an unreadable issue response.")
            return issue.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                ?: throw IOException("GitHub created the report but did not return its issue URL.")
        }
    }

    private fun findExistingIssue(title: String): String? {
        val request = requestBuilder(apiRoot + "/issues?state=all&per_page=100").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(accessError(response.code))
            val issues = runCatching { JSONArray(response.body?.string().orEmpty()) }.getOrNull()
                ?: throw IOException("GitHub returned an unreadable issue list.")
            for (index in 0 until issues.length()) {
                val issue = issues.optJSONObject(index) ?: continue
                if (!issue.has("pull_request") && issue.optString("title") == title) {
                    return issue.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                }
            }
        }
        return null
    }

    private fun requestBuilder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer " + token)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "Friday-Android-Device-Reporter")

    private fun accessError(status: Int): String = when (status) {
        401 -> "GitHub rejected the credential. Check the token and replace it if expired."
        403 -> "GitHub denied access. The fine-grained token needs Issues read and write permission for this repository."
        404 -> "The token cannot access this repository, or the repository is unavailable."
        422 -> "GitHub rejected the report format. Friday did not mark it as delivered."
        429 -> "GitHub rate-limited the request. Friday can retry later."
        else -> "GitHub request failed with HTTP " + status + ". Friday did not mark the report as delivered."
    }

    companion object {
        const val REPOSITORY = "mhw77655-hue/Friday"
        private const val API_ROOT = "https://api.github.com/repos/" + REPOSITORY
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Pure validation and formatting boundary, also exercised by JVM tests. */
internal object FridayGitHubReportPayload {
    private const val MAX_REPORT_BYTES = 40 * 1024
    private val secretPattern = Regex(
        "(?i)(github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|sk-(?:proj-)?[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|xox[baprs]-[A-Za-z0-9-]{20,}|bearer\\s+[A-Za-z0-9._-]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)"
    )

    fun parse(reportJson: String): JSONObject {
        require(reportJson.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES) {
            "Diagnostic report exceeds the 40 KiB upload limit."
        }
        require(!secretPattern.containsMatchIn(reportJson)) {
            "Diagnostic report was blocked because it resembles a credential."
        }
        val payload = runCatching { JSONObject(reportJson) }
            .getOrElse { throw IllegalArgumentException("Diagnostic report is not valid JSON.") }
        require(payload.optString("report_type") == "FRIDAY_LIVE_TEST_REPORT") {
            "Unsupported diagnostic report type."
        }
        require(payload.optInt("schema_version", -1) == 1) {
            "Unsupported diagnostic report schema."
        }
        val reportId = payload.optString("report_id")
        require(reportId.matches(Regex("[A-Za-z0-9-]{8,80}"))) {
            "Diagnostic report has no valid stable report id."
        }
        return payload
    }

    fun issueTitle(payload: JSONObject): String =
        "[Friday device report] " + payload.getString("report_id")

    fun issueBody(payload: JSONObject): String = buildString {
        appendLine("Automated Friday phone diagnostic report.")
        appendLine()
        appendLine("This report was submitted by Friday under the owner's explicit consent for sanitized automatic device reporting.")
        appendLine("It contains bounded technical diagnostics and action categories, not conversation content.")
        appendLine()
        appendLine("FRIDAY_DEVICE_REPORT_JSON_BEGIN")
        append(payload.toString(2))
        appendLine()
        appendLine("FRIDAY_DEVICE_REPORT_JSON_END")
    }.also {
        require(it.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES + 1024) {
            "Formatted diagnostic report exceeds the GitHub issue limit."
        }
    }
}
