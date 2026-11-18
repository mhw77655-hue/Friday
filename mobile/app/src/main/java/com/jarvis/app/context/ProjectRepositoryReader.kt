package com.jarvis.app.context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Bounded, read-only source retrieval for adviser context.
 * Friday does not clone or upload the entire repository: it fetches at most
 * three explicitly requested text files from the public project repository.
 */
class ProjectRepositoryReader(
    private val owner: String = "mhw77655-hue",
    private val repository: String = "Friday",
    private val branch: String = "main",
    private val maxFiles: Int = 3,
    private val maxCharsPerFile: Int = 4_000,
    private val maxTotalChars: Int = 8_000
) {
    init {
        require(owner.matches(Regex("[A-Za-z0-9_.-]{1,80}")))
        require(repository.matches(Regex("[A-Za-z0-9_.-]{1,100}")))
        require(branch.matches(Regex("[A-Za-z0-9_./-]{1,120}")) && ".." !in branch)
        require(maxFiles in 1..3 && maxCharsPerFile in 512..8_000 && maxTotalChars in 512..8_000)
    }

    suspend fun readFiles(requestedPaths: List<String>): String = withContext(Dispatchers.IO) {
        val paths = requestedPaths.map { it.trim() }.distinct().take(maxFiles)
        if (paths.isEmpty()) return@withContext ""
        val out = StringBuilder("SELECTED REPOSITORY SOURCES (read-only; branch=$branch)\n")
        for (path in paths) {
            if (!isAllowedPath(path)) {
                appendBounded(out, "\n[REJECTED SOURCE PATH: $path]\n", maxTotalChars)
                continue
            }
            val encodedPath = path.split('/').joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val url = URL("https://raw.githubusercontent.com/$owner/$repository/$branch/$encodedPath")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "text/plain")
            }
            try {
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) {
                    appendBounded(out, "\n[SOURCE UNAVAILABLE: $path · HTTP $status]\n", maxTotalChars)
                    continue
                }
                val remaining = maxTotalChars - out.length
                if (remaining <= 160) break
                val fileLimit = minOf(maxCharsPerFile, remaining - 120)
                val content = readBounded(connection, fileLimit)
                appendBounded(out, "\n--- SOURCE: $path ---\n$content\n--- END SOURCE ---\n", maxTotalChars)
            } catch (e: Exception) {
                appendBounded(out, "\n[SOURCE FETCH FAILED: $path · ${e.message ?: "network error"}]\n", maxTotalChars)
            } finally {
                connection.disconnect()
            }
        }
        out.toString()
    }

    private fun readBounded(connection: HttpURLConnection, limit: Int): String {
        val out = StringBuilder()
        BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
            while (out.length < limit) {
                val line = reader.readLine() ?: break
                val remaining = limit - out.length
                out.append(line.take(remaining))
                if (out.length < limit) out.append('\n')
            }
        }
        if (out.length >= limit) out.append("\n[TRUNCATED AT ${limit} CHARACTERS]")
        return out.toString()
    }

    private fun isAllowedPath(path: String): Boolean {
        if (path.isBlank() || path.length > 240 || path.startsWith('/') || '\\' in path) return false
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return false
        val lower = path.lowercase()
        if (listOf(".env", "local.properties", "keystore", "secret", "token", "credential", ".jks", ".keystore", ".pem", ".p12", ".apk").any { it in lower }) return false
        val approvedRalphFiles = setOf(
            ".ralph/VISION.md",
            ".ralph/OWNER_WORDS.md",
            ".ralph/REPO_FACTS.md",
            ".ralph/FRIDAY_PROJECT_CONTEXT_PACK.json",
            ".ralph/AI_COUNCIL_CONNECTORS_SHARED_MEMORY_20261118.md"
        )
        val allowedPrefix = path in approvedRalphFiles ||
            path.startsWith("mobile/app/src/main/") ||
            path.startsWith("mobile/app/src/test/") ||
            path.startsWith(".github/workflows/") ||
            path.startsWith("gradle/") ||
            path == "mobile/app/build.gradle.kts" ||
            path == "settings.gradle.kts" ||
            path == "build.gradle.kts"
        if (!allowedPrefix) return false
        return listOf(".kt", ".kts", ".java", ".md", ".json", ".yml", ".yaml", ".xml", ".toml", ".txt", ".properties", ".gradle")
            .any { lower.endsWith(it) }
    }

    private fun appendBounded(target: StringBuilder, text: String, limit: Int) {
        if (target.length >= limit) return
        target.append(text.take(limit - target.length))
    }
}
