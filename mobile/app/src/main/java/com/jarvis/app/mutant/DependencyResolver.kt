package com.jarvis.app.mutant

import com.jarvis.app.genome.Genome
import com.jarvis.app.mutation.EnvironmentBackend
import com.jarvis.app.mutation.EnvironmentManifest

/**
 * Dependency Resolver — maps a candidate genome's declared dependencies to a
 * concrete execution backend (§16 self-environment creation).
 *
 * A candidate may require a runtime, a model, a tool, or a native library.
 * The resolver checks which registered [EnvironmentBackend] can provide the
 * full set, preferring the process-local backend when it suffices (the
 * cheapest valid execution habitat) and falling back to Termux / container /
 * remote backends. The production APK is never a dependency target: a
 * candidate that needs something the phone cannot isolate is unresolved and
 * the evolution layer rejects it before any production code is touched (§20).
 */
class DependencyResolver(
    private val backends: () -> Collection<EnvironmentBackend>,
    private val preferredBackend: String = "process"
) {

    fun resolve(genome: Genome, manifest: EnvironmentManifest): DependencyResolution {
        val required = LinkedHashSet<String>(manifest.dependencies)
        // Genome tool/model requirements become environment dependencies for the
        // backend: a candidate that needs an un-installable tool cannot run.
        genome.toolRequirements.forEach { if (it.required) required += "tool:${it.toolId}" }
        genome.modelRequirements.models.forEach { if (it.required) required += "model:${it.id}" }

        val backendsNow = backends().toList()
        val capable = backendsNow.filter { backend ->
            required.all { backend.canProvide(it) }
        }
        val chosen = capable.firstOrNull { it.name == preferredBackend } ?: capable.firstOrNull()

        val unresolved = if (chosen == null) {
            required.filter { dep -> backendsNow.none { it.canProvide(dep) } }
        } else {
            emptyList()
        }

        return DependencyResolution(
            resolved = chosen != null,
            backendName = chosen?.name,
            unresolved = unresolved,
            resolvedDependencies = if (chosen != null) required.toList() else emptyList(),
            note = when {
                chosen == null && unresolved.isNotEmpty() ->
                    "No backend can provide: ${unresolved.joinToString(", ")}"
                chosen == null -> "No environment backend available"
                else -> "Dependencies resolved on backend '${chosen.name}'"
            }
        )
    }
}

/** Outcome of resolving a candidate's dependency set to a backend. */
data class DependencyResolution(
    val resolved: Boolean,
    val backendName: String?,
    val unresolved: List<String>,
    val resolvedDependencies: List<String>,
    val note: String
)
