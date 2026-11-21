package ai.friday.assistant.companioncore.engine

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Remote HTTP runtime binding (optional research adapter) — polls an HF-Space `/api/status`-class
 * endpoint on the I/O lane and caches the last-known-good [FridayRuntimeSnapshot], so
 * [read] never blocks the caller (spec §2.30: remote-binding network calls
 * must never block the render thread; last-known-good used on any stall).
 *
 * ## Phase 1 scope (plan §4.2 GAP rows)
 * The remote endpoint's exact field names remain provider-specific. This
 * binding therefore accepts an injectable [fetchJson] (returns the raw JSON
 * status body) and a [parseJson] (maps it to [RawCoreState]). Defaults:
 * - [fetchJson] performs an HTTP GET against [endpointUrl] via the injected
 *   [httpGet] (testable without a socket).
 * - [parseJson] reads an intentionally documented `/api/status`-shaped JSON
 *   (`valence`, `arousal`, `confidence`, `trust`, `current_action`, …). If
 *   the live endpoint differs, only [parseJson] changes — nothing else in the
 *   Companion Core does.
 *
 * ## Parity (audit M-8)
 * Both bindings implement the same [FridayRuntimeBinding] interface and both
 * produce [RawCoreState]; the parity test feeds equivalent state to a mock
 * local snapshot and a mock remote payload and asserts identical
 * [ai.friday.assistant.companioncore.contract.CompanionSignal] output.
 */
class RemoteFridayRuntimeBinding(
    private val endpointUrl: String = DEFAULT_ENDPOINT_URL,
    private val httpGet: (String) -> String? = { url ->
        try {
            java.net.URL(url).readText()
        } catch (e: Exception) {
            null
        }
    },
    private val parseJson: (String) -> FridayRuntimeSnapshot = ::parseStatusJson,
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : FridayRuntimeBinding {

    override val label: String = "remote-friday-runtime"
    override val schemaVersion: Int = SCHEMA_VERSION

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "companioncore-remote-binding").apply { isDaemon = true }
    }

    @Volatile private var lastKnownGood: FridayRuntimeSnapshot = RawCoreState.empty(nowMs())
    @Volatile private var healthy: Boolean = false
    private val lastRefreshMs = AtomicLong(0L)

    init {
        executor.scheduleWithFixedDelay({ refresh() }, 0L, refreshIntervalMs, TimeUnit.MILLISECONDS)
    }

    /** One poll cycle, invoked on the I/O lane. */
    private fun refresh() {
        val json = httpGet(endpointUrl)
        val parsed = json?.let { runCatching { parseJson(it) }.getOrNull() }
        if (parsed != null) {
            lastKnownGood = parsed
            healthy = true
            lastRefreshMs.set(nowMs())
        } else {
            healthy = false
        }
    }

    /** Never blocks: returns the cached last-known-good (empty until first poll). */
    override fun read(): FridayRuntimeSnapshot = lastKnownGood

    override fun isHealthy(): Boolean = healthy

    /** Test hook: synchronously perform one poll. */
    fun refreshNow() = refresh()

    companion object {
        const val SCHEMA_VERSION: Int = 1
        const val DEFAULT_REFRESH_INTERVAL_MS: Long = 2_000L
        const val DEFAULT_ENDPOINT_URL: String = "http://127.0.0.1:8080/api/status"

        /**
         * Default parser for a documented `/api/status`-shaped payload. Uses
         * only `org.json` (provided on the test classpath). All fields are
         * optional; unknown fields are ignored; absent emotion fields map to
         * null (→ neutral), never a fabricated default.
         */
        fun parseStatusJson(body: String): FridayRuntimeSnapshot {
            val obj = org.json.JSONObject(body)
            fun optDouble(key: String): Double? =
                if (obj.has(key) && !obj.isNull(key)) obj.getDouble(key) else null
            fun optString(key: String): String? =
                if (obj.has(key) && !obj.isNull(key)) obj.getString(key) else null
            fun optLong(key: String): Long? =
                if (obj.has(key) && !obj.isNull(key)) obj.getLong(key) else null

            val rawAction = optString("current_action")?.let { name ->
                runCatching {
                    ai.friday.assistant.companioncore.contract.CurrentAction.valueOf(name)
                }.getOrNull()
            }

            return FridayRuntimeSnapshot(
                ts = optLong("ts") ?: System.currentTimeMillis(),
                moodValence = optDouble("valence"),
                moodArousal = optDouble("arousal"),
                lastUserConfidence = optDouble("last_user_confidence"),
                trust = optDouble("trust"),
                bondDepth = optDouble("bond_depth"),
                totalInteractions = optLong("total_interactions") ?: 0L,
                secondsSinceLastContact = optLong("seconds_since_last_contact"),
                lastUserSignals = emptyMap(),
                presenceModeName = optString("presence_mode"),
                currentActionRaw = rawAction,
                bridgeStatus = optString("bridge_status"),
                lastUtteranceText = optString("last_utterance_text"),
                fabricationEvidence = obj.optBoolean("fabrication_flag", false)
            )
        }
    }
}

/** Transitional adapter name; this class parses Friday runtime evidence only. */
@Deprecated("Use RemoteFridayRuntimeBinding.")
typealias RemoteHumanCoreBinding = RemoteFridayRuntimeBinding
