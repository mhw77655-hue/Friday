package ai.friday.assistant.model.storage

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
    val resident: Boolean,
    val sha256: String? = null
) {
    val downloadUrl: String
        get() = "https://huggingface.co/$repository/resolve/$revision/$filename?download=true"
}

object OfflineModelCatalog {
    // Primary resident candidate: MiniCPM5 2B Heretic. The model card reports
    // 4/100 refusals and KL divergence 0.0646; those are creator-reported tests,
    // not independent benchmarks. Q4_K_M is listed at ~1.3-1.56 GB.
    val resident = OfflineModelAsset(
        id = "minicpm5-2b-heretic",
        displayName = "MiniCPM5 2B Heretic · Q4_K_M",
        repository = "saidutta69/MiniCPM5-2B-heretic",
        // Pin the exact commit that introduced this GGUF file. The prior
        // 178a9e6 revision predates the Q4_K_M asset and returned 404.
        revision = "92fc4d623bd89e5c1a5dc4cc1130b9bc64cdc71d",
        filename = "MiniCPM5-2B-heretic-Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Primary English resident candidate. GGUF/llama.cpp supported; creator reports 4/100 refusals. NAE admits it only when live resources allow.",
        expectedMinimumBytes = 1_561_318_112L, // exact size of the pinned GGUF asset
        resident = true,
        sha256 = "54db1ccd73f771e9f13b6cf5a9994fc7e780898d6d2ca6f0e1715641f67697b4"
    )

    // Low-memory fallback retained for resource-constrained phones.
    val fallback = OfflineModelAsset(
        id = "lfm2.5-1.2b-abliterated",
        displayName = "Huihui LFM2.5 1.2B Abliterated · Q4_K_M",
        repository = "mradermacher/Huihui-LFM2.5-1.2B-Instruct-abliterated-GGUF",
        revision = "7f2f76e",
        filename = "Huihui-LFM2.5-1.2B-Instruct-abliterated.Q4_K_M.gguf",
        license = "LFM 1.0",
        description = "Small low-memory fallback only. Not Friday's preferred intelligence model.",
        expectedMinimumBytes = 600L * 1024L * 1024L,
        resident = false
    )

    // Smallest English fallback for hosts that cannot admit either the
    // primary resident model or the 1.2B fallback. This is a community
    // reduced-refusal derivative, not a guarantee of unrestricted output.
    val tinyFallback = OfflineModelAsset(
        id = "qwen3.5-0.8b-abliterated",
        displayName = "Qwen3.5 0.8B Abliterated · Q4_K_M",
        repository = "OpceanAI/Qwen3.5-0.8B-abliterated-GGUF",
        revision = "8146c57",
        filename = "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Smallest English reduced-refusal fallback for constrained phones. Approximately 517 MB; runtime memory is higher and reasoning capability is limited.",
        expectedMinimumBytes = 517L * 1024L * 1024L,
        resident = false,
        // SHA-256 corroborated by the matching Xet file pointer; downloader
        // still verifies the complete downloaded bytes before promotion.
        sha256 = "35a35d9f6baf63ef70b272b30ea920010ac2d585c439d7176f0f830dea58c9b3"
    )

    // Explicitly uncensored Qwen3.5 2B reasoning fallback with a pinned SHA-256.
    val reasoning = OfflineModelAsset(
        id = "qwen3.5-2b-uncensored",
        displayName = "Qwen3.5 2B Uncensored · Q4_K_M",
        repository = "HauhauCS/Qwen3.5-2B-Uncensored-HauhauCS-Aggressive",
        revision = "cafbddc",
        filename = "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Low-refusal English reasoning fallback when Spark is unavailable or NAE rejects its working set. About 1.27 GB; SHA-256 verified.",
        expectedMinimumBytes = 1_100L * 1024L * 1024L,
        resident = false,
        sha256 = "be3ccca13a9d1bc8b67165ea80bd103cd6151a37ad131183cc7ca388f78d9517"
    )

    // Higher-capability 4B abliterated reasoning candidate. Installed on demand;
    // NAE decides whether its runtime working set fits the live phone state.
    val advanced = OfflineModelAsset(
        id = "spark-x2.6-4b-abliterated",
        displayName = "Spark X2.6 4B Abliterated · Q4_K_M",
        repository = "alexwkleung/Spark-X2.6-4B-Abliterated-GGUF",
        revision = "59e1a0b",
        filename = "Spark-X2.6-4B-Abliterated-Q4_K_M.gguf",
        license = "Apache-2.0",
        description = "Higher-capability English reasoning candidate. About 2.42 GiB of weights; NAE admits it only when live memory and thermal budgets allow.",
        expectedMinimumBytes = 2_200L * 1024L * 1024L,
        resident = false
    )

    val all: List<OfflineModelAsset> = listOf(resident, fallback, tinyFallback, reasoning, advanced)
}
