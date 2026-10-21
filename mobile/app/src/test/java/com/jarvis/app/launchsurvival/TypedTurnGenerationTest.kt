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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LAUNCH-SURVIVAL AC3: names are resolved, not assumed, and a typed turn
 * returns generated text through the production path.
 *
 * The phone observed Ollama receive only GET /health (404) and GET /api/tags
 * (200) and NEVER a generate call — because the resident load threw first. With
 * the name resolution fixed, the same production path (ModelManager ->
 * OllamaModelBackend -> real HTTP /api/chat) must reach the fake server and
 * return its generated text for one typed turn.
 */
class TypedTurnGenerationTest {

    @Test
    fun `one typed turn returns generated text through the production model path`(): Unit =
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

                // Production shape: load the tier (the anchor's boot call),
                // book it for the turn, then send the user's text.
                val resident = modelManager.request(OrganRole.RESIDENT, task = "launch-survival.ac3")
                assertEquals("jarvis-resident:latest", resident.modelId)

                modelManager.noteTurnServe(resident)
                modelManager.send("hello friday")

                // send() is async; wait for the reply to land (bounded).
                var waited = 0
                while (modelManager.lastReply.value.isBlank() && waited < 5_000) {
                    delay(50)
                    waited += 50
                }

                assertEquals(
                    "the typed turn is answered by the fake Ollama generate path",
                    "fake-ollama reply",
                    modelManager.lastReply.value
                )
                assertTrue(
                    "generation really went through the fake server's /api/chat",
                    fake.chatRequests >= 1
                )
                assertEquals(
                    "the /api/chat request targeted the resolved resident model",
                    "jarvis-resident:latest",
                    fake.lastChatModel
                )
            }
        }
}
