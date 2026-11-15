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
 * [StoreKind.GENOME_ARCHIVE] (JSONL append-only) — no second persistence system.
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
                fileStorage.append(StoreKind.GENOME_ARCHIVE, arr.toString())
            } catch (t: Throwable) {
                android.util.Log.w("GenomeArchive", "persist failed", t)
            }
        }
    }

    fun loadFromDisk(): Int {
        return try {
            val content = fileStorage.read(StoreKind.GENOME_ARCHIVE) ?: return 0
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
        put("runtimeRequirements", JSONObject().apply {
            put("minSdk", g.runtimeRequirements.minSdk)
            put("maxConcurrency", g.runtimeRequirements.maxConcurrency)
            put("requiresMainThread", g.runtimeRequirements.requiresMainThread)
            put("requiresBackground", g.runtimeRequirements.requiresBackground)
            put("isolation", g.runtimeRequirements.isolation.name)
        })
        put("modelRequirements", JSONArray().apply {
            g.modelRequirements.models.forEach { model ->
                put(JSONObject().apply {
                    put("id", model.id)
                    put("type", model.type)
                    put("quantization", model.quantization ?: JSONObject.NULL)
                    put("maxMemoryMb", model.maxMemoryMb)
                    put("required", model.required)
                })
            }
        })
        put("toolRequirements", JSONArray().apply {
            g.toolRequirements.forEach { tool ->
                put(JSONObject().put("toolId", tool.toolId).put("required", tool.required))
            }
        })
        put("environmentRequirements", JSONObject().apply {
            put("networkAccess", g.environmentRequirements.networkAccess)
            put("fileAccess", JSONArray(g.environmentRequirements.fileAccess.toList()))
            put("sensors", JSONArray(g.environmentRequirements.sensors.toList()))
            put("permissions", JSONArray(g.environmentRequirements.permissions.toList()))
            put("batteryMinPercent", g.environmentRequirements.batteryMinPercent)
            put("thermalOk", g.environmentRequirements.thermalOk)
        })
        put("communicationContract", JSONObject().apply {
            put("inputTopics", JSONArray(g.communicationContract.inputTopics.toList()))
            put("outputTopics", JSONArray(g.communicationContract.outputTopics.toList()))
            put("requestTopics", JSONArray(g.communicationContract.requestTopics.toList()))
            put("resultTopics", JSONArray(g.communicationContract.resultTopics.toList()))
            put("confidenceThreshold", g.communicationContract.confidenceThreshold.toDouble())
            put("priority", g.communicationContract.priority)
            put("maxInflight", g.communicationContract.maxInflight)
        })
        put("resourceBudget", JSONObject().apply {
            put("maxMemoryMb", g.resourceBudget.maxMemoryMb)
            put("maxCpuPercent", g.resourceBudget.maxCpuPercent)
            put("maxEnergyPerHour", g.resourceBudget.maxEnergyPerHour)
            put("priority", g.resourceBudget.priority)
            put("isLatencySensitive", g.resourceBudget.isLatencySensitive)
            put("maxLatencyMs", g.resourceBudget.maxLatencyMs)
        })
        put("latencyBudget", JSONObject().apply {
            put("maxFirstTokenMs", g.latencyBudget.maxFirstTokenMs)
            put("maxFullResponseMs", g.latencyBudget.maxFullResponseMs)
            put("maxTtsStartMs", g.latencyBudget.maxTtsStartMs)
        })
        put("safetyConstraints", JSONArray(g.safetyConstraints.map { it.name }))
        put("inputs", portsToJson(g.inputs))
        put("outputs", portsToJson(g.outputs))
        put("testSuite", JSONArray().apply {
            g.testSuite.forEach { test ->
                put(JSONObject().put("name", test.name).put("type", test.type.name).put("description", test.description))
            }
        })
        put("benchmarkSuite", JSONArray().apply {
            g.benchmarkSuite.forEach { benchmark ->
                put(JSONObject().put("name", benchmark.name).put("metric", benchmark.metric)
                    .put("baseline", benchmark.baseline).put("tolerance", benchmark.tolerance))
            }
        })
        put("fitnessMetrics", JSONObject(g.fitnessMetrics))
        put("mutationOperators", JSONArray(g.mutationOperators.map { it.name }))
        put("compatibilityRequirements", JSONObject().apply {
            put("requiresGenomeIds", JSONArray(g.compatibilityRequirements.requiresGenomeIds.toList()))
            put("incompatibleGenomeIds", JSONArray(g.compatibilityRequirements.incompatibleGenomeIds.toList()))
            put("requiredCapabilities", JSONArray(g.compatibilityRequirements.requiredCapabilities.toList()))
        })
        put("healthState", g.healthState.name)
        put("provenance", JSONObject().apply {
            put("author", g.provenance.author)
            put("createdMs", g.provenance.createdMs)
            put("lastMutation", g.provenance.lastMutation)
            put("mutationOperator", g.provenance.mutationOperator?.name ?: JSONObject.NULL)
            put("sourceContext", g.provenance.sourceContext)
        })
    }

    private fun portsToJson(ports: List<Port>) = JSONArray().apply {
        ports.forEach { port ->
            put(JSONObject().put("name", port.name).put("type", port.type)
                .put("nullable", port.nullable).put("description", port.description))
        }
    }

    private fun fromJson(j: JSONObject): Genome {
        val runtime = j.optJSONObject("runtimeRequirements") ?: JSONObject()
        val environment = j.optJSONObject("environmentRequirements") ?: JSONObject()
        val communication = j.optJSONObject("communicationContract") ?: JSONObject()
        val resources = j.optJSONObject("resourceBudget") ?: JSONObject()
        val latency = j.optJSONObject("latencyBudget") ?: JSONObject()
        val compatibility = j.optJSONObject("compatibilityRequirements") ?: JSONObject()
        val provenance = j.optJSONObject("provenance") ?: JSONObject()
        val fitness = mutableMapOf<String, Double>()
        j.optJSONObject("fitnessMetrics")?.keys()?.forEach { key ->
            fitness[key] = j.getJSONObject("fitnessMetrics").optDouble(key, 0.0)
        }
        val models = j.optJSONArray("modelRequirements") ?: JSONArray()
        val tools = j.optJSONArray("toolRequirements") ?: JSONArray()
        val tests = j.optJSONArray("testSuite") ?: JSONArray()
        val benchmarks = j.optJSONArray("benchmarkSuite") ?: JSONArray()
        return Genome(
            id = j.getString("id"),
            version = j.optInt("version", 1),
            parentLineage = stringList(j, "parentLineage").ifEmpty { listOf(Genome.ROOT_LINEAGE) },
            capabilities = stringList(j, "capabilities").toSet(),
            inputs = readPorts(j.optJSONArray("inputs")),
            outputs = readPorts(j.optJSONArray("outputs")),
            dependencies = stringList(j, "dependencies").toSet(),
            runtimeRequirements = RuntimeRequirements(
                minSdk = runtime.optInt("minSdk", 21),
                maxConcurrency = runtime.optInt("maxConcurrency", 1),
                requiresMainThread = runtime.optBoolean("requiresMainThread", false),
                requiresBackground = runtime.optBoolean("requiresBackground", false),
                isolation = enumValue(runtime.optString("isolation"), IsolationLevel.PROCESS_LOCAL)
            ),
            modelRequirements = ModelRequirements((0 until models.length()).map { index ->
                val item = models.getJSONObject(index)
                ModelSlot(
                    id = item.optString("id"),
                    type = item.optString("type"),
                    quantization = if (item.isNull("quantization")) null else item.optString("quantization"),
                    maxMemoryMb = item.optLong("maxMemoryMb", 0L),
                    required = item.optBoolean("required", true)
                )
            }),
            toolRequirements = ModelTools.read(tools),
            environmentRequirements = EnvironmentRequirements(
                networkAccess = environment.optBoolean("networkAccess", false),
                fileAccess = stringList(environment, "fileAccess"),
                sensors = stringList(environment, "sensors"),
                permissions = stringList(environment, "permissions"),
                batteryMinPercent = environment.optInt("batteryMinPercent", 0),
                thermalOk = environment.optBoolean("thermalOk", true)
            ),
            communicationContract = CommunicationContract(
                inputTopics = stringList(communication, "inputTopics"),
                outputTopics = stringList(communication, "outputTopics"),
                requestTopics = stringList(communication, "requestTopics"),
                resultTopics = stringList(communication, "resultTopics"),
                confidenceThreshold = communication.optDouble("confidenceThreshold", 0.5).toFloat(),
                priority = communication.optInt("priority", 0),
                maxInflight = communication.optInt("maxInflight", 1)
            ),
            resourceBudget = ResourceBudget(
                maxMemoryMb = resources.optLong("maxMemoryMb", 512L),
                maxCpuPercent = resources.optDouble("maxCpuPercent", 50.0),
                maxEnergyPerHour = resources.optDouble("maxEnergyPerHour", 100.0),
                priority = resources.optInt("priority", 0),
                isLatencySensitive = resources.optBoolean("isLatencySensitive", false),
                maxLatencyMs = resources.optLong("maxLatencyMs", 1000L)
            ),
            latencyBudget = LatencyBudget(
                maxFirstTokenMs = latency.optLong("maxFirstTokenMs", 5000L),
                maxFullResponseMs = latency.optLong("maxFullResponseMs", 30000L),
                maxTtsStartMs = latency.optLong("maxTtsStartMs", 2000L)
            ),
            safetyConstraints = stringList(j, "safetyConstraints").mapNotNull {
                runCatching { SafetyConstraint.valueOf(it) }.getOrNull()
            }.toSet(),
            testSuite = (0 until tests.length()).map { index ->
                val item = tests.getJSONObject(index)
                TestDescriptor(
                    name = item.optString("name"),
                    type = enumValue(item.optString("type"), TestType.UNIT),
                    description = item.optString("description")
                )
            },
            benchmarkSuite = (0 until benchmarks.length()).map { index ->
                val item = benchmarks.getJSONObject(index)
                BenchmarkDescriptor(
                    name = item.optString("name"),
                    metric = item.optString("metric"),
                    baseline = item.optDouble("baseline", 0.0),
                    tolerance = item.optDouble("tolerance", 0.2)
                )
            },
            fitnessMetrics = fitness,
            mutationOperators = stringList(j, "mutationOperators").mapNotNull {
                runCatching { MutationOperator.valueOf(it) }.getOrNull()
            }.toSet(),
            compatibilityRequirements = CompatibilityRequirements(
                requiresGenomeIds = stringList(compatibility, "requiresGenomeIds").toSet(),
                incompatibleGenomeIds = stringList(compatibility, "incompatibleGenomeIds").toSet(),
                requiredCapabilities = stringList(compatibility, "requiredCapabilities").toSet()
            ),
            healthState = enumValue(j.optString("healthState"), GenomeHealth.UNKNOWN),
            provenance = Provenance(
                author = provenance.optString("author", j.optString("author", "unknown")),
                createdMs = provenance.optLong("createdMs", j.optLong("createdMs", 0L)),
                lastMutation = provenance.optString("lastMutation", ""),
                mutationOperator = provenance.optString("mutationOperator").takeIf { it.isNotBlank() }?.let {
                    runCatching { MutationOperator.valueOf(it) }.getOrNull()
                },
                sourceContext = provenance.optString("sourceContext", "")
            )
        )
    }

    private fun readPorts(array: JSONArray?): List<Port> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Port(
                name = item.optString("name"),
                type = item.optString("type"),
                nullable = item.optBoolean("nullable", false),
                description = item.optString("description")
            )
        }
    }

    private fun stringList(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun enumValue(value: String?, default: RuntimeRequirements.IsolationLevel): RuntimeRequirements.IsolationLevel {
        return try { RuntimeRequirements.IsolationLevel.valueOf(value.orEmpty()) } catch (_: Exception) { default }
    }

    private fun enumValue(value: String?, default: TestType): TestType =
        try { TestType.valueOf(value.orEmpty()) } catch (_: Exception) { default }

    private fun enumValue(value: String?, default: GenomeHealth): GenomeHealth =
        try { GenomeHealth.valueOf(value.orEmpty()) } catch (_: Exception) { default }

    private object ModelTools {
        fun read(array: JSONArray): List<ToolRequirement> =
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ToolRequirement(item.optString("toolId"), item.optBoolean("required", true))
            }
    }
}
