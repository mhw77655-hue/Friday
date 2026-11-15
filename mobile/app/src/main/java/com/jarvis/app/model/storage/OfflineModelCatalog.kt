package com.jarvis.app.model.storage

/** Downloadable app-private models. Model weights are replaceable skills, not identity. */
data class OfflineModelAsset(
    val id: String, val displayName: String, val fileName: String,
    val downloadUrl: String, val expectedBytes: Long,
    val licenseName: String, val licenseUrl: String, val role: String
)

object OfflineModelCatalog {
    val resident = OfflineModelAsset(
        id = "lfm2.5-1.2b-instruct",
        displayName = "LFM2.5 1.2B Instruct · Q4_K_M",
        fileName = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf?download=true",
        expectedBytes = 766_509_056L,
        licenseName = "LFM Open License (check upstream terms)",
        licenseUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct",
        role = "Small resident model candidate"
    )
    val reasoning = OfflineModelAsset(
        id = "qwen3-1.7b-q4",
        displayName = "Qwen3 1.7B · Q4_K_M",
        fileName = "Qwen3-1.7B-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true",
        expectedBytes = 1_282_000_000L,
        licenseName = "Apache-2.0",
        licenseUrl = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF",
        role = "Deeper reasoning candidate"
    )
    val all = listOf(resident, reasoning)
}
