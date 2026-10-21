package com.jarvis.app.launchsurvival

import com.jarvis.app.env.ModelSource
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OllamaModelBackend
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.model.adapters.OllamaAdapter
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LAUNCH-SURVIVAL AC1(b): reproduce CRASH B before touching production code.
 *
 * The phone's real situation, rebuilt on the production model-loading path:
 * the active provider carries no modelId (the phone's environment switch and
 * the manager's own heuristic default both leave ProviderConfig.Default), so
 * loading the RESIDENT tier falls through ModelManager.defaultModelId's
 * "resident" literal against an Ollama that registers
 * jarvis-reasoning:latest and jarvis-resident:latest.
 *
 * The production path under test is the same one the anchor launch takes:
 * ModelManager.request(RESIDENT) -> loadOnce -> the ONE ModelBackend
 * (OllamaModelBackend over real HTTP to the fake server's /api/tags).
 * Loading the resident tier must RESOLVE to jarvis-resident:latest.
 */
class ResidentTierLoadTest {

    @Test
    fun `loading the resident tier resolves to jarvis-resident-latest against the real registry shape`(): Unit =
        runBlocking {
            FakeOllama().use { fake ->
                val adapter = OllamaAdapter(null).apply {
                    configure(ModelSource.Local(fake.host), ProviderConfig.Default)
                }
                val backend = OllamaModelBackend(adapter)
                val modelManager = ModelManager(
                    context = null,
                    scope = CoroutineScope(Dispatchers.IO),
                    backend = backend,
                    resourceGovernor = ResourceGovernor(),
                    autoSweep = false
                )

                // The exact call the anchor launch makes at boot.
                val resident = modelManager.request(OrganRole.RESIDENT, task = "launch-survival.ac1b")

                assertEquals(
                    "the resident tier resolves to the registered resident model",
                    "jarvis-resident:latest",
                    resident.modelId
                )
                val resolution = backend.lastResolution
                assertEquals(
                    "the requested bare tier name 'resident' degraded to a registered model",
                    true,
                    resolution?.degraded
                )
                assertEquals(
                    "the degraded resolution names a model the server actually registers",
                    true,
                    resolution?.resolvedId in (resolution?.registered ?: emptyList())
                )
            }
        }
}
