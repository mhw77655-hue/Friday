package com.jarvis.app.alerts

import com.jarvis.app.JarvisEngine
import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

/**
 * Local Alerts System - persistent alert store for system and user-facing alerts.
 * Persists to filesDir/alerts/alerts.jsonl
 * Supports: system errors, approval expirations, task failures, model disconnections, etc.
 */
class LocalAlertStore(
    private val fileStorage: FileStorage,
    private val scope: CoroutineScope
) {
    private val alertsDir = fileStorage.baseDir.resolve("alerts")
    private val alertsFile = alertsDir.resolve("alerts.jsonl")
    private val idCounter = AtomicLong(System.currentTimeMillis())

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts = _alerts.asStateFlow()

    private val _unacknowledged = MutableStateFlow<List<Alert>>(emptyList())
    val unacknowledged = _unacknowledged.asStateFlow()

    init {
        alertsDir.mkdirs()
        scope.launch { loadAlerts() }
    }

    /** UI-friendly observe method returning LiveData of AlertEntry */
    fun observeAlertsForUI(): LiveData<List<AlertEntry>> {
        return alerts
            .map { it.filter { !it.dismissed }.map { AlertEntry.from(it) } }
            .asLiveData(Dispatchers.Main)
    }

    /** Emit a new alert */
    suspend fun alert(
        level: AlertLevel,
        title: String,
        message: String,
        source: String = "system",
        metadata: Map<String, String> = emptyMap()
    ): Alert {
        val alert = Alert(
            id = "alert-${idCounter.incrementAndGet()}",
            level = level,
            title = title,
            message = message,
            source = source,
            metadata = metadata,
            timestamp = System.currentTimeMillis(),
            acknowledged = false,
            dismissed = false
        )
        addAlert(alert)
        return alert
    }

    /** Convenience methods */
    suspend fun info(title: String, message: String, source: String = "system", metadata: Map<String, String> = emptyMap()) =
        alert(AlertLevel.INFO, title, message, source, metadata)

    suspend fun warning(title: String, message: String, source: String = "system", metadata: Map<String, String> = emptyMap()) =
        alert(AlertLevel.WARNING, title, message, source, metadata)

    suspend fun error(title: String, message: String, source: String = "system", metadata: Map<String, String> = emptyMap()) =
        alert(AlertLevel.ERROR, title, message, source, metadata)

    suspend fun critical(title: String, message: String, source: String = "system", metadata: Map<String, String> = emptyMap()) =
        alert(AlertLevel.CRITICAL, title, message, source, metadata)

    /** Acknowledge an alert */
    suspend fun acknowledge(alertId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val alert = _alerts.value.find { it.id == alertId }
            ?: return@withContext Result.failure(IllegalArgumentException("Alert not found"))
        val updated = alert.copy(acknowledged = true)
        updateAlert(updated)
        Result.success(Unit)
    }

    /** Dismiss an alert (remove from active view) */
    suspend fun dismiss(alertId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val alert = _alerts.value.find { it.id == alertId }
            ?: return@withContext Result.failure(IllegalArgumentException("Alert not found"))
        val updated = alert.copy(dismissed = true)
        updateAlert(updated)
        Result.success(Unit)
    }

    /** Clear all dismissed alerts */
    suspend fun clearDismissed(): Result<Int> = withContext(Dispatchers.IO) {
        val dismissed = _alerts.value.filter { it.dismissed }
        val remaining = _alerts.value.filterNot { it.dismissed }
        _alerts.value = remaining
        rewriteAllAlerts()
        refreshDerivedFlows()
        Result.success(dismissed.size)
    }

    private suspend fun addAlert(alert: Alert) = withContext(Dispatchers.IO) {
        val current = _alerts.value
        _alerts.value = current + alert
        persistAlert(alert)
        refreshDerivedFlows()
    }

    private suspend fun updateAlert(alert: Alert) = withContext(Dispatchers.IO) {
        val current = _alerts.value
        val index = current.indexOfFirst { it.id == alert.id }
        if (index >= 0) {
            val updated = current.toMutableList().apply { set(index, alert) }
            _alerts.value = updated
            rewriteAllAlerts()
            refreshDerivedFlows()
        }
    }

    private fun refreshDerivedFlows() {
        _unacknowledged.value = _alerts.value.filter { !it.acknowledged && !it.dismissed }
    }

    private suspend fun loadAlerts() = withContext(Dispatchers.IO) {
        if (!alertsFile.exists()) return@withContext
        val alerts = mutableListOf<Alert>()
        alertsFile.readLines().forEach { line ->
            if (line.isNotBlank()) {
                try {
                    alerts.add(Alert.fromJson(JSONObject(line)))
                } catch (e: Exception) {
                    // Skip corrupted
                }
            }
        }
        // Init-load races with the first writes: if anything landed in memory
        // before this disk read finished, in-memory state is authoritative.
        if (_alerts.value.isEmpty()) {
            _alerts.value = alerts
            refreshDerivedFlows()
        }
    }

    private fun persistAlert(alert: Alert) {
        fileStorage.append(alertsFile, alert.toJson().toString())
    }

    private fun rewriteAllAlerts() {
        val content = _alerts.value.joinToString("\n") { it.toJson().toString() } + "\n"
        fileStorage.write(alertsFile, content)
    }

    companion object {
        fun create(): LocalAlertStore = LocalAlertStore(
            fileStorage = FileStorage(JarvisEngine.appContext().filesDir),
            scope = CoroutineScope(Dispatchers.Main)
        )
    }
}

/** Alert entity */
data class Alert(
    val id: String,
    val level: AlertLevel,
    val title: String,
    val message: String,
    val source: String,
    val metadata: Map<String, String>,
    val timestamp: Long,
    val acknowledged: Boolean,
    val dismissed: Boolean
) {
    /** Manual JSON round-trip (kotlinx-serialization is not in the build). */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("level", level.name)
        put("title", title)
        put("message", message)
        put("source", source)
        put("metadata", JSONObject(metadata))
        put("timestamp", timestamp)
        put("acknowledged", acknowledged)
        put("dismissed", dismissed)
    }

    companion object {
        fun fromJson(o: JSONObject): Alert = Alert(
            id = o.getString("id"),
            level = runCatching { AlertLevel.valueOf(o.getString("level")) }
                .getOrDefault(AlertLevel.INFO),
            title = o.optString("title"),
            message = o.optString("message"),
            source = o.optString("source"),
            metadata = o.optJSONObject("metadata")?.let { jo ->
                buildMap {
                    jo.keys().forEach { k -> this[k] = jo.optString(k) }
                }
            } ?: emptyMap(),
            timestamp = o.optLong("timestamp"),
            acknowledged = o.optBoolean("acknowledged"),
            dismissed = o.optBoolean("dismissed")
        )
    }
}

enum class AlertLevel {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/** UI-friendly alert entry */
data class AlertEntry(
    val id: String,
    val title: String,
    val message: String,
    val level: String,
    val source: String,
    val timestamp: String
) {
    companion object {
        fun from(alert: Alert): AlertEntry = AlertEntry(
            id = alert.id,
            title = alert.title,
            message = alert.message,
            level = alert.level.name,
            source = alert.source,
            timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(alert.timestamp))
        )
    }
}
