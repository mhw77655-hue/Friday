package com.jarvis.app.model.storage

data class OfflineModelAsset(
    val id: String,
    val displayName: String,
    val repository: String,
    val revision: String,
    val filename: String,
    val license: String,
    val description: String,
    val expectedMinimumBytes: Long,
    val resident: Boolean,
    val sha256: String? = null
) {
    val downloadUrl: String
        get() = "https://huggingface.co/$repository/resolve/$revision/$filename?download=true"
}

object OfflineModelCatalog {
    val resident = OfflineModelAsset(
        id = "qwen3.5-0.8b-abliterated",
        displayName = "Qwen3.5 0.8B Abliterated · Q4_K_M",
        repository = "xdchinza/Qwen3.5-0.8B-abliterated-GGUF",
        revision = "6cef073",
        filename = "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Low-memory reduced-refusal resident fallback. Approximately 517 MB; runtime memory is higher.",
        expectedMinimumBytes = 450L * 1024L * 1024L,
        resident = true
    )

    val reasoning = OfflineModelAsset(
        id = "qwen3.5-2b-uncensored",
        displayName = "Qwen3.5 2B Uncensored · Q4_K_M",
        repository = "HauhauCS/Qwen3.5-2B-Uncensored-HauhauCS-Aggressive",
        revision = "cafbddc",
        filename = "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Primary reduced-refusal reasoning candidate. Approximately 1.27 GB; load only with NAE headroom.",
        expectedMinimumBytes = 1_100L * 1024L * 1024L,
        resident = false,
        sha256 = "be3ccca13a9d1bc8b67165ea80bd103cd6151a37ad131183cc7ca388f78d9517"
    )

    val all: List<OfflineModelAsset> = listOf(resident, reasoning)
}
