package com.jarvis.app.voice

import com.jarvis.app.language.DialectSignal
import com.jarvis.app.voice.VoiceForgeBackend.VoiceForgeOutcome
import com.jarvis.app.voice.VoiceForgeSynthesisResponse.Audio
import com.jarvis.app.voice.VoiceForgeSynthesisResponse.Failed
import com.jarvis.app.voice.VoiceForgeSynthesisResponse.Unreachable
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recording fake for the SINGLE final HTTP hop (the VoiceForgeSynthesizer seam). */
class RecordingVoiceForgeSynthesizer(
    private val mode: Mode = Mode.AUDIO
) : VoiceForgeSynthesizer {

    enum class Mode { AUDIO, FAIL, UNREACHABLE }

    private val stateLock = Any()
    private val requests = mutableListOf<VoiceForgeSynthesisRequest>()

    override suspend fun synthesize(request: VoiceForgeSynthesisRequest): VoiceForgeSynthesisResponse {
        synchronized(stateLock) { requests.add(request) }
        return when (mode) {
            Mode.AUDIO -> Audio(wavBytes = byteArrayOf(0x52, 0x49), sampleRate = 24000, channels = 1, durationMs = 500L)
            Mode.FAIL -> Failed(reason = "forced synthesis failure")
            Mode.UNREACHABLE -> throw IOException("forced connection failure")
        }
    }

    override suspend fun health(): VoiceForgeHealth = VoiceForgeHealth(healthy = mode == Mode.AUDIO)

    fun lastRequest(): VoiceForgeSynthesisRequest? = synchronized(stateLock) { requests.lastOrNull() }

    fun allRequests(): List<VoiceForgeSynthesisRequest> = synchronized(stateLock) { requests.toList() }
}

/**
 * VOICE-FORGE-EGYPTIAN-KAREN-TTS (AC3a/AC3b/AC3c, pure-JVM unit layer):
 * the backend routes by the real DialectSignal, carries its OWN configured
 * checkpoint/exaggeration/cfgWeight/audioPromptPath with the text (never the
 * server's defaults), and degrades to platform fallback or silence — it never
 * throws.
 */
class VoiceForgeBackendTest {

    private fun egyptianSignal() = DialectSignal(
        detectedLanguageMix = "ar-EG",
        dialectConfidence = 0.9f,
        codeSwitchPoints = listOf(0),
        register = "informal"
    )

    private fun englishSignal() = DialectSignal(
        detectedLanguageMix = "en",
        dialectConfidence = 0f,
        codeSwitchPoints = emptyList(),
        register = "formal"
    )

    @Test
    fun `egyptian signal routes to the egyptian checkpoint with configured parameters`() =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer()
            val backend = VoiceForgeBackend(
                synthesizer = synth,
                audioPromptPath = "assets/voiceforge/venon_voice_reference.wav"
            )

            val outcome = backend.synthesize(
                text = "عايز أفهم دة دلوقتي",
                signal = egyptianSignal()
            )

            assertTrue("Egyptian turn must synthesize audio", outcome is VoiceForgeOutcome.Synthesized)
            val outcomeAudio = outcome as VoiceForgeOutcome.Synthesized
            assertEquals(24000, outcomeAudio.sampleRate)
            assertEquals(1, outcomeAudio.channels)

            val req = synth.lastRequest()
            assertTrue("request must reach the synthesize hop", req != null)
            assertEquals("NAMAA-Egyptian-TTS", req!!.checkpoint)
            assertEquals("ar-EG", req.language)
            assertEquals(0.65f, req.exaggeration, 0.0001f)
            assertEquals(1.7f, req.cfgWeight, 0.0001f)
            assertEquals("assets/voiceforge/venon_voice_reference.wav", req.audioPromptPath)
            assertEquals("عايز أفهم دة دلوقتي", req.text)

            assertEquals(1, backend.counts.first)
            assertEquals("NAMAA-Egyptian-TTS", backend.lastCheckpoint)
        }

    @Test
    fun `non-egyptian turn uses the base multilingual checkpoint in english`() =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer()
            val backend = VoiceForgeBackend(synthesizer = synth)

            val outcome = backend.synthesize(text = "hello there", signal = englishSignal())

            assertTrue(outcome is VoiceForgeOutcome.Synthesized)
            val req = synth.lastRequest()
            assertEquals("chatterbox-multilingual", req!!.checkpoint)
            assertEquals("en", req.language)
            assertEquals(0.65f, req.exaggeration, 0.0001f)
            assertEquals(1.7f, req.cfgWeight, 0.0001f)
        }

    @Test
    fun `null signal uses the base checkpoint`() =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer()
            val backend = VoiceForgeBackend(synthesizer = synth)

            backend.synthesize(text = "just a reply")

            val req = synth.lastRequest()
            assertEquals("chatterbox-multilingual", req!!.checkpoint)
            assertEquals("en", req.language)
        }

    @Test
    fun `request always carries the configured parameters not server defaults`() =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer()
            val backend = VoiceForgeBackend(
                synthesizer = synth,
                exaggeration = 0.5f,
                cfgWeight = 2.0f
            )

            backend.synthesize(text = "trust me", signal = englishSignal())

            val req = synth.lastRequest()
            assertEquals(0.5f, req!!.exaggeration, 0.0001f)
            assertEquals(2.0f, req.cfgWeight, 0.0001f)
        }

    @Test
    fun `failed synthesis with fallback falls back to platform speech`() =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer(RecordingVoiceForgeSynthesizer.Mode.FAIL)
            val spoken = mutableListOf<String>()
            val backend = VoiceForgeBackend(
                synthesizer = synth,
                platformFallback = { text -> spoken.add(text) }
            )

            val outcome = backend.synthesize(text = "hello there", signal = englishSignal())

            assertTrue("failure must fall back, never silence", outcome is VoiceForgeOutcome.FellBack)
            assertEquals(listOf("hello there"), spoken)
            assertEquals(1, backend.counts.second)
            assertEquals(0, backend.counts.third)
        }

    @Test
    fun `unreachable synthesis without fallback degrades to silence - never throws`() =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer(RecordingVoiceForgeSynthesizer.Mode.UNREACHABLE)
            val backend = VoiceForgeBackend(synthesizer = synth)

            val outcome = backend.synthesize(text = "hello there")

            assertTrue("no fallback wired must yield silence, not a crash", outcome is VoiceForgeOutcome.Silent)
            assertEquals(1, backend.counts.third)
        }

    @Test
    fun `synthesizer that throws is treated as unreachable`() =
        runBlocking {
            val synth = RecordingVoiceForgeSynthesizer(RecordingVoiceForgeSynthesizer.Mode.UNREACHABLE)
            val spoken = mutableListOf<String>()
            val backend = VoiceForgeBackend(synthesizer = synth, platformFallback = { spoken.add(it) })

            val outcome = backend.synthesize(text = "ah", signal = egyptianSignal())

            assertEquals("NAMAA-Egyptian-TTS", (outcome as VoiceForgeOutcome.FellBack).checkpoint)
            assertEquals(listOf("ah"), spoken)
        }
}