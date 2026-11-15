package com.jarvis.app.model.storage

import android.content.Context
import java.io.File

/**
 * Canonical local-model resolver. Friday's app-private files/models vault is
 * authoritative; legacy external paths remain migration-only fallbacks.
 */
class ModelPathResolver(private val context: Context) {
    companion object {
        private val MODEL_FILENAMES = mapOf(
            "qwen3.5-0.8b-abliterated" to "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf",
            "qwen3.5-2b-uncensored" to "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
            "spark-x2.6-4b-abliterated" to "Spark-X2.6-4B-Abliterated-Q4_K_M.gguf",
            "resident" to "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf",
            "jarvis-resident:latest" to "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf",
            "jarvis-reasoning:latest" to "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
            // Retained only so older installs can still resolve existing files.
            "lfm2.5-1.2b-instruct" to "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "lfm" to "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "qwen3-1.7b" to "Qwen3-1.7B-Q4_K_M.gguf",
            "qwen3-1.7b-q4" to "Qwen3-1.7B-Q4_K_M.gguf",
            "qwen3-1.7b-q4_k_m" to "Qwen3-1.7B-Q4_K_M.gguf"
        )

        fun modelFileName(modelId: String?): String {
            val id = modelId?.trim().orEmpty()
            MODEL_FILENAMES[id.lowercase()]?.let { return it }
            if (id.endsWith(".gguf", ignoreCase = true) &&
                !id.contains("/") && !id.contains("\\")) return id
            return "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf"
        }

        private fun looksLikeAbsoluteFilePath(value: String): Boolean =
            value.startsWith("/") || value.startsWith("file://")

        private fun isInside(child: File, parent: File): Boolean =
            child == parent || child.path.startsWith(parent.path + File.separator)

    }

    fun resolve(configuredPath: String?, modelId: String?): File? {
        val fileName = modelFileName(modelId)
        configuredPath?.trim()?.takeIf { it.isNotBlank() }?.let { configured ->
            if (looksLikeAbsoluteFilePath(configured)) {
                val explicit = runCatching { File(configured.removePrefix("file://")).canonicalFile }.getOrNull()
                val privateRoot = runCatching { context.filesDir.canonicalFile }.getOrNull()
                val externalAppRoot = runCatching { context.getExternalFilesDir(null)?.canonicalFile }.getOrNull()
                val appOwned = explicit != null && (
                    (privateRoot != null && isInside(explicit, privateRoot)) ||
                    (externalAppRoot != null && isInside(explicit, externalAppRoot))
                )
                if (explicit != null && appOwned && explicit.isFile && explicit.canRead()) return explicit
            }
        }

        val privateFile = File(context.filesDir, "models/$fileName")
        if (privateFile.isFile && privateFile.canRead()) return privateFile

        context.getExternalFilesDir(null)?.let { externalRoot ->
            val external = File(externalRoot, "models/$fileName")
            if (external.isFile && external.canRead()) return external
        }

        return null
    }
}
