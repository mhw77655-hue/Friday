package com.jarvis.app.trace

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * TURN-TRACE (Gate 3a): a structured, per-turn trace of every real conversation
 * turn through the production CognitiveTurnResult path.
 *
 * One [TurnTraceRecord] is appended per turn (append-only JSON Lines, one JSON
 * object per line, local-only — never a network call). It carries the raw
 * input, the retrieved memory ids, any predictions made this turn, the prompt
 * section boundaries of the assembled generation payload, the model output that
 * was synchronously available, and the per-stage latency in milliseconds
 * (embed / retrieve / prompt-build / generate / post-process).
 *
 * This is pure logging: no new behavior, no model changes. A [TurnTraceStore]
 * that is absent (null seam) or disabled ([TurnTraceStore.enabled] == false)
 * writes nothing and keeps the pre-trace path byte-for-byte.
 */
object TurnTrace {

    /** Canonical per-stage latency keys (order preserved in the record). */
    const val STAGE_EMBED = "embed"
    const val STAGE_RETRIEVE = "retrieve"
    const val STAGE_PROMPT_BUILD = "prompt-build"
    const val STAGE_GENERATE = "generate"
    const val STAGE_POST_PROCESS = "post-process"

    /** The five canonical stages, in execution order. */
    val ALL_STAGES: List<String> = listOf(
        STAGE_EMBED,
        STAGE_RETRIEVE,
        STAGE_PROMPT_BUILD,
        STAGE_GENERATE,
        STAGE_POST_PROCESS
    )

    /**
     * A named slice of the assembled generation payload. [startOffset] is the
     * first character index (inclusive) of the section within the final prompt
     * string, [length] its character count; [preview] is a bounded excerpt of
     * the section text for inspection without loading the whole payload.
     *
     * Prompt section names fixed by the generation path that builds the payload:
     *  - "user-input"           the raw user text
     *  - "cross-session-memory" the [Cross-session memory] block
     *  - "identity-context"     the [Identity context] suffix
     */
    data class PromptSection(
        val name: String,
        val startOffset: Int,
        val length: Int,
        val preview: String
    )

    /** A prediction made during the turn (forward schema slot — wired by the
     *  predictor stages; the current turn path makes none, so the list is
     *  empty until a real predictor exists). */
    data class TracePrediction(
        val description: String,
        val confidence: Double
    )

    /**
     * One trace record for one conversation turn.
     *
     * Serialized as JSON via [toJson]/[fromJson] (org.json — present in the
     * Android SDK and on the unit-test classpath).
     */
    data class TurnTraceRecord(
        val id: String,
        val turnIndex: Long,
        val timestampMs: Long,
        val inputText: String,
        val decision: String,
        val retrievedMemoryIds: List<String>,
        val predictions: List<TracePrediction>,
        val promptSections: List<PromptSection>,
        val outputText: String?,
        val generationPayload: String?,
        val crossSessionMemories: List<String>,
        val stageTimingsMs: Map<String, Long>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("turnIndex", turnIndex)
            .put("timestampMs", timestampMs)
            .put("inputText", inputText)
            .put("decision", decision)
            .put("retrievedMemoryIds", JSONArray(retrievedMemoryIds))
            .put("predictions", JSONArray(predictions.map {
                JSONObject().put("description", it.description).put("confidence", it.confidence)
            }))
            .put("promptSections", JSONArray(promptSections.map {
                JSONObject()
                    .put("name", it.name)
                    .put("startOffset", it.startOffset)
                    .put("length", it.length)
                    .put("preview", it.preview)
            }))
            .put("outputText", outputText ?: JSONObject.NULL)
            .put("generationPayload", generationPayload ?: JSONObject.NULL)
            .put("crossSessionMemories", JSONArray(crossSessionMemories))
            .put("stageTimingsMs", JSONObject(stageTimingsMs))

        companion object {
            fun fromJson(json: JSONObject): TurnTraceRecord = TurnTraceRecord(
                id = json.getString("id"),
                turnIndex = json.getLong("turnIndex"),
                timestampMs = json.getLong("timestampMs"),
                inputText = json.getString("inputText"),
                decision = json.getString("decision"),
                retrievedMemoryIds = json.getJSONArray("retrievedMemoryIds")
                    .let { a -> (0 until a.length()).map { a.getString(it) } },
                predictions = json.getJSONArray("predictions")
                    .let { a ->
                        (0 until a.length()).map {
                            val p = a.getJSONObject(it)
                            TracePrediction(p.getString("description"), p.getDouble("confidence"))
                        }
                    },
                promptSections = json.getJSONArray("promptSections")
                    .let { a ->
                        (0 until a.length()).map {
                            val s = a.getJSONObject(it)
                            PromptSection(
                                name = s.getString("name"),
                                startOffset = s.getInt("startOffset"),
                                length = s.getInt("length"),
                                preview = s.getString("preview")
                            )
                        }
                    },
                outputText = if (json.isNull("outputText")) null else json.getString("outputText"),
                generationPayload = if (json.isNull("generationPayload")) null else json.getString("generationPayload"),
                crossSessionMemories = json.getJSONArray("crossSessionMemories")
                    .let { a -> (0 until a.length()).map { a.getString(it) } },
                stageTimingsMs = json.getJSONObject("stageTimingsMs")
                    .let { o ->
                        buildMap {
                            val it = o.keys()
                            while (it.hasNext()) {
                                val k = it.next() as String
                                put(k, o.getLong(k))
                            }
                        }
                    }
            )
        }
    }

    /**
     * Build the prompt section list over the exact payload string that was
     * (or would have been) dispatched to generation: the raw user text, then
     * the cross-session memory block, then the identity-context suffix. Each
     * section records its character offsets in the concatenated payload.
     */
    fun buildPromptSections(
        userText: String,
        crossSessionBlock: String,
        identitySuffix: String?
    ): List<PromptSection> {
        val sections = mutableListOf<PromptSection>()
        var cursor = 0

        fun add(name: String, text: String) {
            if (text.isEmpty()) return
            sections.add(
                PromptSection(
                    name = name,
                    startOffset = cursor,
                    length = text.length,
                    preview = text.take(120)
                )
            )
            cursor += text.length
        }

        add("user-input", userText)
        add("cross-session-memory", crossSessionBlock)
        identitySuffix?.let { add("identity-context", it) }
        return sections
    }
}

/**
 * Storage seam for trace records. A null engine seam, or a store whose
 * [enabled] flag is false, writes nothing (the negative control: the trace
 * file must not grow while disabled, which proves the wiring is real and not
 * a stub).
 */
interface TurnTraceStore {
    val enabled: Boolean
    fun setEnabled(enabled: Boolean)
    fun append(record: TurnTrace.TurnTraceRecord)
    fun count(): Long

    /** The stored raw input text of a past turn, for replay. */
    fun storedInput(id: String): String?

    /**
     * AC3 `replay_turn(id)`: re-run a past turn's stored input against the
     * current logic by handing it to [runner] (in practice the current
     * [com.jarvis.app.cognitive.CognitiveEngine.process] entry point). Returns
     * null when no stored turn exists with that id.
     */
    suspend fun <T> replay_turn(id: String, runner: suspend (String) -> T): T?

    /**
     * FORGET-PROPAGATION: redact every stored record whose serialized text
     * carries [text] (case-insensitive) via a rewrite compaction — the record
     * keeps its id, turn index, timestamp, decision, retrieved ids, predictions
     * and stage timings, but its input/output/generation text and its
     * cross-session memory block are blanked (AC6: the trace exists, the text
     * does not). Appending new turns stays append-only; this is the only
     * destructive path and it is reachable solely from an explicit user forget.
     * Returns the number of records redacted. Default no-op keeps stores that
     * never forget compiling unchanged.
     */
    fun redactContaining(text: String): Int = 0
}

/**
 * Append-only, local-file JSON Lines trace store.
 *
 * Every [append] writes one line (one JSON object) and flushes, so the file is
 * durable and grow-only; there is no rewrite of history. Disabling the store
 * ([setEnabled]false]) makes [append] a no-op — the file length is then provably
 * unchanged across a turn (AC5).
 *
 * Local-only by construction: the target is a [File] on this device; there is
 * no network/socket/HTTP anywhere in this class (AC4).
 */
class JsonlTurnTraceStore(
    private val file: File,
    initiallyEnabled: Boolean = true
) : TurnTraceStore {

    @Volatile
    private var _enabled: Boolean = initiallyEnabled

    override val enabled: Boolean get() = _enabled

    override fun setEnabled(enabled: Boolean) {
        _enabled = enabled
    }

    /** The append-only local trace file. */
    val traceFile: File get() = file

    override fun append(record: TurnTrace.TurnTraceRecord) {
        if (!_enabled) return
        file.parentFile?.mkdirs()
        val line = record.toJson().toString()
        synchronized(this) {
            if (!_enabled) return
            file.appendText("$line\n", Charsets.UTF_8)
        }
    }

    override fun count(): Long {
        if (!file.exists()) return 0L
        return file.useLines { it.count() }.toLong()
    }

    override fun storedInput(id: String): String? =
        readRecords().firstOrNull { it.id == id }?.inputText

    override suspend fun <T> replay_turn(id: String, runner: suspend (String) -> T): T? {
        val input = storedInput(id) ?: return null
        return runCatching { runner(input) }.getOrElse { null }
    }

    /** Ordered list of every stored record (replay/id lookups read the file). */
    fun readRecords(): List<TurnTrace.TurnTraceRecord> {
        if (!file.exists()) return emptyList()
        val records = mutableListOf<TurnTrace.TurnTraceRecord>()
        synchronized(this) {
            file.useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    runCatching { TurnTrace.TurnTraceRecord.fromJson(JSONObject(line)) }
                        .onSuccess { records.add(it) }
                }
            }
        }
        return records
    }

    // FORGET-PROPAGATION: the trace-file surface of a forget. Only the explicit
    // forget/مرور rewrite path rewrites history (text blanked, structure kept);
    // the AC6 records of the forget turn are written already-redacted by the
    // engine. The same double-guarded append stays the ONLY writer for new turns.
    override fun redactContaining(text: String): Int = synchronized(this) {
        val all = readRecords()
        var redacted = 0
        val needle = text.lowercase()
        val rewritten = all.map { record ->
            if (record.toJson().toString().lowercase().contains(needle)) {
                redacted++
                record.copy(
                    inputText = "",
                    outputText = null,
                    generationPayload = null,
                    promptSections = record.promptSections.map { it.copy(preview = "") },
                    crossSessionMemories = emptyList()
                )
            } else {
                record
            }
        }
        if (redacted > 0) {
            file.writeText("", Charsets.UTF_8)
            rewritten.forEach { file.appendText("${it.toJson()}\n", Charsets.UTF_8) }
        }
        redacted
    }
}