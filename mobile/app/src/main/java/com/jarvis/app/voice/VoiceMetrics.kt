package com.jarvis.app.voice

import java.util.concurrent.CopyOnWriteArrayList

/**
 * R2 — lightweight benchmark hooks for the voice organism.
 *
 * Deliberately record-based (not a framework): each event appends one
 * [Metric], and a consumer can dump [snapshot] to the milestone report or a
 * future dashboard. The same hooks are what a future Termux/remote environment
 * will report through — the contract is the record, not the process.
 */
object VoiceMetrics {

    enum class Kind { SYNTHESIS, MODEL_LOAD, CANCEL, RECOVERY, ERROR }

    data class Metric(
        val kind: Kind,
        val provider: String? = null,
        val model: String? = null,
        val language: String? = null,
        val durationMs: Long,
        val note: String? = null,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val records = CopyOnWriteArrayList<Metric>()

    /** [wallClockMs] is end-to-end synthesis time; [audioMs] the produced audio length. */
    fun recordSynthesis(provider: String, language: String, wallClockMs: Long, audioMs: Long, sampleRate: Int = 0) {
        records.add(
            Metric(
                kind = Kind.SYNTHESIS,
                provider = provider,
                language = language,
                durationMs = wallClockMs,
                note = "audioMs=$audioMs,sr=$sampleRate"
            )
        )
    }

    fun recordModelLoad(model: String, durationMs: Long, ok: Boolean) {
        records.add(Metric(Kind.MODEL_LOAD, model = model, durationMs = durationMs, note = if (ok) "loaded" else "failed"))
    }

    fun recordCancel(durationMs: Long) {
        records.add(Metric(Kind.CANCEL, durationMs = durationMs))
    }

    fun recordRecovery(durationMs: Long, note: String) {
        records.add(Metric(Kind.RECOVERY, durationMs = durationMs, note = note))
    }

    fun recordError(provider: String?, error: String) {
        records.add(Metric(Kind.ERROR, provider = provider, durationMs = 0, note = error))
    }

    fun snapshot(): List<Metric> = records.toList()

    /** Rolling RTF (synthesis wall-clock / audio duration) over the last [n] synthesis metrics. */
    fun rollingRtf(n: Int = 10): Double? {
        val synths = records.filter { it.kind == Kind.SYNTHESIS }.takeLast(n)
        if (synths.isEmpty()) return null
        val values = synths.mapNotNull { m ->
            val audioMs = m.note?.substringAfter("audioMs=")?.substringBefore(",")?.toLongOrNull()
            if (audioMs != null && audioMs > 0) m.durationMs.toDouble() / audioMs else null
        }
        return values.takeIf { it.isNotEmpty() }?.average()
    }

    fun clear() = records.clear()
}
