package com.jarvis.app.mutant

import com.jarvis.app.mutation.EnvironmentBackend
import com.jarvis.app.mutation.EnvironmentExecResult
import com.jarvis.app.mutation.EnvironmentInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * The in-process execution backend for the Mutant Environment.
 *
 * A generated spec cannot be serialized to disk (its logic lives in a
 * runtime function), so the backend keeps a registry of registered specs
 * and executes string tasks against them. Registered specs are keyed by
 * implementation id; a task is `run:<specId>:<jsonInput>` and returns the
 * JSON form of the spec's loud [SpecOutput].
 *
 * This is the "process" backend the [com.jarvis.app.mutation.EnvironmentFactory]
 * ships with. Termux / container / remote backends plug in behind the same
 * [EnvironmentBackend] contract — the factory does not assume every
 * environment runs inside the app process.
 */
class ProcessLocalBackend(
    private val sandbox: ResourceSandbox = ResourceSandbox(),
    private val defaultTimeoutMs: Long = 2_000
) : EnvironmentBackend {

    override val name: String = "process"

    private val registeredSpecs = ConcurrentHashMap<String, AlgorithmSpec>()

    /** Register a spec so `run:<specId>:<json>` tasks can execute it. */
    fun register(specId: String, spec: AlgorithmSpec) {
        registeredSpecs[specId] = spec
    }

    fun unregister(specId: String) {
        registeredSpecs.remove(specId)
    }

    fun has(specId: String): Boolean = registeredSpecs.containsKey(specId)

    override fun canProvide(dependency: String): Boolean =
        dependency in SUPPORTED_DEPENDENCIES || dependency.startsWith("spec:")

    override suspend fun initialize(env: EnvironmentInstance): Boolean = true

    override suspend fun execute(env: EnvironmentInstance, task: String): EnvironmentExecResult =
        withContext(Dispatchers.IO) {
            val result: EnvironmentExecResult = try {
                val parsed = parseTask(task)
                if (parsed == null) {
                    EnvironmentExecResult(success = false, error = "Malformed task: $task")
                } else {
                    val (specId, jsonInput) = parsed
                    val spec = registeredSpecs[specId]
                    if (spec == null) {
                        EnvironmentExecResult(success = false, error = "Unknown spec: $specId")
                    } else {
                        val input = toInputMap(jsonInput)
                        val run = sandbox.run(spec, input, defaultTimeoutMs)
                        if (run.success) {
                            EnvironmentExecResult(
                                success = true,
                                output = JSONObject(toJsonMap(run.data as? Map<*, *> ?: emptyMap<String, Any>())).toString(),
                                exitCode = 0
                            )
                        } else {
                            EnvironmentExecResult(
                                success = false,
                                error = run.error ?: "spec failed",
                                exitCode = 1
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                EnvironmentExecResult(success = false, error = "backend threw: ${t.message}", exitCode = 2)
            }
            result
        }

    override suspend fun teardown(env: EnvironmentInstance) {
        // registered specs are unregistered by MutantEnvironment explicitly
    }

    private fun parseTask(task: String): Pair<String, JSONObject>? {
        if (!task.startsWith("run:")) return null
        val idx = task.indexOf(':', 4)
        if (idx < 0) return null
        val specId = task.substring(4, idx)
        val json = runCatching { JSONObject(task.substring(idx + 1)) }.getOrNull() ?: return null
        return specId to json
    }

    private fun toInputMap(json: JSONObject): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()
        json.keys().forEach { key -> map[key] = json.get(key) }
        return map
    }

    private fun toJsonMap(map: Map<*, *>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        map.forEach { (k, v) -> if (v != null) out[k.toString()] = v }
        return out
    }

    companion object {
        val SUPPORTED_DEPENDENCIES = setOf("kotlin-runtime", "spec", "none")
    }
}
