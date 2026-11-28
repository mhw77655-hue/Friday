package ai.friday.assistant.diagnostics

import ai.friday.assistant.humancore.store.FileStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Persistent, bounded evidence for the production conversation lifecycle.
 *
 * The API intentionally accepts only numeric correlation IDs and closed enums.
 * It has no parameter through which message text, prompts, transcripts, audio,
 * memory contents, exception messages, URLs or credentials could be recorded.
 */
class TurnDiagnosticJournal(
    directory: File,
    private val maxEvents: Int = 300,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val file = File(directory, FILE_NAME)
    private val storage = FileStorage(directory)
    private val lock = Any()

    init {
        require(maxEvents in 1..MAX_EVENTS)
    }

    data class Event(
        val atEpochMs: Long,
        val exchangeId: Long,
        val stage: Stage,
        val route: Route,
        val outcome: Outcome,
        val elapsedMs: Long?
    )

    enum class Stage {
        INPUT_ACCEPTED,
        ACK_STARTED,
        ACK_FLUSH_REQUESTED,
        ROUTE_SELECTED,
        COGNITION_STARTED,
        MODEL_REQUEST_STARTED,
        MODEL_REPLY_RECEIVED,
        LOCAL_FALLBACK_SELECTED,
        REPLY_DELIVERY_STARTED,
        SPEECH_HANDOFF,
        TURN_SETTLED,
        TURN_TIMED_OUT
    }

    enum class Route { UNKNOWN, MODEL, MODEL_FREE }
    enum class Outcome { OBSERVED, STARTED, SUCCEEDED, FAILED, TIMED_OUT }

    fun record(
        exchangeId: Long,
        stage: Stage,
        route: Route = Route.UNKNOWN,
        outcome: Outcome = Outcome.OBSERVED,
        elapsedMs: Long? = null
    ) = synchronized(lock) {
        require(exchangeId >= 0L)
        val root = readRoot()
        val events = root.optJSONArray("events") ?: JSONArray()
        events.put(
            JSONObject()
                .put("at_epoch_ms", clock())
                .put("exchange_id", exchangeId)
                .put("stage", stage.name)
                .put("route", route.name)
                .put("outcome", outcome.name)
                .put("elapsed_ms", elapsedMs?.coerceAtLeast(0L) ?: JSONObject.NULL)
        )
        val bounded = JSONArray()
        val first = (events.length() - maxEvents).coerceAtLeast(0)
        for (index in first until events.length()) bounded.put(events.getJSONObject(index))
        storage.write(
            file,
            JSONObject()
                .put("schema", SCHEMA)
                .put("events", bounded)
                .toString()
        )
    }

    fun snapshot(): List<Event> = synchronized(lock) {
        val events = readRoot().optJSONArray("events") ?: JSONArray()
        buildList {
            for (index in 0 until events.length()) {
                val item = events.optJSONObject(index) ?: continue
                val stage = runCatching { Stage.valueOf(item.getString("stage")) }.getOrNull() ?: continue
                val route = runCatching { Route.valueOf(item.optString("route")) }.getOrDefault(Route.UNKNOWN)
                val outcome = runCatching { Outcome.valueOf(item.optString("outcome")) }.getOrDefault(Outcome.OBSERVED)
                val elapsed = if (item.isNull("elapsed_ms")) null else item.optLong("elapsed_ms")
                add(Event(
                    atEpochMs = item.optLong("at_epoch_ms"),
                    exchangeId = item.optLong("exchange_id"),
                    stage = stage,
                    route = route,
                    outcome = outcome,
                    elapsedMs = elapsed
                ))
            }
        }
    }

    /** Export contains only fixed vocabulary and timing/correlation metadata. */
    fun exportJson(
        appVersion: String,
        sourceRevision: String,
        androidApi: Int
    ): String = synchronized(lock) {
        val events = JSONArray().apply {
            snapshot().forEach { event ->
                put(JSONObject()
                    .put("at_epoch_ms", event.atEpochMs)
                    .put("exchange_id", event.exchangeId)
                    .put("stage", event.stage.name)
                    .put("route", event.route.name)
                    .put("outcome", event.outcome.name)
                    .put("elapsed_ms", event.elapsedMs ?: JSONObject.NULL))
            }
        }
        JSONObject()
            .put("schema", SCHEMA)
            .put("generated_at_utc", Instant.ofEpochMilli(clock()).toString())
            .put("app_version", appVersion.take(80))
            .put("source_revision", sourceRevision.take(80))
            .put("android_api", androidApi)
            .put("event_count", events.length())
            .put("events", events)
            .put("privacy", JSONObject()
                .put("message_bodies_included", false)
                .put("prompts_included", false)
                .put("transcripts_included", false)
                .put("audio_included", false)
                .put("personal_memory_included", false)
                .put("exception_messages_included", false)
                .put("credentials_included", false))
            .toString(2)
    }

    private fun readRoot(): JSONObject {
        if (!file.isFile) return JSONObject().put("schema", SCHEMA).put("events", JSONArray())
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .getOrElse { JSONObject().put("schema", SCHEMA).put("events", JSONArray()) }
    }

    companion object {
        const val SCHEMA = "friday-turn-lifecycle-v1"
        const val FILE_NAME = "turn-lifecycle.json"
        const val MAX_EVENTS = 2_000
    }
}

/** Main-process facade. Recording is best-effort and never blocks a turn on failure. */
object FridayTurnDiagnostics {
    @Volatile private var journal: TurnDiagnosticJournal? = null

    fun initialize(directory: File) {
        journal = TurnDiagnosticJournal(File(directory, "diagnostics"))
    }

    fun record(
        exchangeId: Long,
        stage: TurnDiagnosticJournal.Stage,
        route: TurnDiagnosticJournal.Route = TurnDiagnosticJournal.Route.UNKNOWN,
        outcome: TurnDiagnosticJournal.Outcome = TurnDiagnosticJournal.Outcome.OBSERVED,
        elapsedMs: Long? = null
    ) {
        runCatching { journal?.record(exchangeId, stage, route, outcome, elapsedMs) }
    }

    fun exportJson(appVersion: String, sourceRevision: String, androidApi: Int): String =
        journal?.exportJson(appVersion, sourceRevision, androidApi)
            ?: TurnDiagnosticJournal(File(System.getProperty("java.io.tmpdir") ?: ".", "friday-diagnostics"))
                .exportJson(appVersion, sourceRevision, androidApi)
}
