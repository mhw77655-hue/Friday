package com.jarvis.app.microsystem

import com.jarvis.app.failure.FailureReport
import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.MutationOperator
import com.jarvis.app.resource.ResourceRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Micro-system contract + registry. Plain JVM (JUnit 4).
 */
class MicroSystemRegistryTest {

    private class FakeMicroSystem(
        override val id: String,
        private val caps: Set<String>,
        private val sourceGenome: Genome
    ) : MicroSystemContract {
        override val genome: Genome = sourceGenome
        override suspend fun initialize() {}
        override suspend fun onPromoted() {}
        override suspend fun shutdown() {}
        override val isReady: Boolean = true
        override val capabilities: Set<String> = caps
        override val requiredCapabilities: Set<String> = emptySet()

        private val _health = MutableStateFlow(MicroSystemHealth(MicroSystemStatus.HEALTHY))
        override val health: StateFlow<MicroSystemHealth> = _health.asStateFlow()
        override suspend fun checkHealth(): MicroSystemHealth = _health.value

        override val resourceRequirements: ResourceRequest = ResourceRequest(50, 5.0, 1000)
        private val _resources = MutableStateFlow(CurrentResourceUsage())
        override val currentResources: StateFlow<CurrentResourceUsage> = _resources.asStateFlow()

        private val _incoming = MutableSharedFlow<SyncMessage>()
        override val incoming: kotlinx.coroutines.flow.SharedFlow<SyncMessage> = _incoming.asSharedFlow()

        private val _outgoing = MutableSharedFlow<SyncMessage>()
        override val outgoing: kotlinx.coroutines.flow.SharedFlow<SyncMessage> = _outgoing.asSharedFlow()

        var delivered: MutableList<SyncMessage> = mutableListOf()
        override suspend fun deliver(message: SyncMessage) { delivered += message }
        override fun emit(message: SyncMessage) {}

        override fun reportFailure(report: FailureReport) {}

        private val _telemetry = MutableStateFlow<Map<String, Any>>(emptyMap())
        override val telemetry: StateFlow<Map<String, Any>> = _telemetry.asStateFlow()
    }

    private fun genome(id: String): Genome = GenomeBuilder(id)
        .capability("speech_to_text")
        .mutationOperator(MutationOperator.PARAMETER_MUTATION)
        .build()

    @Test
    fun `register and retrieve`() {
        val registry = MicroSystemRegistry()
        val ms = FakeMicroSystem("ms1", setOf("stt"), genome("g1"))
        registry.register(ms)
        assertTrue(registry.has("ms1"))
        assertNotNull(registry.get("ms1"))
        assertEquals(1, registry.count())
    }

    @Test
    fun `unregister removes`() {
        val registry = MicroSystemRegistry()
        registry.register(FakeMicroSystem("ms1", setOf("stt"), genome("g1")))
        registry.unregister("ms1")
        assertFalse(registry.has("ms1"))
        assertNull(registry.get("ms1"))
        assertEquals(0, registry.count())
    }

    @Test
    fun `find by capability`() {
        val registry = MicroSystemRegistry()
        registry.register(FakeMicroSystem("stt1", setOf("speech_to_text"), genome("g1")))
        registry.register(FakeMicroSystem("tts1", setOf("text_to_speech"), genome("g2")))
        val sttProviders = registry.provides("speech_to_text")
        assertEquals(1, sttProviders.size)
        assertEquals("stt1", sttProviders[0].id)
    }

    @Test
    fun `deliver routes message to microsystem`() {
        val registry = MicroSystemRegistry()
        val ms = FakeMicroSystem("ms1", setOf("stt"), genome("g1"))
        registry.register(ms)

        runBlocking {
            ms.deliver(SyncMessage(from = "nervous", to = "ms1", topic = "input", payload = "hello"))
        }
        assertEquals(1, ms.delivered.size)
        assertEquals("hello", ms.delivered[0].payload)
    }

    @Test
    fun `genome contract exposes version`() {
        val ms = FakeMicroSystem("ms1", setOf("stt"), genome("g1"))
        assertEquals(1, ms.version)
        assertEquals("g1", ms.genome.id)
    }
}
