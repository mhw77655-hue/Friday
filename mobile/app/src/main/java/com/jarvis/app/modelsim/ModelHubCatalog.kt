package com.jarvis.app.modelsim

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Read-only discovery of public GGUF repositories on Hugging Face.
 *
 * This is catalog discovery, not model execution: it never downloads weights,
 * maps model files, or claims that a discovered model is compatible. A file
 * without a trustworthy byte size is omitted rather than assigned a guess.
 */
class ModelHubCatalog(
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun discover(search: String = "instruct", limit: Int = 30): List<ModelCandidate> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceIn(1, 20)
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("huggingface.co")
                .addPathSegments("api/models")
                .addQueryParameter("filter", "gguf")
                .addQueryParameter("search", search.trim().take(80))
                .addQueryParameter("sort", "downloads")
                .addQueryParameter("direction", "-1")
                .addQueryParameter("limit", safeLimit.toString())
                .addQueryParameter("full", "true")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Friday-Model-Free-Device-Lab/1.0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Model catalog returned HTTP ${response.code}.")
                }
                val body = response.body?.string()
                    ?: throw IOException("Model catalog returned an empty response.")
                val listing = JSONArray(body)
                val repoIds = (0 until listing.length()).mapNotNull { index ->
                    listing.optJSONObject(index)?.optString("id")
                        ?.takeIf { it.contains("/") }
                }.distinct().take(safeLimit)

                // The listing endpoint supplies repository discovery. File byte
                // sizes require per-repository blob metadata; do not infer them
                // from names or total repository storage.
                val detailed = coroutineScope {
                    repoIds.map { repoId ->
                        async(Dispatchers.IO) { fetchRepositoryMetadata(repoId) }
                    }.awaitAll().filterNotNull()
                }
                val detailedArray = JSONArray().apply {
                    detailed.forEach { put(JSONObject(it)) }
                }
                parse(detailedArray.toString())
            }
        }

    private fun fetchRepositoryMetadata(repoId: String): String? {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("huggingface.co")
            .addPathSegments("api/models/$repoId")
            .addQueryParameter("blobs", "true")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Friday-Model-Free-Device-Lab/1.0")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    internal fun parse(json: String): List<ModelCandidate> {
        val repos = JSONArray(json)
        val candidates = mutableListOf<ModelCandidate>()
        for (index in 0 until repos.length()) {
            val repo = repos.optJSONObject(index) ?: continue
            val repoId = repo.optString("id").takeIf { it.contains("/") } ?: continue
            val revision = repo.optString("sha").takeIf { it.isNotBlank() }.orEmpty()
            val downloads = repo.optLong("downloads", 0L)
            val likes = repo.optLong("likes", 0L)
            val tags = repo.optJSONArray("tags").toStringSet()
            val siblings = repo.optJSONArray("siblings") ?: continue

            for (fileIndex in 0 until siblings.length()) {
                val file = siblings.optJSONObject(fileIndex) ?: continue
                val filename = file.optString("rfilename")
                if (!filename.endsWith(".gguf", ignoreCase = true)) continue
                val bytes = file.optLong("size", -1L).takeIf { it > 0L }
                    ?: file.optJSONObject("lfs")?.optLong("size", -1L)?.takeIf { it > 0L }
                    ?: continue
                val quant = QUANT_PATTERN.find(filename)?.value ?: "GGUF quantization unspecified"
                val params = PARAM_PATTERN.find(repoId)?.value ?: "Unknown parameters"
                val modality = when {
                    tags.any { it.contains("vision") || it.contains("image") } -> "Text + possible vision (verify model card)"
                    tags.any { it.contains("audio") } -> "Text + possible audio (verify model card)"
                    else -> "Text; modality requires model-card verification"
                }
                val mb = (bytes + MIB - 1L) / MIB
                candidates += ModelCandidate(
                    id = "$repoId/$filename",
                    name = "$repoId · $filename",
                    parameterLabel = params,
                    weightMb = mb,
                    runtime = "GGUF · llama.cpp compatibility must be checked",
                    quantization = quant,
                    modalities = modality,
                    notes = "Hub metadata only · downloads $downloads · likes $likes · revision ${revision.take(12)} · architecture, license, quality and refusal behaviour unverified."
                )
            }
        }
        return candidates.distinctBy { it.id }.sortedWith(
            compareBy<ModelCandidate> { quantizationRank(it.quantization) }
                .thenBy { it.weightMb }
        ).take(60)
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it).takeIf { value -> value.isNotBlank() } }.toSet()
    }

    private fun quantizationRank(value: String): Int = when {
        value.contains("Q4_K_M", true) -> 0
        value.contains("Q4_0", true) -> 1
        value.contains("Q5_K_M", true) -> 2
        value.contains("Q3_K", true) -> 3
        value.contains("Q6_K", true) -> 4
        else -> 5
    }

    companion object {
        private const val MIB = 1024L * 1024L
        private val PARAM_PATTERN = Regex("(?i)\\b\\d+(?:\\.\\d+)?B\\b")
        private val QUANT_PATTERN = Regex("(?i)Q[234568]_[A-Z0-9_]+")
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
