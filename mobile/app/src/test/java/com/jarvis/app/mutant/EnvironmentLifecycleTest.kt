package com.jarvis.app.mutant

import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.mutation.EnvironmentBackend
import com.jarvis.app.mutation.EnvironmentExecResult
import com.jarvis.app.mutation.EnvironmentInstance
import com.jarvis.app.mutation.EnvironmentManifest
import com.jarvis.app.mutation.EnvironmentStatus
import com.jarvis.app.mutation.RuntimeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Dependency Resolver (self-environment creation) + Environment Lifecycle
 * (disposable habitat state machine). Plain JVM (JUnit 4).
 */
class EnvironmentLifecycleTest {

    private class StubBackend(
        override val name: String,
        private val provides: List<String>
    ) : EnvironmentBackend {
        override fun canProvide(dependency: String): Boolean = dependency in provides
        override suspend fun initialize(env: EnvironmentInstance): Boolean = true
        override suspend fun execute(env: EnvironmentInstance, task: String): EnvironmentExecResult =
            EnvironmentExecResult(success = true, output = "ok", exitCode = 0)
        override suspend fun teardown(env: EnvironmentInstance) {}
    }

    private fun env(id: String, backend: String) = EnvironmentInstance(
        id = id, genomeId = "g", manifest = EnvironmentManifest(
            id = id, dependencies = listOf(), runtime = RuntimeSelection.PROCESS_LOCAL,
            lifecycle = com.jarvis.app.mutation.EnvironmentLifecycle.TEMPORARY
        ),
        workspace = File("/tmp/$id"), backend = backend,
        status = EnvironmentStatus.CREATING, createdAtMs = 0
    )

    @Test
    fun `resolver picks the preferred capable backend`() {
        val resolver = DependencyResolver(
            backends = { listOf(StubBackend("process", listOf("a", "b")), StubBackend("termux", listOf("a", "b", "c"))) },
            preferredBackend = "termux"
        )
        val genome = GenomeBuilder("g").capability("x").build()
        val resolution = resolver.resolve(genome, EnvironmentManifest(
            id = "e", dependencies = listOf("a", "b"), runtime = RuntimeSelection.PROCESS_LOCAL,
            lifecycle = com.jarvis.app.mutation.EnvironmentLifecycle.TEMPORARY
        ))
        assertTrue(resolution.resolved)
        assertEquals("termux", resolution.backendName)
        assertTrue(resolution.unresolved.isEmpty())
    }

    @Test
    fun `resolver reports unresolved dependencies when no backend can provide them`() {
        val resolver = DependencyResolver(backends = { listOf(StubBackend("process", listOf("a"))) })
        val genome = GenomeBuilder("g").capability("x").build()
        val resolution = resolver.resolve(genome, EnvironmentManifest(
            id = "e", dependencies = listOf("a", "ffmpeg"), runtime = RuntimeSelection.PROCESS_LOCAL,
            lifecycle = com.jarvis.app.mutation.EnvironmentLifecycle.TEMPORARY
        ))
        assertFalse(resolution.resolved)
        assertEquals(listOf("ffmpeg"), resolution.unresolved)
    }

    @Test
    fun `resolver folds required genome tools into the dependency set`() {
        val resolver = DependencyResolver(backends = { listOf(StubBackend("termux", listOf("tool:ffmpeg"))) })
        val genome = GenomeBuilder("g").capability("x").tool("ffmpeg", required = true).build()
        val resolution = resolver.resolve(genome, EnvironmentManifest(
            id = "e", dependencies = listOf(), runtime = RuntimeSelection.PROCESS_LOCAL,
            lifecycle = com.jarvis.app.mutation.EnvironmentLifecycle.TEMPORARY
        ))
        assertTrue(resolution.resolved)
        assertTrue("tool:ffmpeg" in resolution.resolvedDependencies)
    }

    @Test
    fun `lifecycle enforces legal environment transitions`() {
        val instance = env("e1", "process")
        // CREATING → READY ✓
        assertTrue(EnvironmentLifecycle.transition(instance, EnvironmentStatus.READY))
        // READY → RUNNING ✓
        assertTrue(EnvironmentLifecycle.transition(instance, EnvironmentStatus.RUNNING))
        // RUNNING → READY ✓
        assertTrue(EnvironmentLifecycle.transition(instance, EnvironmentStatus.READY))
        // READY → PRESERVED ✓
        assertTrue(EnvironmentLifecycle.transition(instance, EnvironmentStatus.PRESERVED))
        // PRESERVED → READY ✗ (preserved habitats are never reused)
        assertFalse(EnvironmentLifecycle.transition(instance, EnvironmentStatus.READY))
        assertEquals(EnvironmentStatus.PRESERVED, instance.status)
        // PRESERVED → DESTROYED ✓
        assertTrue(EnvironmentLifecycle.transition(instance, EnvironmentStatus.DESTROYED))
        assertTrue(EnvironmentLifecycle.isTerminal(instance.status))
    }

    @Test
    fun `lifecycle rejects reuse of a destroyed environment`() {
        val instance = env("e2", "process")
        EnvironmentLifecycle.transition(instance, EnvironmentStatus.READY)
        EnvironmentLifecycle.transition(instance, EnvironmentStatus.DESTROYED)
        assertFalse(EnvironmentLifecycle.transition(instance, EnvironmentStatus.READY))
        assertFalse(EnvironmentLifecycle.isLive(instance.status))
    }
}
