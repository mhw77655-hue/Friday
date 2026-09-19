package com.jarvis.app.voice

/**
 * R2 — the voice model manifest: the surviving-artifact recipe for every model
 * the habitat can lazily acquire. Records what to download, where from, what
 * it costs (disk/RAM), and which provider speaks through it. Models are NOT
 * eagerly loaded — [VoiceModelManager] acquires on first use and can unload.
 *
 * Sources: sherpa-onnx public GitHub release `tts-models` (no HF auth needed).
 * Layout verified from sherpa-onnx docs: archive extracts to a directory
 * containing `model.onnx`, `tokens.txt`, `voices.bin`.
 */
object VoiceModelManifest {

    const val BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    /** kokoro-en-v0_19 (int8) — the first production native model: English, 11 speakers, CPU-friendly. */
    val kokoroEnInt8 = VoiceModelEntry(
        id = "kokoro-int8-en-v0_19",
        providerId = "sherpa",
        displayName = "Kokoro English (int8)",
        languages = setOf("en"),
        sizeMb = 103,
        expectedRamMb = 220,
        expectedCpuPercent = 25.0,
        downloadUrl = "$BASE_URL/kokoro-int8-en-v0_19.tar.bz2",
        archiveFileName = "kokoro-int8-en-v0_19.tar.bz2",
        modelFile = "model.onnx",
        tokensFile = "tokens.txt",
        voicesFile = "voices.bin",
        speakerCount = 11,
        quality = com.jarvis.app.voice.provider.VoiceQuality.HIGH,
        startupCostMs = 6_000,
        latencyMs = 350
    )

    /** kokoro-en-v0_19 (fp32) — slightly higher quality, ~3x the download. */
    val kokoroEnFp32 = kokoroEnInt8.copy(
        id = "kokoro-en-v0_19",
        displayName = "Kokoro English (fp32)",
        sizeMb = 320,
        expectedRamMb = 400,
        downloadUrl = "$BASE_URL/kokoro-en-v0_19.tar.bz2",
        archiveFileName = "kokoro-en-v0_19.tar.bz2",
        startupCostMs = 9_000
    )

    /**
     * kokoro-multilingual v1.0 (int8) — 8 languages (en/ja/zh/es/fr/de/hi/it),
     * 53 speakers. The future swap when multilingual Kokoro is promoted.
     */
    val kokoroMultiLangInt8 = kokoroEnInt8.copy(
        id = "kokoro-int8-multi-lang-v1_0",
        providerId = "sherpa",
        displayName = "Kokoro Multilingual (int8)",
        languages = setOf("en", "ja", "zh", "es", "fr", "de", "hi", "it"),
        sizeMb = 132,
        expectedRamMb = 280,
        downloadUrl = "$BASE_URL/kokoro-int8-multi-lang-v1_0.tar.bz2",
        archiveFileName = "kokoro-int8-multi-lang-v1_0.tar.bz2",
        speakerCount = 53,
        startupCostMs = 7_000,
        latencyMs = 400
    )

    val ALL: List<VoiceModelEntry> = listOf(kokoroEnInt8, kokoroEnFp32, kokoroMultiLangInt8)

    fun byId(id: String): VoiceModelEntry? = ALL.firstOrNull { it.id == id }
}

/**
 * One downloadable model. [expectedRamMb]/[expectedCpuPercent] feed
 * [com.jarvis.app.resource.ResourceGovernor] admission so the habitat never
 * loads a second model the device can't afford.
 */
data class VoiceModelEntry(
    val id: String,
    val providerId: String,
    val displayName: String,
    val languages: Set<String>,
    val sizeMb: Long,
    val expectedRamMb: Long,
    val expectedCpuPercent: Double,
    val downloadUrl: String,
    val archiveFileName: String,
    val modelFile: String,
    val tokensFile: String,
    val voicesFile: String,
    val speakerCount: Int,
    val quality: com.jarvis.app.voice.provider.VoiceQuality,
    val startupCostMs: Long,
    val latencyMs: Long
)
