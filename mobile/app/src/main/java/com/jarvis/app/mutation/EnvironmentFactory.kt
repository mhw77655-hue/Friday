package com.jarvis.app.mutation

import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.EnvironmentRequirements
import com.jarvis.app.genome.Genome
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Mutant Environment Factory: constructs isolated execution environments
 * for generated micro-systems.
 *
 * The factory layer ships in the APK. Actual runtime backends (Termux,
 * isolated process, container, cloud) are pluggable — the factory does not
 * assume every environment can run inside the Android app process.
 */
class EnvironmentFactory(
    private val failureSurface: FailureSurface,
    private val workspaceRoot: File,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    // Backends registered by the host (Termux, subprocess, etc.)
    private val backends = ConcurrentHashMap<String, EnvironmentBackend>()

    fun registerBackend(name: String, backend: EnvironmentBackend) {
        backends[name] = backend
    }

    fun backendNames(): Set<String> = backends.keys

    /** Create a candidate environment for a genome. */
    suspend fun createEnvironment(
        genome: Genome,
        manifest: EnvironmentManifest,
        backendName: String = "process"
    ): EnvironmentCreationResult {
        val backend = backends[backendName]
        if (backend == null) {
            failureSurface.report(FailureReport(
                subsystem = "ENV_FACTORY",
                operation = "create",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.ENVIRONMENT,
                message = "Unknown environment backend: $backendName",
                source = "EnvironmentFactory"
            ))
            return EnvironmentCreationResult(success = false, error = "Unknown backend: $backendName")
        }

        // Resolve dependencies declared by the manifest
        val unresolved = manifest.dependencies.filter { dep -> !backend.canProvide(dep) }
        if (unresolved.isNotEmpty()) {
            failureSurface.report(FailureReport(
                subsystem = "ENV_FACTORY",
                operation = "resolve",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.DEPENDENCY,
                message = "Unresolved dependencies: $unresolved",
                source = "EnvironmentFactory"
            ))
            return EnvironmentCreationResult(success = false, error = "Unresolved dependencies: $unresolved")
        }

        // Create a workspace for this candidate
        val workspace = File(workspaceRoot, "candidate_${genome.id}_${nowMs()}")
        if (!workspace.exists() && !workspace.mkdirs()) {
            failureSurface.report(FailureReport(
                subsystem = "ENV_FACTORY",
                operation = "workspace",
                severity = FailureSeverity.ERROR,
                category = FailureCategory.ENVIRONMENT,
                message = "Could not create workspace: $workspace",
                source = "EnvironmentFactory"
            ))
            return EnvironmentCreationResult(success = false, error = "Could not create workspace")
        }

        val env = EnvironmentInstance(
            id = "env_${genome.id}_${nowMs()}",
            genomeId = genome.id,
            manifest = manifest,
            workspace = workspace,
            backend = backendName,
            status = EnvironmentStatus.CREATING,
            createdAtMs = nowMs()
        )

        return try {
            val ready = backend.initialize(env)
            if (!ready) {
                env.status = EnvironmentStatus.FAILED
                cleanup(env)
                EnvironmentCreationResult(success = false, error = "Backend init failed")
            } else {
                env.status = EnvironmentStatus.READY
                EnvironmentCreationResult(success = true, environment = env)
            }
        } catch (t: Throwable) {
            env.status = EnvironmentStatus.FAILED
            cleanup(env)
            failureSurface.report(FailureReport(
                subsystem = "ENV_FACTORY",
                operation = "initialize",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.ENVIRONMENT,
                message = "Environment init threw: ${t.message}",
                cause = t.message,
                source = "EnvironmentFactory"
            ))
            EnvironmentCreationResult(success = false, error = "Init threw: ${t.message}")
        }
    }

    /** Run a task inside a created environment. */
    suspend fun execute(env: EnvironmentInstance, task: String): EnvironmentExecResult {
        val backend = backends[env.backend] ?: return EnvironmentExecResult(success = false, error = "Backend gone")
        return try {
            backend.execute(env, task)
        } catch (t: Throwable) {
            failureSurface.report(FailureReport(
                subsystem = "ENV_FACTORY",
                operation = "execute",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.ENVIRONMENT,
                message = "Exec failed: ${t.message}",
                cause = t.message,
                source = "EnvironmentFactory"
            ))
            EnvironmentExecResult(success = false, error = t.message)
        }
    }

    /** Preserve a successful environment (move to archive, mark PROMOTED). */
    fun preserve(env: EnvironmentInstance): Boolean {
        if (env.status != EnvironmentStatus.READY) return false
        env.status = EnvironmentStatus.PRESERVED
        return true
    }

    /** Destroy a failed temporary environment. */
    fun destroy(env: EnvironmentInstance) {
        cleanup(env)
    }

    private fun cleanup(env: EnvironmentInstance) {
        if (env.status == EnvironmentStatus.PRESERVED) return
        env.workspace.deleteRecursively()
        env.status = EnvironmentStatus.DESTROYED
    }
}

// ── Data types ──

data class EnvironmentManifest(
    val id: String,
    val version: Int = 1,
    val dependencies: List<String> = emptyList(),
    val runtime: RuntimeSelection = RuntimeSelection.PROCESS_LOCAL,
    val modelRequirements: List<String> = emptyList(),
    val toolRequirements: List<String> = emptyList(),
    val permissions: Set<String> = emptySet(),
    val lifecycle: EnvironmentLifecycle = EnvironmentLifecycle.TEMPORARY
)

enum class RuntimeSelection {
    PROCESS_LOCAL,
    SEPARATE_PROCESS,
    TERMUX,
    CONTAINER,
    REMOTE,
    CLOUD
}

enum class EnvironmentLifecycle {
    TEMPORARY,      // destroyed after evaluation
    PERSISTENT,     // preserved if successful
    ALWAYS_PERSIST  // kept regardless
}

enum class EnvironmentStatus {
    CREATING, READY, RUNNING, PRESERVED, FAILED, DESTROYED
}

data class EnvironmentInstance(
    val id: String,
    val genomeId: String,
    val manifest: EnvironmentManifest,
    val workspace: File,
    val backend: String,
    var status: EnvironmentStatus,
    val createdAtMs: Long,
    var lastActivityMs: Long = 0
)

data class EnvironmentCreationResult(
    val success: Boolean,
    val environment: EnvironmentInstance? = null,
    val error: String? = null
)

data class EnvironmentExecResult(
    val success: Boolean,
    val output: String? = null,
    val exitCode: Int = 0,
    val error: String? = null
)

/** Pluggable backend contract. Implementations: process-local, Termux, subprocess, remote. */
interface EnvironmentBackend {
    val name: String
    fun canProvide(dependency: String): Boolean
    suspend fun initialize(env: EnvironmentInstance): Boolean
    suspend fun execute(env: EnvironmentInstance, task: String): EnvironmentExecResult
    suspend fun teardown(env: EnvironmentInstance)
}
