package com.jarvis.app.model.storage

/** Public GGUF assets installed into Friday's private model directory. */
data class OfflineModelAsset(
    val id: String,
    val displayName: String,
    val repository: String,
    val revision: String,
    val filename: String,
    val license: String,
    val description: String,
    val expectedMinimumBytes: Long,
    val resident: Boolean
) {
    val downloadUrl: String
        get() = "https://huggingface.co/$repository/resolve/$revision/$filename?download=true"
}

object OfflineModelCatalog {
    val resident = OfflineModelAsset(
        id = "lfm2.5-1.2b-instruct",
        displayName = "LFM2.5 1.2B Instruct · Q4_K_M",
        repository = "LiquidAI/LFM2.5-1.2B-Instruct-GGUF",
        revision = "main",
        filename = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
        license = "LFM Open License 1.0",
        description = "Compact on-device resident model. Approximately 731 MB; runtime memory is higher.",
        expectedMinimumBytes = 600L * 1024L * 1024L,
        resident = true
    )

    val reasoning = OfflineModelAsset(
        id = "qwen3-1.7b-q4",
        displayName = "Qwen3 1.7B · Q4_K_M",
        repository = "ggml-org/Qwen3-1.7B-GGUF",
        revision = "main",
        filename = "Qwen3-1.7B-Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Optional reasoning worker. Approximately 1.28 GB; load only with resource headroom.",
        expectedMinimumBytes = 1_000L * 1024L * 1024L,
        resident = false
    )

    val all: List<OfflineModelAsset> = listOf(resident, reasoning)
}
