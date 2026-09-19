package com.jarvis.app.latency

import android.content.Context
import com.jarvis.app.TtsBridge
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Latency-first ack speech (fast path).
 *
 * The ack is a short, mood-congruent phrase picked from a **local** pool — no
 * model inference, no guard pass — and played from a **pre-synthesized WAV**
 * when one is cached, so the first spoken reply is near-instant and
 * deterministic. The deep reply later goes through [SpeechEngine.fullSpeak]
 * (cloud-TTS-then-on-device) unchanged.
 *
 * The ack deliberately bypasses the Consistency Guard: it is a procedural
 * affordance ("On it."), never a factual claim, so there is nothing to guard.
 *
 * Preload: [prepare] pre-synthesizes the phrase pool to cacheDir WAVs once the
 * TTS engine reports ready. The cache is invalidated when the selected TTS
 * voice changes, so stale audio is never played for the "new" voice.
 */
object AckSpeech {
    private const val CACHE_DIR_NAME = "jarvis_tts_cache"

    @Volatile private var appContext: Context? = null
    private val rotation = AtomicInteger(0)
    private val prepareLock = Any()
    @Volatile private var preparedForVoiceKey: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** The full pool plus the common voice-path override — everything to preload. */
    private val preloadPhrases: List<String> by lazy {
        (AckPhrases.pool().values.flatten() + "Let me check the screen.").distinct()
    }

    /**
     * Pick a short ack for the current exchange. [override] wins (used by the
     * voice path for action-specific lines like "Let me check the screen.");
     * otherwise the bucket is chosen from the Human Core mood read (read-only
     * [HumanCore.snapshot]) and phrases rotate so we don't repeat forever.
     */
    fun pick(override: String? = null, valence: Double? = snapshotValence()): String {
        if (!override.isNullOrBlank()) return override
        return AckPhrases.pick(AckPhrases.bucketFor(valence), rotation.getAndIncrement())
    }

    /**
     * Fast playback: cached pre-synthesized WAV when available (near-instant),
     * live on-device TTS otherwise. Never blocks; both paths land on the
     * engine thread.
     */
    fun fastSpeak(phrase: String) {
        if (phrase.isBlank()) return
        val cached = cachedFileFor(phrase)
        if (cached != null && TtsBridge.playCached(cached)) return
        TtsBridge.speak(phrase)
    }

    /**
     * Pre-synthesize the ack pool once the TTS engine reports ready. Idempotent
     * per voice; if the engine isn't ready yet, retries until it is (bounded),
     * then re-synthesizes on any voice change.
     */
    fun prepare() {
        val ctx = appContext ?: return
        val voiceKey = voiceKeyOf(TtsBridge.status.value)
        if (voiceKey == null) {
            retryUntilReady(ctx)
            return
        }
        synchronized(prepareLock) {
            if (preparedForVoiceKey == voiceKey) return
            // Voice changed (or first prepare): drop stale ack audio.
            File(ctx.cacheDir, CACHE_DIR_NAME).deleteRecursively()
            preparedForVoiceKey = voiceKey
        }
        preloadPhrases.forEach { phrase -> TtsBridge.synthesizeToCache(phrase) { } }
    }

    private fun retryUntilReady(ctx: Context) {
        scope.launch {
            var attempts = 0
            while (attempts < PREPARE_MAX_RETRIES && !TtsBridge.status.value.startsWith("ready")) {
                delay(PREPARE_RETRY_MS)
                attempts++
            }
            prepare()
        }
    }

    private fun snapshotValence(): Double? = HumanCore.snapshot()?.moodValence

    /** "ready (...)" → a stable key that changes when the selected voice changes. */
    private fun voiceKeyOf(status: String): String? =
        status.takeIf { it.startsWith("ready") }?.let { Integer.toHexString(it.hashCode()) }

    private fun cachedFileFor(phrase: String): File? {
        val ctx = appContext ?: return null
        return File(ctx.cacheDir, "$CACHE_DIR_NAME/${fileFor(phrase)}").takeIf { it.exists() }
    }

    /** Mirrors [com.jarvis.app.JarvisTts] cache naming so preload and lookup agree. */
    private fun fileFor(phrase: String): String = "${phrase.length}_${phrase.hashCode().toUInt()}.wav"

    private const val PREPARE_RETRY_MS = 500L
    private const val PREPARE_MAX_RETRIES = 20
}

/**
 * Pure ack-phrase pool + selection (latency-first fast path). Kept as its own
 * unit so selection is deterministic and unit-testable without any Android
 * dependency.
 */
object AckPhrases {
    enum class Bucket { POSITIVE, NEUTRAL, LOW }

    private val pools = mapOf(
        Bucket.POSITIVE to listOf("On it.", "Coming right up.", "Right away."),
        Bucket.NEUTRAL to listOf("One moment.", "Mm-hm.", "Let me think."),
        Bucket.LOW to listOf("Working on it.", "Okay.", "Just a second.")
    )

    fun pool(): Map<Bucket, List<String>> = pools

    /** Valence in [-1,1]; null (no HC read) maps to neutral. */
    fun bucketFor(valence: Double?): Bucket = when {
        valence == null -> Bucket.NEUTRAL
        valence >= 0.2 -> Bucket.POSITIVE
        valence <= -0.2 -> Bucket.LOW
        else -> Bucket.NEUTRAL
    }

    /** Deterministic rotation: the same rotation always yields the same phrase. */
    fun pick(bucket: Bucket, rotation: Int): String {
        val pool = pools.getValue(bucket)
        return pool[Math.floorMod(rotation, pool.size)]
    }
}
