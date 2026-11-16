package com.jarvis.app.life

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Friday's model-independent life substrate.
 *
 * This component owns durable identity, continuity, event intake, simple
 * associative reinforcement and goal persistence. It has no model/runtime
 * dependency and is initialized before the optional language-model stack.
 *
 * It deliberately does not claim human consciousness or semantic learning:
 * associations are explicit, inspectable reinforcement records. Rich
 * interpretation may be delegated to optional intelligence providers later,
 * but identity and continuity never are.
 */
class IndependentLifeCore private constructor(context: Context) {
    data class Snapshot(
        val identity: String = "Friday",
        val bornAtMs: Long,
        val lastActiveAtMs: Long,
        val foreground: Boolean,
        val eventCount: Long,
        val experienceCount: Long,
        val associationCount: Int,
        val activeGoals: Int,
        val reflectionCount: Long
    )

    data class Goal(
        val id: String,
        val description: String,
        val createdAtMs: Long,
        val status: String
    )

    private val app = context.applicationContext
    private val root = File(app.filesDir, "friday-life").apply { mkdirs() }
    private val stateFile = File(root, "continuity.json")
    private val eventFile = File(root, "events.jsonl")
    private val goalFile = File(root, "goals.json")
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reflectionJob: Job? = null
    private val associations = linkedMapOf<String, Int>()
    private val goals = linkedMapOf<String, Goal>()
    private var bornAt = System.currentTimeMillis()
    private var lastActiveAt = bornAt
    private var foreground = false
    private var eventCount = 0L
    private var experienceCount = 0L
    private var reflectionCount = 0L
    private val mutableSnapshot = MutableStateFlow(snapshot())
    val state: StateFlow<Snapshot> = mutableSnapshot.asStateFlow()

    init {
        restore()
        recordBoot()
        startReflection()
    }

    private fun snapshot() = Snapshot(
        bornAtMs = bornAt,
        lastActiveAtMs = lastActiveAt,
        foreground = foreground,
        eventCount = eventCount,
        experienceCount = experienceCount,
        associationCount = associations.size,
        activeGoals = goals.values.count { it.status == "active" },
        reflectionCount = reflectionCount
    )

    private fun restore() {
        runCatching {
            if (stateFile.exists()) {
                val json = JSONObject(stateFile.readText())
                bornAt = json.optLong("bornAt", bornAt)
                lastActiveAt = json.optLong("lastActiveAt", bornAt)
                eventCount = json.optLong("eventCount", 0)
                experienceCount = json.optLong("experienceCount", 0)
                reflectionCount = json.optLong("reflectionCount", 0)
                val a = json.optJSONObject("associations") ?: JSONObject()
                a.keys().forEach { key -> associations[key] = a.optInt(key, 0) }
            }
            if (goalFile.exists()) {
                val array = JSONArray(goalFile.readText())
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val goal = Goal(item.getString("id"), item.getString("description"),
                        item.getLong("createdAtMs"), item.getString("status"))
                    goals[goal.id] = goal
                }
            }
        }
        mutableSnapshot.value = snapshot()
    }

    private fun recordBoot() {
        appendEvent("system.boot", "Friday life substrate initialized")
        persist()
    }

    /** Record a grounded event from the app/device. No model call is made. */
    suspend fun observe(kind: String, detail: String = "") = mutex.withLock {
        val safeKind = kind.take(80)
        val safeDetail = detail.take(500)
        val now = System.currentTimeMillis()
        lastActiveAt = now
        eventCount++
        experienceCount++
        val key = safeKind.lowercase()
        associations[key] = (associations[key] ?: 0) + 1
        appendEvent(safeKind, safeDetail)
        persist()
        mutableSnapshot.value = snapshot()
    }

    /** Store an explicit goal; goals survive process death and app upgrades. */
    suspend fun addGoal(description: String): String = mutex.withLock {
        val id = UUID.randomUUID().toString()
        goals[id] = Goal(id, description.take(500), System.currentTimeMillis(), "active")
        persistGoals()
        mutableSnapshot.value = snapshot()
        id
    }

    suspend fun completeGoal(id: String) = mutex.withLock {
        val old = goals[id] ?: return@withLock
        goals[id] = old.copy(status = "completed")
        persistGoals()
        mutableSnapshot.value = snapshot()
    }

    suspend fun setForeground(value: Boolean) = mutex.withLock {
        foreground = value
        lastActiveAt = System.currentTimeMillis()
        appendEvent(if (value) "app.foreground" else "app.background", "")
        eventCount++
        persist()
        mutableSnapshot.value = snapshot()
    }

    /** Low-frequency consolidation is model-free and never blocks the UI. */
    private fun startReflection() {
        reflectionJob = scope.launch {
            while (isActive) {
                delay(60_000)
                mutex.withLock {
                    reflectionCount++
                    persist()
                    mutableSnapshot.value = snapshot()
                }
            }
        }
    }

    private fun appendEvent(kind: String, detail: String) {
        runCatching {
            val row = JSONObject()
                .put("atMs", System.currentTimeMillis())
                .put("kind", kind)
                .put("detail", detail)
            eventFile.appendText(row.toString() + "\n")
        }
    }

    private fun persistGoals() {
        val array = JSONArray()
        goals.values.forEach { goal ->
            array.put(JSONObject().put("id", goal.id).put("description", goal.description)
                .put("createdAtMs", goal.createdAtMs).put("status", goal.status))
        }
        atomicWrite(goalFile, array.toString())
    }

    private fun persist() {
        val a = JSONObject()
        associations.forEach { (key, value) -> a.put(key, value) }
        val json = JSONObject()
            .put("identity", "Friday")
            .put("bornAt", bornAt)
            .put("lastActiveAt", lastActiveAt)
            .put("eventCount", eventCount)
            .put("experienceCount", experienceCount)
            .put("reflectionCount", reflectionCount)
            .put("associations", a)
        atomicWrite(stateFile, json.toString())
        persistGoals()
    }

    private fun atomicWrite(target: File, text: String) {
        runCatching {
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.writeText(text)
                temp.delete()
            }
        }
    }

    companion object {
        @Volatile private var instance: IndependentLifeCore? = null

        @Synchronized
        fun initialize(context: Context): IndependentLifeCore {
            return instance ?: IndependentLifeCore(context).also { instance = it }
        }

        fun get(): IndependentLifeCore? = instance
    }
}
