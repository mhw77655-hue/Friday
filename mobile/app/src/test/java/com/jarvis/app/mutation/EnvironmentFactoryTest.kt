package com.jarvis.app.mutation

import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.MutationOperator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mutant environment factory: isolated workspaces, backend resolution,
 * dependency resolution, preserve/destroy lifecycle. Plain JVM (JUnit 4).
 */
class EnvironmentFactoryTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("env-factory-test", "").apply {
        delete()
        mkdirs()
        tempDirs += this
    }

    private class StubBackend(
        override val name: String,
        private val canProvideList: List<String> = emptyList()
    ) : EnvironmentBackend {
        override fun canProvide(dependency: String): Boolean = dependency in canProvideList
        override suspend fun initialize(env: EnvironmentInstance): Boolean = true
        override suspend fun execute(env: EnvironmentInstance, task: String): EnvironmentExecResult =
            EnvironmentExecResult(success = true, output = "ran: $task", exitCode = 0)
        override suspend fun teardown(env: EnvironmentInstance) {}
    }

    private fun genome(id: String) = GenomeBuilder(id)
        .capability("speech_to_text")
        .mutationOperator(MutationOperator.PARAMETER_MUTATION)
        .build()

    @Test
    fun `creates environment with workspace when backend exists`() = runBlocking {
        val root = tmpDir()
        val factory = EnvironmentFactory(FailureSurface(), root)
        factory.registerBackend("process", StubBackend("process"))

        val result = factory.createEnvironment(genome("g1"), EnvironmentManifest(id = "m1"))
        assertTrue(result.success)
        assertTrue(result.environment?.workspace?.exists() == true)
        assertEquals(EnvironmentStatus.READY, result.environment?.status)
    }

    @Test
    fun `unknown backend is rejected and reported to failure surface`() = runBlocking {
        val root = tmpDir()
        val surface = FailureSurface()
        val factory = EnvironmentFactory(surface, root)

        val result = factory.createEnvironment(genome("g1"), EnvironmentManifest(id = "m1"), backendName = "missing")
        assertFalse(result.success)
        assertTrue(result.error?.contains("missing") == true)
        assertTrue(surface.recentFailures.value.isNotEmpty())
    }

    @Test
    fun `unresolved dependencies are rejected`() = runBlocking {
        val root = tmpDir()
        val factory = EnvironmentFactory(FailureSurface(), root)
        factory.registerBackend("process", StubBackend("process", canProvideList = listOf("kotlin")))

        val result = factory.createEnvironment(
            genome("g1"),
            EnvironmentManifest(id = "m1", dependencies = listOf("kotlin", "missing-lib"))
        )
        assertFalse(result.success)
        assertTrue(result.error?.contains("missing-lib") == true)
    }

    @Test
    fun `execute runs task in ready environment`() = runBlocking {
        val root = tmpDir()
        val factory = EnvironmentFactory(FailureSurface(), root)
        factory.registerBackend("process", StubBackend("process"))
        val created = factory.createEnvironment(genome("g1"), EnvironmentManifest(id = "m1"))
        assertTrue(created.success)

        val exec = factory.execute(created.environment!!, "run_tests")
        assertTrue(exec.success)
        assertEquals("ran: run_tests", exec.output)
    }

    @Test
    fun `preserve keeps workspace while destroy cleans it`() = runBlocking {
        val root = tmpDir()
        val factory = EnvironmentFactory(FailureSurface(), root)
        factory.registerBackend("process", StubBackend("process"))

        val preserved = factory.createEnvironment(genome("p1"), EnvironmentManifest(id = "m1")).environment!!
        assertTrue(factory.preserve(preserved))
        assertTrue(preserved.workspace.exists())
        assertEquals(EnvironmentStatus.PRESERVED, preserved.status)

        val destroyed = factory.createEnvironment(genome("d1"), EnvironmentManifest(id = "m2")).environment!!
        factory.destroy(destroyed)
        assertFalse(destroyed.workspace.exists())
        assertEquals(EnvironmentStatus.DESTROYED, destroyed.status)
    }

    @Test
    fun `preserved environments are not cleaned up`() = runBlocking {
        val root = tmpDir()
        val factory = EnvironmentFactory(FailureSurface(), root)
        factory.registerBackend("process", StubBackend("process"))
        val env = factory.createEnvironment(genome("g1"), EnvironmentManifest(id = "m1")).environment!!
        factory.preserve(env)
        factory.destroy(env) // should be a no-op for preserved
        assertTrue(env.workspace.exists())
        assertEquals(EnvironmentStatus.PRESERVED, env.status)
    }

    @Test
    fun `backend list reflects registered backends`() {
        val root = tmpDir()
        val factory = EnvironmentFactory(FailureSurface(), root)
        factory.registerBackend("process", StubBackend("process"))
        factory.registerBackend("termux", StubBackend("termux"))
        assertEquals(setOf("process", "termux"), factory.backendNames())
    }
}
