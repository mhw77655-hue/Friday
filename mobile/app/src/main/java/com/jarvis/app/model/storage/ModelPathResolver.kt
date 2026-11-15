package com.jarvis.app.model.storage

import android.content.Context
import java.io.File

/** App-private GGUF resolver; shared storage is migration fallback only. */
class ModelPathResolver(private val context: Context) {
    companion object {
        const val CANONICAL_MODEL_ROOT = "/sdcard/jarvis-repo/models"
        private val MODEL_FILENAMES = mapOf(
            "lfm2.5-1.2b-instruct" to "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "lfm" to "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "resident" to "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "jarvis-resident:latest" to "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "qwen3-1.7b" to "Qwen3-1.7B-Q4_K_M.gguf",
            "qwen3-1.7b-q4" to "Qwen3-1.7B-Q4_K_M.gguf",
            "qwen3-1.7b-q4_k_m" to "Qwen3-1.7B-Q4_K_M.gguf",
            "jarvis-reasoning:latest" to "Qwen3-1.7B-Q4_K_M.gguf",
            "dolphin3.0-llama3.1-8b-q4" to "Dolphin3.0-Llama3.1-8B.Q4_K_M.gguf"
        )
        fun modelFileName(modelId: String?): String {
            val id = modelId?.trim().orEmpty()
            MODEL_FILENAMES[id.lowercase()]?.let { return it }
            if (id.endsWith(".gguf", true) && !id.contains("/") && !id.contains("\\")) return id
            return "LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
        }
        private fun looksAbsolute(value: String) = value.startsWith("/") || value.startsWith("file://")
    }
    fun resolve(configuredPath: String?, modelId: String?): File? {
        val name = modelFileName(modelId)
        configuredPath?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (looksAbsolute(it)) File(it.removePrefix("file://")).takeIf { f -> f.isFile && f.canRead() }?.let { f -> return f }
        }
        File(context.filesDir, "models/$name").takeIf { it.isFile && it.canRead() }?.let { return it }
        File(CANONICAL_MODEL_ROOT, name).takeIf { it.isFile && it.canRead() }?.let { return it }
        context.getExternalFilesDir(null)?.let { root ->
            File(root, "models/$name").takeIf { it.isFile && it.canRead() }?.let { return it }
        }
        return null
    }
}
