package com.jarvis.app.voice

import android.content.Context
import com.jarvis.app.voice.provider.ProviderHealth
import com.jarvis.app.voice.provider.VoiceCapabilities
import com.jarvis.app.voice.provider.VoiceInitResult
import com.jarvis.app.voice.provider.VoiceInfo
import com.jarvis.app.voice.provider.VoiceProvider
import com.jarvis.app.voice.provider.VoiceProviderRegistry
import com.jarvis.app.voice.provider.VoiceProviderSelector
import com.jarvis.app.voice.provider.VoiceQuality
import com.jarvis.app.voice.provider.VoiceSynthesisParams
import com.jarvis.app.voice.provider.VoiceSynthesisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R2 — deterministic provider selection. The selector must explain WHY it
 * picked a voice: language match, explicit preference, quality/resource
 * preference, cross-language fallback, or a hard, structured failure — never a
 * silent no-op.
 */
class VoiceProviderSelectorTest {

    private class FakeProvider(
        override val id: String,
        private val languages: Set<String>,
        private val quality: VoiceQuality,
        private val voices: List<VoiceInfo> = emptyList(),
        private val memoryMb: Long = 0
    ) : VoiceProvider {
        override val name: String = id
        override fun capabilities(): VoiceCapabilities = VoiceCapabilities(
            providerId = id,
            languages = languages,
            voices = voices,
            streaming = false,
            cloning = false,
            offline = true,
            estimatedLatencyMs = 100,
            estimatedMemoryMb = memoryMb,
            modelSizeMb = 0,
            startupCostMs = 0,
            quality = quality,
            interruptible = true,
            needsPhonemizer = false
        )
        override fun voices(): List<VoiceInfo> = voices
        override suspend fun initialize(context: Context): VoiceInitResult = VoiceInitResult(ok = true)
        override suspend fun synthesize(
            text: String, language: String, voiceId: String?,
            params: VoiceSynthesisParams, outFile: File
        ): VoiceSynthesisResult = VoiceSynthesisResult(success = true, sampleRate = 24000, channels = 1, durationMs = 100, audioFile = outFile)
        override fun cancel() {}
        override suspend fun healthCheck(): ProviderHealth = ProviderHealth(healthy = true, providerId = id)
        override fun release() {}
    }

    private fun registry(vararg fakes: Pair<FakeProvider, Int>): VoiceProviderRegistry =
        VoiceProviderRegistry().apply {
            fakes.forEach { (p, prio) -> register(p, priority = prio) }
        }

    private fun en(priority: Int = 0) = FakeProvider(
        "sherpa", setOf("en"), VoiceQuality.HIGH,
        listOf(VoiceInfo("speaker_0", "Speaker 0", "en", quality = VoiceQuality.HIGH))
    )

    @Test
    fun `selects the only en provider for an en profile`() {
        val sel = VoiceProviderSelector(registry(en() to 0)).select(
            DefaultJarvisVoiceProfile.copy(language = "en")
        )
        assertTrue(sel is VoiceProviderSelector.Selection.Selected)
        assertEquals("sherpa", (sel as VoiceProviderSelector.Selection.Selected).provider.id)
    }

    @Test
    fun `routes ar to the arabic-capable provider`() {
        val system = FakeProvider("system", setOf("ar-EG"), VoiceQuality.MEDIUM)
        val sel = VoiceProviderSelector(registry(en() to 0, system to 100)).select(
            DefaultJarvisVoiceProfile.copy(language = "ar-EG")
        )
        assertTrue(sel is VoiceProviderSelector.Selection.Selected)
        assertEquals("system", (sel as VoiceProviderSelector.Selection.Selected).provider.id)
    }

    @Test
    fun `cross-language fallback is explicit`() {
        val sel = VoiceProviderSelector(registry(en() to 0)).select(
            DefaultJarvisVoiceProfile.copy(language = "fr")
        )
        assertTrue(sel is VoiceProviderSelector.Selection.Fallback)
        val fb = sel as VoiceProviderSelector.Selection.Fallback
        assertEquals("fr", fb.requestedLanguage)
        assertEquals("sherpa", fb.provider.id)
        assertTrue(fb.reason.contains("fr"))
    }

    @Test
    fun `hard failure when nothing is enabled`() {
        val sel = VoiceProviderSelector(VoiceProviderRegistry()).select(DefaultJarvisVoiceProfile)
        assertTrue(sel is VoiceProviderSelector.Selection.Failure)
    }

    @Test
    fun `explicit preferred provider wins over priority`() {
        val system = FakeProvider("system", setOf("en", "ar-EG"), VoiceQuality.MEDIUM)
        val sel = VoiceProviderSelector(registry(system to 100, en() to 0)).select(
            DefaultJarvisVoiceProfile.copy(language = "en", preferredProvider = "system")
        )
        assertTrue(sel is VoiceProviderSelector.Selection.Selected)
        assertEquals("system", (sel as VoiceProviderSelector.Selection.Selected).provider.id)
    }

    @Test
    fun `preferred voice is resolved against the chosen provider`() {
        val p = FakeProvider(
            "sherpa", setOf("en"), VoiceQuality.HIGH,
            listOf(
                VoiceInfo("speaker_0", "S0", "en", quality = VoiceQuality.HIGH),
                VoiceInfo("speaker_3", "S3", "en", quality = VoiceQuality.HIGH)
            )
        )
        val sel = VoiceProviderSelector(registry(p to 0)).select(
            DefaultJarvisVoiceProfile.copy(language = "en", preferredVoice = "speaker_3")
        ) as VoiceProviderSelector.Selection.Selected
        assertEquals("speaker_3", sel.voice?.id)
    }

    @Test
    fun `battery preference favours the light provider on a language tie`() {
        val heavy = FakeProvider("sherpa", setOf("en"), VoiceQuality.HIGH, memoryMb = 220)
        val light = FakeProvider("system", setOf("en"), VoiceQuality.MEDIUM, memoryMb = 20)
        val sel = VoiceProviderSelector(registry(heavy to 0, light to 100)).select(
            DefaultJarvisVoiceProfile.copy(language = "en", preferredProvider = null, resourcePreference = ResourcePreference.BATTERY)
        )
        assertTrue(sel is VoiceProviderSelector.Selection.Selected)
        assertEquals("system", (sel as VoiceProviderSelector.Selection.Selected).provider.id)
    }

    @Test
    fun `quality preference selects the high-quality provider`() {
        val heavy = FakeProvider("sherpa", setOf("en"), VoiceQuality.HIGH, memoryMb = 220)
        val light = FakeProvider("system", setOf("en"), VoiceQuality.MEDIUM, memoryMb = 20)
        val sel = VoiceProviderSelector(registry(heavy to 100, light to 0)).select(
            DefaultJarvisVoiceProfile.copy(language = "en", resourcePreference = ResourcePreference.QUALITY)
        )
        assertTrue(sel is VoiceProviderSelector.Selection.Selected)
        assertEquals("sherpa", (sel as VoiceProviderSelector.Selection.Selected).provider.id)
    }
}
