package com.jarvis.app.model.storage

import android.content.Context
import java.io.File

/**
 * Canonical local-model file resolver.
 *
 * JARVIS keeps large GGUF models outside the APK.
 *
 * Resolution order:
 * 1. Explicit absolute filesystem path.
 * 2. Canonical shared JARVIS model vault:
 *      /sdcard/jarvis-repo/models/
 * 3. App-specific external files/models/
 * 4. App-private files/models/
 *
 * IMPORTANT:
 * - Model IDs may be aliases rather than filenames.
 * - APK assets are intentionally NOT used as a GGUF fallback.
 */
class ModelPathResolver(
    private val context: Context
) {

    companion object {
        const val CANONICAL_MODEL_ROOT = "/sdcard/jarvis-repo/models"

        private val MODEL_FILENAMES = mapOf(
            "lfm2.5-1.2b-instruct" to "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "lfm" to "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "qwen3-1.7b" to "Qwen3-1.7B-Q8_0.gguf",
            "qwen3-1.7b-q8" to "Qwen3-1.7B-Q8_0.gguf",
            "qwen3-1.7b-q4" to "Qwen3-1.7B-Q4_K_M.gguf",
            "qwen3-1.7b-q4_k_m" to "Qwen3-1.7B-Q4_K_M.gguf"
        )

        fun modelFileName(modelId: String?): String {
            val id = modelId?.trim().orEmpty()

            MODEL_FILENAMES[id.lowercase()]?.let { return it }

            if (
                id.endsWith(".gguf", ignoreCase = true) &&
                !id.contains("/") &&
                !id.contains("\\")
            ) {
                return id
            }

            return "Qwen3-1.7B-Q4_K_M.gguf"
        }

        private fun looksLikeAbsoluteFilePath(value: String): Boolean {
            return value.startsWith("/") || value.startsWith("file://")
        }
    }

    /**
     * Resolve a real readable local model file.
     *
     * A ModelSource.Local value such as "127.0.0.1:8080" is an endpoint,
     * not a filesystem path, so it is deliberately ignored here.
     */
    fun resolve(
        configuredPath: String?,
        modelId: String?
    ): File? {

        val fileName = modelFileName(modelId)

        // 1. Explicit absolute filesystem path.
        configuredPath
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { configured ->
                if (looksLikeAbsoluteFilePath(configured)) {
                    val normalized = configured.removePrefix("file://")
                    val explicit = File(normalized)

                    if (explicit.isFile && explicit.canRead()) {
                        return explicit
                    }
                }
            }

        // 2. Canonical shared JARVIS model vault.
        val canonical = File(
            CANONICAL_MODEL_ROOT,
            fileName
        )

        if (canonical.isFile && canonical.canRead()) {
            return canonical
        }

        // 3. App-specific external storage.
        context.getExternalFilesDir(null)
            ?.let { externalRoot ->
                val external = File(
                    externalRoot,
                    "models/$fileName"
                )

                if (external.isFile && external.canRead()) {
                    return external
                }
            }

        // 4. App-private storage.
        val private = File(
            context.filesDir,
            "models/$fileName"
        )

        if (private.isFile && private.canRead()) {
            return private
        }

        return null
    }
}
