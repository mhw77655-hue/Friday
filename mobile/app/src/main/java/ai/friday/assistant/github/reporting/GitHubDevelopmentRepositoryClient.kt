package ai.friday.assistant.github.reporting

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.ZipInputStream
import java.util.concurrent.TimeUnit

/**
 * Narrow GitHub source-candidate publisher. It creates an isolated branch and
 * pull request; it cannot merge, publish a release, or access signing keys.
 */
class GitHubDevelopmentRepositoryClient(
    private val token: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiRoot: String = API_ROOT
) {
    init {
        require(GitHubCredentialStore.isPlausibleToken(token)) {
            "GitHub development credential is not configured."
        }
    }

    data class CandidatePullRequest(
        val branch: String,
        val commitSha: String,
        val url: String,
        val number: Int
    )

    fun verifyDevelopmentAccess(baseBranch: String = "main"): String {
        val head = getRef(baseBranch)
        val plan = getFile(".ralph/FRIDAY_DELIVERY_PLAN.md", baseBranch)
        require(plan.isNotBlank()) { "Canonical plan read returned empty content." }
        return "Repository read access verified at " + baseBranch + " (" + head.take(12) + "). " +
            "Candidate creation additionally requires Contents write and Pull requests write."
    }

    /**
     * Recover a branch that was created before an Android process died. The
     * exact existing commit is reused; no candidate code is regenerated.
     */
    fun recoverExistingCandidate(
        taskId: String,
        title: String,
        recoveryBody: String,
        baseBranch: String = "main"
    ): CandidatePullRequest? {
        require(taskId.matches(Regex("[A-Za-z0-9._-]{3,80}"))) { "Task id is malformed." }
        val branch = "friday/candidate/" + taskId.lowercase()
            .replace(Regex("[^a-z0-9._/-]"), "-")
        val commit = runCatching { getRef(branch) }.getOrNull() ?: return null
        val pr = findExistingPullRequest(branch, baseBranch)
            ?: createPullRequest(title, recoveryBody, branch, baseBranch)
        return CandidatePullRequest(branch, commit, pr.first, pr.second)
    }

    /** Publish a bounded candidate. Never writes directly to main and never merges. */
    fun publishCandidate(
        taskId: String,
        title: String,
        body: String,
        changes: Map<String, String>,
        baseBranch: String = "main"
    ): CandidatePullRequest {
        require(taskId.matches(Regex("[A-Za-z0-9._-]{3,80}"))) { "Task id is malformed." }
        require(title.isNotBlank() && title.length <= 180) { "Candidate title is invalid." }
        require(body.isNotBlank() && body.length <= 20_000) { "Candidate evidence body is invalid." }
        require(!SECRET_PATTERN.containsMatchIn(title + "\n" + body)) {
            "Candidate metadata was blocked because it resembles a credential."
        }
        validateChanges(changes)
        val candidateBranch = "friday/candidate/" + taskId.lowercase()
            .replace(Regex("[^a-z0-9._/-]"), "-")

        // Crash recovery: if a previous attempt created this task's branch,
        // reuse its exact commit and existing PR rather than generating a new
        // commit that conflicts with the immutable candidate ref.
        val existingCommit = runCatching { getRef(candidateBranch) }.getOrNull()
        if (existingCommit != null) {
            val existingPr = findExistingPullRequest(candidateBranch, baseBranch)
                ?: createPullRequest(title, body, candidateBranch, baseBranch)
            return CandidatePullRequest(candidateBranch, existingCommit, existingPr.first, existingPr.second)
        }

        val baseCommit = getRef(baseBranch)
        val baseTree = getCommitTree(baseCommit)
        val entries = JSONArray()
        changes.toSortedMap().forEach { (path, source) ->
            entries.put(
                JSONObject()
                    .put("path", path)
                    .put("mode", "100644")
                    .put("type", "blob")
                    .put("sha", createBlob(source))
            )
        }
        val tree = createTree(baseTree, entries)
        val commit = createCommit("Friday candidate: " + title, tree, baseCommit)
        createRef(candidateBranch, commit)
        val pr = createPullRequest(title, body, candidateBranch, baseBranch)
        return CandidatePullRequest(candidateBranch, commit, pr.first, pr.second)
    }

    /** External public Seats may see source only after this live visibility check. */
    fun isRepositoryPublic(): Boolean = !getJson(apiRoot).optBoolean("private", true)

    /** Fail closed before any candidate content is sent to a public advisory Seat. */
    fun validateExternalShareableCandidate(changes: Map<String, String>) {
        require(isRepositoryPublic()) { "Repository is private; candidate source cannot be shared externally." }
        validateChanges(changes)
    }

    /** Read a bounded commit diff for independent review; never executes candidate code. */
    fun readCommitDiff(commitSha: String): String {
        require(commitSha.matches(Regex("[0-9a-f]{40}"))) { "Candidate commit SHA is invalid." }
        val response = http.newCall(
            requestBuilder(apiRoot + "/commits/" + commitSha)
                .header("Accept", "application/vnd.github.v3.diff")
                .build()
        ).execute()
        response.use {
            if (!it.isSuccessful) throw IOException(apiError(it.code, "read candidate diff"))
            val diff = it.body?.string().orEmpty()
            require(diff.isNotBlank() && diff.length <= MAX_REVIEW_DIFF_CHARS) {
                "Candidate diff is empty or exceeds the independent-review budget."
            }
            return diff
        }
    }

    data class ReaInvestigationRun(
        val id: Long,
        val title: String,
        val status: String,
        val conclusion: String?,
        val url: String,
        val headSha: String
    )

    /**
     * Start the pinned REA investigation workflow. This is deliberately limited
     * to the repository-owned workflow file; the caller cannot dispatch arbitrary
     * workflow IDs or supply shell commands/targets.
     */
    fun dispatchReaInvestigation(investigationId: String, ref: String = "main") {
        require(investigationId.matches(Regex("[A-Za-z0-9._-]{8,80}"))) {
            "REA investigation id is malformed."
        }
        require(ref == "main") { "REA investigations may only run from the protected main ref." }
        val payload = JSONObject()
            .put("ref", ref)
            .put("inputs", JSONObject().put("investigation_id", investigationId))
        val response = request(
            apiRoot + "/actions/workflows/rea-investigation.yml/dispatches",
            "POST",
            payload.toString()
        )
        response.use {
            if (it.code == 204) return
            throw IOException(apiError(it.code, "dispatch the REA investigation workflow"))
        }
    }

    /** Find the exact workflow run associated with a Friday-generated investigation id. */
    fun findReaInvestigationRun(investigationId: String): ReaInvestigationRun? {
        require(investigationId.matches(Regex("[A-Za-z0-9._-]{8,80}"))) {
            "REA investigation id is malformed."
        }
        val json = getJson(
            apiRoot + "/actions/workflows/rea-investigation.yml/runs?branch=main&event=workflow_dispatch&per_page=50"
        )
        val runs = json.optJSONArray("workflow_runs") ?: JSONArray()
        val expectedTitle = "Friday REA investigation " + investigationId
        for (index in 0 until runs.length()) {
            val run = runs.optJSONObject(index) ?: continue
            val title = run.optString("display_title").ifBlank { run.optString("name") }
            if (title != expectedTitle && !title.endsWith(investigationId)) continue
            val id = run.optLong("id", -1L)
            val url = run.optString("html_url")
            if (id <= 0L || !url.startsWith("https://github.com/")) continue
            return ReaInvestigationRun(
                id = id,
                title = title,
                status = run.optString("status", "unknown"),
                conclusion = run.optString("conclusion").takeIf { value -> value.isNotBlank() && value != "null" },
                url = url,
                headSha = run.optString("head_sha")
            )
        }
        return null
    }

    /**
     * Download only the investigation summary from the run's evidence ZIP.
     * ZIP paths are never extracted to disk; size limits and exact entry matching
     * prevent path traversal and unbounded decompression.
     */
    fun downloadReaInvestigationSummary(runId: Long): String {
        require(runId > 0L) { "REA workflow run id is invalid." }
        val json = getJson(apiRoot + "/actions/runs/" + runId + "/artifacts?per_page=100")
        val artifacts = json.optJSONArray("artifacts") ?: JSONArray()
        val expectedName = "friday-rea-android-evidence-" + runId
        var artifactId = -1L
        for (index in 0 until artifacts.length()) {
            val artifact = artifacts.optJSONObject(index) ?: continue
            if (artifact.optString("name") == expectedName && !artifact.optBoolean("expired", true)) {
                artifactId = artifact.optLong("id", -1L)
                break
            }
        }
        require(artifactId > 0L) { "The completed REA run has no available evidence artifact." }
        val response = request(apiRoot + "/actions/artifacts/" + artifactId + "/zip", "GET", null)
        val archive = response.use {
            if (!it.isSuccessful) throw IOException(apiError(it.code, "download REA evidence"))
            val bytes = it.body?.bytes() ?: throw IOException("GitHub returned an empty REA evidence archive.")
            require(bytes.size <= MAX_REA_ARCHIVE_BYTES) { "REA evidence archive exceeds the download limit." }
            bytes
        }
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == "investigation-summary.json") {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4_096)
                    var total = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_REA_SUMMARY_BYTES) { "REA investigation summary exceeds the size limit." }
                        output.write(buffer, 0, count)
                    }
                    val summary = output.toString(StandardCharsets.UTF_8.name())
                    val parsed = runCatching { JSONObject(summary) }
                        .getOrElse { throw IOException("REA evidence summary is not valid JSON.") }
                    require(parsed.optInt("schema_version", -1) == 1) {
                        "REA evidence summary schema is unsupported."
                    }
                    return summary
                }
                zip.closeEntry()
            }
        }
        throw IOException("REA evidence archive did not contain investigation-summary.json.")
    }

    data class CandidateChecks(
        val state: String,
        val total: Int,
        val completed: Int,
        val failed: Int,
        val details: List<String>
    )

    /** Read-only CI status inspection for a candidate commit. */
    fun inspectCandidateChecks(commitSha: String): CandidateChecks {
        require(commitSha.matches(Regex("[0-9a-f]{40}"))) { "Candidate commit SHA is invalid." }
        val checks = getJson(apiRoot + "/commits/" + commitSha + "/check-runs?per_page=100")
        val runs = checks.optJSONArray("check_runs") ?: JSONArray()
        val details = mutableListOf<String>()
        var completed = 0
        var failed = 0
        for (index in 0 until runs.length()) {
            val run = runs.optJSONObject(index) ?: continue
            val name = run.optString("name", "unnamed-check").take(120)
            val status = run.optString("status")
            val conclusion = run.optString("conclusion")
            details += "$name:$status:$conclusion"
            if (status == "completed") {
                completed++
                if (conclusion !in setOf("success", "neutral", "skipped")) failed++
            }
        }
        val combined = getJson(apiRoot + "/commits/" + commitSha + "/status")
        val combinedState = combined.optString("state", "pending")
        val pendingStatuses = combined.optJSONArray("statuses") ?: JSONArray()
        val total = runs.length()
        val state = when {
            failed > 0 || combinedState == "failure" || combinedState == "error" -> "FAILURE"
            total > 0 && completed == total && combinedState == "success" -> "SUCCESS"
            // GitHub reports "pending" when there are no legacy commit
            // statuses as well as when one or more status contexts are still
            // pending. Only the empty-status case may rely solely on completed
            // Check Runs; never promote a genuinely pending context to green.
            total > 0 && completed == total && combinedState == "pending" && pendingStatuses.length() == 0 -> "SUCCESS"
            else -> "PENDING"
        }
        return CandidateChecks(state, total, completed, failed, details)
    }

    private fun getRef(branch: String): String {
        val json = getJson(apiRoot + "/git/ref/heads/" + encodePath(branch))
        return validSha(json.optJSONObject("object")?.optString("sha").orEmpty(), "branch")
    }

    private fun getCommitTree(commitSha: String): String {
        val json = getJson(apiRoot + "/git/commits/" + commitSha)
        return validSha(json.optJSONObject("tree")?.optString("sha").orEmpty(), "base tree")
    }

    /** Read a bounded repository source file for Friday's local Builder context. */
    fun readSourceFile(path: String, ref: String = "main"): String = getFile(path, ref)

    private fun getFile(path: String, ref: String): String {
        validatePath(path)
        val url = apiRoot + "/contents/" + encodePath(path) + "?ref=" + encode(ref)
        val json = getJson(url)
        require(json.optString("encoding") == "base64") {
            "GitHub did not return a base64 file payload."
        }
        val encoded = json.optString("content").replace("\\n", "")
        val bytes = runCatching {
            Base64.getDecoder().decode(encoded.replace("\n", "").replace("\r", ""))
        }.getOrElse { throw IOException("GitHub returned an unreadable source file.") }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun createBlob(content: String): String {
        val json = postJson(
            apiRoot + "/git/blobs",
            JSONObject().put("content", content).put("encoding", "utf-8")
        )
        return validSha(json.optString("sha"), "blob")
    }

    private fun createTree(baseTree: String, entries: JSONArray): String {
        val json = postJson(
            apiRoot + "/git/trees",
            JSONObject().put("base_tree", baseTree).put("tree", entries)
        )
        return validSha(json.optString("sha"), "tree")
    }

    private fun createCommit(message: String, tree: String, parent: String): String {
        val json = postJson(
            apiRoot + "/git/commits",
            JSONObject()
                .put("message", message)
                .put("tree", tree)
                .put("parents", JSONArray().put(parent))
        )
        return validSha(json.optString("sha"), "commit")
    }

    private fun createRef(branch: String, sha: String) {
        val response = request(
            apiRoot + "/git/refs",
            "POST",
            JSONObject().put("ref", "refs/heads/" + branch).put("sha", sha).toString()
        )
        response.use { result ->
            if (result.isSuccessful) return
            if (result.code == 422) {
                val existing = runCatching { getRef(branch) }.getOrNull()
                if (existing == sha) return
                throw IOException("Candidate branch already exists with different content; refusing to overwrite it.")
            }
            throw IOException(apiError(result.code, "create candidate branch"))
        }
    }

    private fun createPullRequest(
        title: String,
        body: String,
        head: String,
        base: String
    ): Pair<String, Int> {
        val payload = JSONObject().put("title", title).put("body", body)
            .put("head", REPOSITORY_OWNER + ":" + head).put("base", base)
        val response = request(apiRoot + "/pulls", "POST", payload.toString())
        response.use { result ->
            val text = result.body?.string().orEmpty()
            if (result.isSuccessful) {
                val pr = JSONObject(text)
                val url = pr.optString("html_url")
                val number = pr.optInt("number", -1)
                if (url.startsWith("https://github.com/") && number > 0) return url to number
                throw IOException("GitHub opened a PR but returned incomplete metadata.")
            }
            if (result.code == 422) {
                findExistingPullRequest(head, base)?.let { return it }
            }
            throw IOException(apiError(result.code, "open candidate pull request"))
        }
    }

    private fun findExistingPullRequest(head: String, base: String): Pair<String, Int>? {
        val query = apiRoot + "/pulls?state=open&base=" + encode(base) +
            "&head=" + encode(REPOSITORY_OWNER + ":" + head)
        val json = getArray(query)
        for (index in 0 until json.length()) {
            val pr = json.optJSONObject(index) ?: continue
            val url = pr.optString("html_url")
            val number = pr.optInt("number", -1)
            if (url.startsWith("https://github.com/") && number > 0) return url to number
        }
        return null
    }

    private fun getJson(url: String): JSONObject {
        val response = request(url, "GET", null)
        response.use {
            if (!it.isSuccessful) throw IOException(apiError(it.code, "read repository data"))
            return runCatching { JSONObject(it.body?.string().orEmpty()) }
                .getOrElse { throw IOException("GitHub returned an unreadable JSON response.") }
        }
    }

    private fun getArray(url: String): JSONArray {
        val response = request(url, "GET", null)
        response.use {
            if (!it.isSuccessful) throw IOException(apiError(it.code, "read pull requests"))
            return runCatching { JSONArray(it.body?.string().orEmpty()) }
                .getOrElse { throw IOException("GitHub returned an unreadable pull-request list.") }
        }
    }

    private fun postJson(url: String, payload: JSONObject): JSONObject {
        val response = request(url, "POST", payload.toString())
        response.use {
            if (!it.isSuccessful) throw IOException(apiError(it.code, "write candidate data"))
            return runCatching { JSONObject(it.body?.string().orEmpty()) }
                .getOrElse { throw IOException("GitHub returned an unreadable write response.") }
        }
    }

    private fun request(url: String, method: String, body: String?) =
        http.newCall(
            requestBuilder(url).method(method, body?.toRequestBody(JSON_MEDIA_TYPE)).build()
        ).execute()

    private fun requestBuilder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer " + token)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "Friday-Android-Development")

    private fun validateChanges(changes: Map<String, String>) {
        require(changes.isNotEmpty() && changes.size <= MAX_FILES) {
            "A candidate must contain between 1 and " + MAX_FILES + " changed files."
        }
        var totalBytes = 0
        changes.forEach { (path, source) ->
            validatePath(path)
            require(path.startsWith("mobile/app/src/main/java/") ||
                path.startsWith("mobile/app/src/test/java/") ||
                path.startsWith("mobile/app/src/androidTest/java/") ||
                path.startsWith("mobile/app/src/main/assets/friday/") ||
                path.startsWith("docs/") || path.startsWith(".ralph/")
            ) { "This publisher only accepts bounded app source, test, asset and documentation paths." }
            require(!path.contains("keystore", ignoreCase = true) &&
                !path.contains("secret", ignoreCase = true) &&
                !path.endsWith(".jks", ignoreCase = true) &&
                !path.endsWith(".p12", ignoreCase = true) &&
                !path.endsWith(".pem", ignoreCase = true)
            ) { "This publisher cannot change signing or secret files." }
            require(source.length <= MAX_FILE_CHARS) { "Candidate file is too large: " + path }
            require(!SECRET_PATTERN.containsMatchIn(source)) {
                "Candidate source was blocked because it resembles a credential."
            }
            totalBytes += source.toByteArray(StandardCharsets.UTF_8).size
        }
        require(totalBytes <= MAX_TOTAL_BYTES) { "Candidate exceeds the total patch size limit." }
    }

    private fun validatePath(path: String) {
        require(path.isNotBlank() && !path.startsWith("/") &&
            path.split('/').none { it.isBlank() || it == "." || it == ".." } &&
            !path.contains('\\')
        ) { "Candidate contains an unsafe repository path." }
    }

    private fun validSha(value: String, kind: String): String =
        value.takeIf { it.matches(Regex("[0-9a-f]{40}")) }
            ?: throw IOException("GitHub returned an invalid " + kind + " SHA.")

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { encode(it) }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun apiError(code: Int, operation: String): String = when (code) {
        401 -> "GitHub rejected Friday's credential while trying to " + operation + "."
        403 -> "GitHub denied permission to " + operation + ". Check the fine-grained token's repository and permission scopes."
        404 -> "The repository or requested source was not found, or the token cannot access it."
        409 -> "GitHub detected a repository conflict while trying to " + operation + "."
        422 -> "GitHub rejected the candidate request while trying to " + operation + "."
        429 -> "GitHub rate-limited Friday; the operation can be retried later."
        else -> "GitHub could not " + operation + " (HTTP " + code + ")."
    }

    companion object {
        const val REPOSITORY = "mhw77655-hue/Friday"
        private const val REPOSITORY_OWNER = "mhw77655-hue"
        private const val API_ROOT = "https://api.github.com/repos/" + REPOSITORY
        private const val MAX_FILES = 12
        private const val MAX_FILE_CHARS = 160_000
        private const val MAX_TOTAL_BYTES = 512 * 1024
        private const val MAX_REVIEW_DIFF_CHARS = 256_000
        private const val MAX_REA_ARCHIVE_BYTES = 20 * 1024 * 1024
        private const val MAX_REA_SUMMARY_BYTES = 2 * 1024 * 1024
        private val SECRET_PATTERN = Regex(
            "(?i)(github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|sk-(?:proj-)?[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|xox[baprs]-[A-Za-z0-9-]{20,}|bearer\\s+[A-Za-z0-9._-]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)"
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
