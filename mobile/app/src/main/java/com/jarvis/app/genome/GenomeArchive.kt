package com.jarvis.app.genome

import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent genome archive. Stores every genome that was ever created,
 * preserving the full lineage. Uses the existing [FileStorage] and
 * [StoreKind.DIALOGUE] (JSONL append-only) — no second persistence system.
 *
 * The archive is the evolutionary memory. Queryable, append-only, never
 * prunes successful or informative failed candidates.
 */
class GenomeArchive(
    private val fileStorage: FileStorage,
    private val scope: CoroutineScope
) {

    private val store = ConcurrentHashMap<String, Genome>()

    /** All known genomes, keyed by id. */
    val allGenomes: Map<String, Genome> get() = store.toMap()

    private val _latestId = MutableStateFlow<String?>(null)
    val latestId: StateFlow<String?> = _latestId.asStateFlow()

    fun put(genome: Genome) {
        store[genome.id] = genome
        _latestId.value = genome.id
        persistAsync()
    }

    fun get(id: String): Genome? = store[id]

    fun children(parentId: String): List<Genome> =
        store.values.filter { parentId in it.parentLineage }

    fun lineage(genomeId: String): List<Genome> {
        val visited = mutableSetOf<String>()
        val result = mutableListOf<Genome>()
        var current = store[genomeId]
        while (current != null && current.id !in visited) {
            visited += current.id
            result += current
            current = store[current.parentLineage.lastOrNull()] // walk to the direct parent
        }
        return result
    }

    fun allAccepted(): List<Genome> = store.values.filter { it.isAccepted() }
    fun allRejected(): List<Genome> = store.values.filter { it.isRejected() }
    fun allTesting(): List<Genome> = store.values.filter { it.healthState == GenomeHealth.TESTING }

    fun count(): Int = store.size

    private fun persistAsync() {
        scope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                for (g in store.values) arr.put(toJson(g))
                fileStorage.append(StoreKind.DIALOGUE, arr.toString())
            } catch (t: Throwable) {
                android.util.Log.w("GenomeArchive", "persist failed", t)
            }
        }
    }

    fun loadFromDisk(): Int {
        return try {
            val content = fileStorage.read(StoreKind.DIALOGUE) ?: return 0
            val lines = content.lines().filter { it.isNotBlank() }
            var loaded = 0
            for (line in lines) {
                try {
                    val arr = JSONArray(line)
                    for (i in 0 until arr.length()) {
                        val g = fromJson(arr.getJSONObject(i))
                        store[g.id] = g
                        loaded++
                    }
                } catch (_: Exception) {
                    // skip corrupt line
                }
            }
            _latestId.value = store.values.maxByOrNull { it.version }?.id
            loaded
        } catch (t: Throwable) {
            android.util.Log.w("GenomeArchive", "load failed", t)
            0
        }
    }

    // ── JSON helpers ──

    private fun toJson(g: Genome): JSONObject = JSONObject().apply {
        put("id", g.id)
        put("version", g.version)
        put("parentLineage", JSONArray(g.parentLineage))
        put("capabilities", JSONArray(g.capabilities.toList()))
        put("dependencies", JSONArray(g.dependencies.toList()))
        put("healthState", g.healthState.name)
        put("fitnessMetrics", JSONObject(g.fitnessMetrics))
        put("mutationOperators", JSONArray(g.mutationOperators.map { it.name }))
        put("testCount", g.testSuite.size)
        put("author", g.provenance.author)
        put("createdMs", g.provenance.createdMs)
    }

    private fun fromJson(j: JSONObject): Genome = Genome(
        id = j.getString("id"),
        version = j.getInt("version"),
        parentLineage = (0 until j.getJSONArray("parentLineage").length()).map { j.getJSONArray("parentLineage").getString(it) },
        capabilities = (0 until j.getJSONArray("capabilities").length()).map { j.getJSONArray("capabilities").getString(it) }.toSet(),
        dependencies = (0 until j.getJSONArray("dependencies").length()).map { j.getJSONArray("dependencies").getString(it) }.toSet(),
        healthState = try { GenomeHealth.valueOf(j.getString("healthState")) } catch (_: Exception) { GenomeHealth.UNKNOWN },
        fitnessMetrics = run {
            val m = mutableMapOf<String, Double>()
            j.optJSONObject("fitnessMetrics")?.keys()?.forEach { m[it] = j.getJSONObject("fitnessMetrics").getDouble(it) }
            m
        },
        mutationOperators = (0 until j.getJSONArray("mutationOperators").length()).map {
            MutationOperator.valueOf(j.getJSONArray("mutationOperators").getString(it))
        }.toSet(),
        testSuite = emptyList(), // tests stored separately
        provenance = Provenance(author = j.optString("author", "unknown"), createdMs = j.optLong("createdMs"))
    )
}
