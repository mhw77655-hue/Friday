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
    // Official Liquid AI GGUF, immutable HF revision. Q4_K_M is approximately 731 MB.
    val resident = OfflineModelAsset(
        id = "lfm2.5-1.2b-instruct",
        displayName = "LFM2.5 1.2B Instruct · Q4_K_M",
        repository = "LiquidAI/LFM2.5-1.2B-Instruct-GGUF",
        revision = "76022b8",
        filename = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
        license = "LFM 1.0",
        description = "Compact on-device resident candidate. Approximately 731 MB; runtime memory is higher.",
        expectedMinimumBytes = 600L * 1024L * 1024L,
        resident = true
    )

    // Official ggml-org GGUF, immutable HF revision, Apache-2.0. Q4_K_M ~1.28 GB.
    val reasoning = OfflineModelAsset(
        id = "qwen3-1.7b-q4",
        displayName = "Qwen3 1.7B · Q4_K_M",
        repository = "ggml-org/Qwen3-1.7B-GGUF",
        revision = "daeb8e2",
        filename = "Qwen3-1.7B-Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Optional reasoning worker. Approximately 1.28 GB; load only with resource headroom.",
        expectedMinimumBytes = 1_000L * 1024L * 1024L,
        resident = false
    )

    val all: List<OfflineModelAsset> = listOf(resident, reasoning)
}
