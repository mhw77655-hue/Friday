package com.jarvis.app.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CapabilityRouterTest {

    private lateinit var registry: CapabilityRegistry
    private lateinit var router: DeterministicCapabilityRouter

    @Before
    fun setUp() {
        registry = CapabilityRegistry()
        router = DeterministicCapabilityRouter(registry)
    }

    private fun register(
        id: String,
        name: String,
        category: CapabilityRegistry.Category,
        quality: CapabilityRegistry.QualityTier = CapabilityRegistry.QualityTier.MEDIUM,
        state: CapabilityRegistry.State = CapabilityRegistry.State.LOADED,
        health: CapabilityRegistry.Health = CapabilityRegistry.Health.HEALTHY,
        languages: Set<String> = emptySet(),
        fallback: String? = null,
        confidence: Float = 1.0f
    ): String {
        return registry.register(
            CapabilityRegistry.Capability(
                id = id,
                version = "1.0",
                name = name,
                function = "test capability $name",
                category = category,
                languages = languages,
                quality = quality,
                confidence = confidence,
                currentState = state,
                health = health,
                fallback = fallback,
                ramEstimateMb = 128,
                latencyMs = 100
            )
        )
    }

    @Test
    fun `router is wired to the real CapabilityRegistry instance`() {
        val id = register("route_tts", "Route TTS", CapabilityRegistry.Category.TTS)
        assertNotNull("Computer-under-test must read the same registry it is constructed with", registry.get(id))

        runTestSuspend {
            val result = router.route(
                CapabilityRouter.RouteRequest(category = CapabilityRegistry.Category.TTS)
            )
            assertTrue(result is CapabilityRouter.RouteResult.Matched)
            val matched = result as CapabilityRouter.RouteResult.Matched
            assertEquals(id, matched.capability.id)
            assertEquals(CapabilityRouter.MatchTactic.EXACT_CATEGORY, matched.matchedBy)
        }
    }

    @Test
    fun `router routes the real production-registered capability via findBest`() {
        // Mirror the real capability that JarvisEngine.init registers (voice_organism_v1 / TTS).
        register(
            id = "voice_organism_v1",
            name = "Voice organism (synthesis)",
            category = CapabilityRegistry.Category.TTS,
            quality = CapabilityRegistry.QualityTier.HIGH,
            languages = setOf("en")
        )

        runTestSuspend {
            val result = router.route(
                CapabilityRouter.RouteRequest(category = CapabilityRegistry.Category.TTS, language = "en")
            )
            assertTrue(result is CapabilityRouter.RouteResult.Matched)
            assertEquals("voice_organism_v1", (result as CapabilityRouter.RouteResult.Matched).capability.id)
        }
    }

    @Test
    fun `deterministic backend routes each currently-real category`() {
        register("route_stt", "KL STT", CapabilityRegistry.Category.STT)
        register("route_tts", "KL TTS", CapabilityRegistry.Category.TTS)
        register("route_llm", "KL LLM", CapabilityRegistry.Category.LLM)
        register("route_mem", "KL Memory", CapabilityRegistry.Category.MEMORY)

        val categories = listOf(
            CapabilityRegistry.Category.STT,
            CapabilityRegistry.Category.TTS,
            CapabilityRegistry.Category.LLM,
            CapabilityRegistry.Category.MEMORY
        )
        runTestSuspend {
            for (category in categories) {
                val result = router.route(CapabilityRouter.RouteRequest(category = category))
                assertTrue("Expected Match for $category but got $result", result is CapabilityRouter.RouteResult.Matched)
                assertEquals(category, (result as CapabilityRouter.RouteResult.Matched).capability.category)
            }
        }
    }

    @Test
    fun `route returns NoMatch with ranked candidates when category has no healthy capability`() {
        register("route_stt", "KL STT", CapabilityRegistry.Category.STT)
        register("route_tts", "KL TTS", CapabilityRegistry.Category.TTS)

        runTestSuspend {
            val result = router.route(
                CapabilityRouter.RouteRequest(category = CapabilityRegistry.Category.VISUAL, requireHealthy = true)
            )
            assertTrue(result is CapabilityRouter.RouteResult.NoMatch)
            val noMatch = result as CapabilityRouter.RouteResult.NoMatch
            assertTrue(noMatch.rankedCandidates.isNotEmpty())
            assertTrue(noMatch.rankedCandidates.all { it.health == CapabilityRegistry.Health.HEALTHY })
        }
    }

    @Test
    fun `route is Ambiguous when two same-category capabilities tie on score`() {
        register(
            "route_llm_a", "LLM A", CapabilityRegistry.Category.LLM,
            quality = CapabilityRegistry.QualityTier.HIGH, confidence = 0.9f
        )
        register(
            "route_llm_b", "LLM B", CapabilityRegistry.Category.LLM,
            quality = CapabilityRegistry.QualityTier.HIGH, confidence = 0.9f
        )

        runTestSuspend {
            val result = router.route(CapabilityRouter.RouteRequest(category = CapabilityRegistry.Category.LLM))
            assertTrue(result is CapabilityRouter.RouteResult.Ambiguous)
            val ambiguous = result as CapabilityRouter.RouteResult.Ambiguous
            assertEquals(2, ambiguous.candidates.size)
        }
    }

    @Test
    fun `route falls back to fallback chain when primary healthy unavailable for category`() {
        // Primary STT present but NOT healthy/available; fallback STT is healthy.
        register(
            "route_stt_unhealthy", "STT Unhealthy", CapabilityRegistry.Category.STT,
            state = CapabilityRegistry.State.FAILED,
            health = CapabilityRegistry.Health.FAILED,
            fallback = "route_tts"
        )
        register("route_tts", "KL TTS", CapabilityRegistry.Category.TTS)

        runTestSuspend {
            val result = router.route(CapabilityRouter.RouteRequest(category = CapabilityRegistry.Category.STT))
            assertTrue(result is CapabilityRouter.RouteResult.Matched)
            val matched = result as CapabilityRouter.RouteResult.Matched
            assertEquals("route_tts", matched.capability.id)
            assertEquals(CapabilityRouter.MatchTactic.FALLBACK_AFTER_CATEGORY, matched.matchedBy)
        }
    }

    @Test
    fun `explicit test - no Needle2 model file download or native binary is introduced`() {
        // The real router uses only CapabilityRegistry lookups - no model files,
        // no downloads, no native libraries, no learned-rank backend.
        assertTrue("router must be the deterministic rule-based backend", router is DeterministicCapabilityRouter)
        // Assert no model-download / native-load entry point exists on the router's public surface.
        val methodSurface = router::class.java.methods.map { it.name }
        assertTrue("router must have no loadModel / download / native entry points", methodSurface.none {
            it.contains("loadModel", ignoreCase = true) ||
                it.contains("loadNative", ignoreCase = true) ||
                it.contains("initNative", ignoreCase = true) ||
                it.contains("download", ignoreCase = true)
        })
    }

    @Test
    fun `explicit test - routing production sources contain no native model loading`() {
        val productionSources = listOf(
            "src/main/java/com/jarvis/app/capability/CapabilityRouter.kt",
            "src/main/java/com/jarvis/app/capability/DeterministicCapabilityRouter.kt"
        )
        for (source in productionSources) {
            val text = java.io.File(source).readText()
            assertTrue(
                "No native model-loading mechanism (model file path, download, native lib) may appear in $source",
                !text.contains(".onnx") &&
                    !text.contains(".gguf") &&
                    !text.contains("System.loadLibrary") &&
                    !text.contains("java.net.HttpURLConnection") &&
                    !text.contains("ProcessBuilder")
            )
        }
    }

    private fun runTestSuspend(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
