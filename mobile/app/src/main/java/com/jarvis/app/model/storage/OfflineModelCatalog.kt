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
    // Community refusal-ablated LFM2.5 1.2B. Q4_K_M is ~731 MB.
    // Kept as the low-memory resident so ordinary turns stay light.
    val resident = OfflineModelAsset(
        id = "lfm2.5-1.2b-abliterated",
        displayName = "Huihui LFM2.5 1.2B Abliterated · Q4_K_M",
        repository = "mradermacher/Huihui-LFM2.5-1.2B-Instruct-abliterated-GGUF",
        revision = "7f2f76e",
        filename = "Huihui-LFM2.5-1.2B-Instruct-abliterated.Q4_K_M.gguf",
        license = "LFM 1.0",
        description = "Low-memory English resident candidate with reduced refusal behaviour. About 731 MB; inference memory is higher.",
        expectedMinimumBytes = 600L * 1024L * 1024L,
        resident = true
    )

    // Community refusal-ablated Qwen3.5 2B. Q4_K_M is ~1.27 GB.
    // Loaded only when the resource governor admits the reasoning tier.
    val reasoning = OfflineModelAsset(
        id = "qwen3.5-2b-abliterated",
        displayName = "Huihui Qwen3.5 2B Abliterated · Q4_K_M",
        repository = "mradermacher/Huihui-Qwen3.5-2B-abliterated-GGUF",
        revision = "f36848f",
        filename = "Huihui-Qwen3.5-2B-abliterated.Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Stronger English reasoning candidate, loaded on demand only when NAE reports enough headroom. About 1.27 GB; inference memory is higher.",
        expectedMinimumBytes = 1_000L * 1024L * 1024L,
        resident = false
    )

    val all: List<OfflineModelAsset> = listOf(resident, reasoning)
}
