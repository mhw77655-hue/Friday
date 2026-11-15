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


    /** Piper VITS Amy Low — compact, speed-first English US voice for phone CPU inference. */
    val piperEnglishAmyLow = VoiceModelEntry(
        id = "piper-en-us-amy-low",
        providerId = "piper",
        displayName = "Piper English US (Amy · Low)",
        languages = setOf("en"),
        sizeMb = 64,
        expectedRamMb = 128,
        expectedCpuPercent = 18.0,
        downloadUrl = "$BASE_URL/vits-piper-en_US-amy-low.tar.bz2",
        archiveFileName = "vits-piper-en_US-amy-low.tar.bz2",
        modelFile = "en_US-amy-low.onnx",
        tokensFile = "tokens.txt",
        voicesFile = "",
        speakerCount = 1,
        quality = com.jarvis.app.voice.provider.VoiceQuality.MEDIUM,
        startupCostMs = 2_500,
        latencyMs = 180
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

    /**
     * Nabra-82M Arabic int8 Kokoro pack. Offline Arabic speech, but MSA rather
     * than Egyptian dialect; use as a truthful local Arabic baseline.
     * Archive SHA-256: e7c644fabb1529190de9a2f7d6044f88c26bf6d9b717ad41ef5d2b748e01459b.
     */
    val nabraArabicInt8 = VoiceModelEntry(
        id = "nabra-82m-arabic-int8",
        providerId = "sherpa-arabic",
        displayName = "Nabra Arabic (int8 · MSA)",
        languages = linkedSetOf("ar-EG", "ar"),
        sizeMb = 85,
        expectedRamMb = 190,
        expectedCpuPercent = 28.0,
        downloadUrl = "$BASE_URL/nabra-82m-arabic-int8.tar.bz2",
        archiveFileName = "nabra-82m-arabic-int8.tar.bz2",
        modelFile = "model.int8.onnx",
        tokensFile = "tokens.txt",
        voicesFile = "voices.bin",
        speakerCount = 1,
        quality = com.jarvis.app.voice.provider.VoiceQuality.HIGH,
        startupCostMs = 4_000,
        latencyMs = 500,
        archiveSha256 = "e7c644fabb1529190de9a2f7d6044f88c26bf6d9b717ad41ef5d2b748e01459b"
    )

    val ALL: List<VoiceModelEntry> = listOf(piperEnglishAmyLow, kokoroEnInt8, kokoroEnFp32, kokoroMultiLangInt8, nabraArabicInt8)

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
    val latencyMs: Long,
    /** Optional SHA-256 of the downloaded archive, verified before extraction. */
    val archiveSha256: String? = null
)
