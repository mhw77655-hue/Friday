package ai.friday.assistant.model.storage

import android.content.Context
import java.io.File

/**
 * Canonical local-model file resolver.
 *
 * FRIDAY keeps large GGUF models outside the APK.
 *
 * Resolution order:
 * 1. Explicit path only when it resolves inside app-owned storage.
 * 2. Friday's app-private files/models/ (the authoritative model vault).
 * 3. App-specific external files/models/ (scoped-storage migration fallback).
 *
 * IMPORTANT:
 * - Model IDs may be aliases rather than filenames.
 * - APK assets are intentionally NOT used as a GGUF fallback.
 */
class ModelPathResolver(
    private val context: Context
) {

    companion object {
        private val MODEL_FILENAMES = mapOf(
            "minicpm5-2b-heretic" to "MiniCPM5-2B-heretic-Q4_K_M.gguf",
            "resident" to "MiniCPM5-2B-heretic-Q4_K_M.gguf",
            "friday-resident:latest" to "MiniCPM5-2B-heretic-Q4_K_M.gguf",
            "lfm2.5-1.2b-abliterated" to "Huihui-LFM2.5-1.2B-Instruct-abliterated.Q4_K_S.gguf",
            "qwen3.5-0.8b-abliterated" to "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf",
            "lfm2.5-1.2b-instruct-abliterated" to "Huihui-LFM2.5-1.2B-Instruct-abliterated.Q4_K_S.gguf",
            "lfm" to "Huihui-LFM2.5-1.2B-Instruct-abliterated.Q4_K_S.gguf",
            "fallback" to "Huihui-LFM2.5-1.2B-Instruct-abliterated.Q4_K_S.gguf",
            "friday-reasoning:latest" to "Spark-X2.6-4B-Abliterated-Q4_K_M.gguf",
            "spark-x2.6-4b-abliterated" to "Spark-X2.6-4B-Abliterated-Q4_K_M.gguf",
            "qwen3.5-2b-abliterated" to "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
            "qwen3.5-2b-uncensored" to "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
            "qwen3.5-2b" to "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf"
        )

        fun modelFileName(modelId: String?): String {
            val id = modelId?.trim()?.lowercase().orEmpty()
            return MODEL_FILENAMES[id] ?: if (id.endsWith(".gguf")) id else ""
        }

        private fun looksLikeAbsoluteFilePath(value: String): Boolean =
            value.startsWith("/") || value.startsWith("file://")

        private fun isInside(child: File, parent: File): Boolean =
            child == parent || child.path.startsWith(parent.path + File.separator)
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
                    val explicit = runCatching { File(normalized).canonicalFile }.getOrNull()
                    val privateRoot = runCatching { context.filesDir.canonicalFile }.getOrNull()
                    val externalAppRoot = runCatching { context.getExternalFilesDir(null)?.canonicalFile }.getOrNull()
                    val appOwned = explicit != null && (
                        (privateRoot != null && isInside(explicit, privateRoot)) ||
                        (externalAppRoot != null && isInside(explicit, externalAppRoot))
                    )
                    if (explicit != null && appOwned && explicit.isFile && explicit.canRead()) return explicit
                }
            }

        if (fileName.isBlank()) return null

        // 2. Friday's own private vault is authoritative. Offline inference
        // must not silently depend on a shared-storage or Termux-managed copy.
        val privateFile = File(context.filesDir, "models/$fileName")
        if (privateFile.isFile && privateFile.canRead()) {
            return privateFile
        }

        // 3. Legacy app-specific external storage, for migration only.
        context.getExternalFilesDir(null)?.let { externalRoot ->
            val external = File(externalRoot, "models/$fileName")
            if (external.isFile && external.canRead()) return external
        }

        return null
    }
}
