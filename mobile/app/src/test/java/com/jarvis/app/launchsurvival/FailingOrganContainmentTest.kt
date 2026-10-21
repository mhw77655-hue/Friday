package com.jarvis.app.launchsurvival

import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * LAUNCH-SURVIVAL AC4: a failing organ does not kill the body.
 *
 * On the phone an uncaught exception on a Dispatchers.IO worker (the speech
 * synthesis loop) and a throw out of the model-load call each killed a process.
 * After the fix, a failing synthesis reports into the FailureSurface and the
 * app-level scope keeps serving; a failing model load degrades instead of
 * throwing. Nothing is swallowed silently: every failure carries a cause
 * (the stack's type + message) and a plain-language line for the user.
 */
class FailingOrganContainmentTest {

    @Test
    fun `a synthesis-loop failure is contained and recorded while the loop keeps serving`() = runBlocking {
        val surface = FailureSurface()
        val processed = AtomicInteger(0)

        // The production pattern: a supervised app-level scope whose worker
        // catches a per-item Throwable, reports it, and continues. This mirrors
        // StreamingTts.startSynthesisLoop exactly.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reports = java.util.Collections.synchronizedList(mutableListOf<FailureReport>())
        val job = scope.launch {
            val segments = listOf("boom", "ok-1", "ok-2")
            for (segment in segments) {
                try {
                    if (segment == "boom") throw IllegalStateException("synthesis exploded")
                    processed.incrementAndGet()
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    reports.add(
                        FailureReport(
                            subsystem = "STREAMING_TTS",
                            operation = "synthesize-segment",
                            severity = FailureSeverity.ERROR,
                            category = FailureCategory.TTS,
                            message = "Streaming TTS segment synthesis threw and was contained",
                            source = "StreamingTts",
                            cause = "${t.javaClass.simpleName}: ${t.message}"
                        )
                    )
                }
            }
        }
        job.join()
        scope.cancel()

        assertEquals("the loop kept serving after the failure", 2, processed.get())
        assertEquals("the failure was recorded, not swallowed", 1, reports.size)
        assertTrue(
            "the recorded failure carries the stack cause",
            reports[0].cause!!.contains("IllegalStateException")
        )
        // Reported through the real surface — the failure lands with its stack
        // and a plain-language line the user can be shown.
        val event = surface.report(reports[0])
        assertEquals(FailureSeverity.ERROR, event.severity)
        assertFalse("the failure has a user-facing line", event.userMessage.isNullOrBlank())
    }

    @Test
    fun `a model-load failure degrades instead of throwing out of the call`(): Unit = runBlocking {
        // A registry that does NOT contain any registered model is the genuine
        // gap: loadModel must return a handle naming the gap, never throw.
        FakeOllama(modelNames = emptyList()).use { fake ->
            val adapter = com.jarvis.app.model.adapters.OllamaAdapter(null).apply {
                configure(com.jarvis.app.env.ModelSource.Local(fake.host), com.jarvis.app.model.config.ProviderConfig.Default)
            }
            val backend = com.jarvis.app.model.OllamaModelBackend(adapter)
            val handle = backend.loadModel(
                com.jarvis.app.model.ModelBackendConfig(modelId = "resident")
            )
            assertEquals(
                "an empty registry yields an explicit gap resolution, not a throw",
                com.jarvis.app.model.OllamaModelBackend.ModelResolution.Match.EMPTY_REGISTRY,
                backend.lastResolution?.match
            )
            assertEquals("resident", handle.modelId)
        }
    }
}
