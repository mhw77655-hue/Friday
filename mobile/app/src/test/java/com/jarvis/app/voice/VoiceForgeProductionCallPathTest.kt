package com.jarvis.app.voice

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.language.EgyptianArabicDialectDetector
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.termux.TermuxJarvisServer
import com.jarvis.app.voice.VoiceForgeBackend.VoiceForgeOutcome
import com.jarvis.app.voice.VoiceForgeSynthesisResponse.Audio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VOICE-FORGE-EGYPTIAN-KAREN-TTS AC3: the REAL production call path
 * (TermuxJarvisServer -> LatencyPipeline -> CognitiveEngine.process ->
 * model generation -> spoken-output seam) with a recording fake swapped in
 * ONLY at the final HTTP hop (the VoiceForgeSynthesizer seam).
 *
 * Every object between the server and the synthesizer is the real production
 * class — the same mirror-the-last-hop discipline as
 * ReasoningTierServeGroundTruthTest / DialectIntegrationTest. The reply-shaped
 * text that reaches [VoiceForgeBackend.synthesize] is the final assistant
 * text, verified against the one the model authority produced.
 */
class VoiceForgeProductionCallPathTest {

    private var activeUserText = ""

    private fun recordingPipeline(
        server: TermuxJarvisServer,
        outgoingPayloads: MutableList<String>,
        spoken: MutableList<String>,
        outcomes: MutableList<VoiceForgeOutcome>
    ): LatencyPipeline = LatencyPipeline(
        dispatch = { it() },
        scheduleDelayed = { _, _ -> },
        bridgeSend = { text ->
            outgoingPayloads.add(text)
            server.modelManager.send(text)
        },
        bridgeStatus = { server.modelManager.status.value },
        fullSpeak = { text ->
            val signal = EgyptianArabicDialectDetector().detect(activeUserText)
            val outcome = runBlocking {
                server.voiceForgeBackend.synthesize(
                    text = text,
                    signal = signal,
                    platformFallback = { spoken.add(it) }
                )
            }
            outcomes.add(outcome)
        },
        beginExchange = { _, _ -> null },
        express = { reply, _ -> StyledResponse.Approved(reply) },
        completeExchange = { _, _, _, _, _ -> },
        sessionContext = { null },
        cognitiveEngine = server.engine
    )

    private suspend fun driveReply(pipeline: LatencyPipeline, server: TermuxJarvisServer, userText: String): String {
        activeUserText = userText
        pipeline.onUserInput(userText)
        val finalReply = server.modelManager.lastReply.first { it.isNotBlank() }
        pipeline.onReplyReady(finalReply)
        return finalReply
    }

    private fun assertSynthRequest(
        synth: RecordingVoiceForgeSynthesizer,
        finalReply: String,
        checkpoint: String,
        language: String
    ) {
        val req = synth.lastRequest()
        assertTrue("final assistant text must reach the synthesize hop", req != null)
        assertEquals(finalReply, req!!.text)
        assertEquals(checkpoint, req.checkpoint)
        assertEquals(language, req.language)
        assertEquals(0.65f, req.exaggeration, 0.0001f)
        assertEquals(1.7f, req.cfgWeight, 0.0001f)
        assertEquals(
            "voice reference clip must ride the production route",
            "assets/voiceforge/venon_voice_reference.wav",
            req.audioPromptPath
        )
    }

    @Test
    fun `egyptian flagged turn - final text reaches adapter with egyptian checkpoint and tuned params`(): Unit =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer()
            val server = TermuxJarvisServer(
                port = 0,
                backendOverride = FakeModelBackend(),
                voiceForgeSynthesizerOverride = synth
            )
            val outgoing = mutableListOf<String>()
            val spoken = mutableListOf<String>()
            val outcomes = mutableListOf<VoiceForgeOutcome>()
            val pipeline = recordingPipeline(server, outgoing, spoken, outcomes)
            try {
                val finalReply = driveReply(pipeline, server, "ايه رايك كده")

                assertSynthRequest(synth, finalReply, "NAMAA-Egyptian-TTS", "ar-EG")
                assertTrue(
                    "the engine itself flagged the turn Egyptian in the assembled payload",
                    outgoing.last().contains("user dialect: ar-EG")
                )
                assertTrue("successful synthesis is a synthesized outcome", outcomes.single() is VoiceForgeOutcome.Synthesized)
                assertTrue("no platform fallback spoke on success", spoken.isEmpty())
            } finally {
                server.stop()
            }
        }

    @Test
    fun `english turn - final text reaches adapter with base checkpoint`(): Unit =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer()
            val server = TermuxJarvisServer(
                port = 0,
                backendOverride = FakeModelBackend(),
                voiceForgeSynthesizerOverride = synth
            )
            val outgoing = mutableListOf<String>()
            val spoken = mutableListOf<String>()
            val outcomes = mutableListOf<VoiceForgeOutcome>()
            val pipeline = recordingPipeline(server, outgoing, spoken, outcomes)
            try {
                val finalReply = driveReply(pipeline, server, "hello there")

                assertSynthRequest(synth, finalReply, "chatterbox-multilingual", "en")
                assertTrue("english turn carries no dialect flag", !outgoing.last().contains("user dialect:"))
                assertTrue(outcomes.single() is VoiceForgeOutcome.Synthesized)
            } finally {
                server.stop()
            }
        }

    @Test
    fun `synthesis failure falls back to platform speech instead of silence`(): Unit =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer(RecordingVoiceForgeSynthesizer.Mode.FAIL)
            val server = TermuxJarvisServer(
                port = 0,
                backendOverride = FakeModelBackend(),
                voiceForgeSynthesizerOverride = synth
            )
            val outgoing = mutableListOf<String>()
            val spoken = mutableListOf<String>()
            val outcomes = mutableListOf<VoiceForgeOutcome>()
            val pipeline = recordingPipeline(server, outgoing, spoken, outcomes)
            try {
                val finalReply = driveReply(pipeline, server, "ايه رايك كده")

                assertSynthRequest(synth, finalReply, "NAMAA-Egyptian-TTS", "ar-EG")
                val outcome = outcomes.single()
                assertTrue("failure must fall back to platform TTS", outcome is VoiceForgeOutcome.FellBack)
                assertEquals("the reply text reached the platform fallback", listOf(finalReply), spoken)
                assertTrue("no silent turn while a fallback exists", outcome !is VoiceForgeOutcome.Silent)
            } finally {
                server.stop()
            }
        }
}