package ai.friday.assistant.github.reporting

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
        validateChanges(changes)

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
        val candidateBranch = "friday/candidate/" + taskId.lowercase()
            .replace(Regex("[^a-z0-9._/-]"), "-")
        createRef(candidateBranch, commit)
        val pr = createPullRequest(title, body, candidateBranch, baseBranch)
        return CandidatePullRequest(candidateBranch, commit, pr.first, pr.second)
    }

    private fun getRef(branch: String): String {
        val json = getJson(apiRoot + "/git/ref/heads/" + encodePath(branch))
        return validSha(json.optJSONObject("object")?.optString("sha").orEmpty(), "branch")
    }

    private fun getCommitTree(commitSha: String): String {
        val json = getJson(apiRoot + "/git/commits/" + commitSha)
        return validSha(json.optJSONObject("tree")?.optString("sha").orEmpty(), "base tree")
    }

    private fun getFile(path: String, ref: String): String {
        validatePath(path)
        val url = apiRoot + "/contents/" + encodePath(path) + "?ref=" + encode(ref)
        val json = getJson(url)
        require(json.optString("encoding") == "base64") {
            "GitHub did not return a base64 file payload."
        }
        val encoded = json.optString("content").replace("\\n", "")
        val bytes = runCatching {
            android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
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
            .put("head", head).put("base", base)
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
            require(!path.startsWith(".github/workflows/") &&
                !path.startsWith("gradle/wrapper/") &&
                !path.contains("keystore", ignoreCase = true) &&
                !path.contains("secret", ignoreCase = true)
            ) { "This publisher cannot change workflow authority, signing or secret files." }
            require(source.length <= MAX_FILE_CHARS) { "Candidate file is too large: " + path }
            totalBytes += source.toByteArray(StandardCharsets.UTF_8).size
        }
        require(totalBytes <= MAX_TOTAL_BYTES) { "Candidate exceeds the total patch size limit." }
    }

    private fun validatePath(path: String) {
        require(path.isNotBlank() && !path.startsWith("/") &&
            path.split('/').none { it.isBlank() || it == "." || it == ".." } &&
            !path.contains('\\\\')
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
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
