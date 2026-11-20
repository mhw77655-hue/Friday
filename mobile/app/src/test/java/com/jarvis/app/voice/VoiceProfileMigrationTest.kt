package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceProfileMigrationTest {

    @Test
    fun `legacy built in profile without provider migrates to Kokoro neural voice`() {
        val legacy = DefaultJarvisVoiceProfile.copy(
            preferredProvider = null,
            preferredVoice = "legacy-system-voice",
            fallbackProviders = listOf("system")
        )

        val migrated = migrateLegacyFridayVoiceProfile(legacy)

        assertEquals("sherpa", migrated.preferredProvider)
        assertEquals("speaker_2", migrated.preferredVoice)
        assertEquals(listOf("piper", "sherpa-arabic"), migrated.fallbackProviders)
    }

    @Test
    fun `legacy system provider migrates to Kokoro neural voice`() {
        val legacy = DefaultJarvisVoiceProfile.copy(
            preferredProvider = "system",
            preferredVoice = "en-us-x",
            fallbackProviders = listOf("system", "sherpa")
        )

        val migrated = migrateLegacyFridayVoiceProfile(legacy)

        assertEquals("sherpa", migrated.preferredProvider)
        assertEquals("speaker_2", migrated.preferredVoice)
        assertEquals(listOf("piper", "sherpa-arabic"), migrated.fallbackProviders)
    }

    @Test
    fun `reference voice remains Pocket TTS`() {
        val profile = DefaultJarvisVoiceProfile.copy(
            preferredProvider = null,
            referenceAudioPath = "/private/reference.wav"
        )

        val migrated = migrateLegacyFridayVoiceProfile(profile)

        assertEquals("pocket", migrated.preferredProvider)
        assertEquals("reference", migrated.preferredVoice)
    }

    @Test
    fun `explicit neural provider is preserved`() {
        val profile = DefaultJarvisVoiceProfile.copy(
            preferredProvider = "sherpa",
            preferredVoice = "kokoro"
        )

        val migrated = migrateLegacyFridayVoiceProfile(profile)

        assertEquals("sherpa", migrated.preferredProvider)
        assertEquals("kokoro", migrated.preferredVoice)
    }

    @Test
    fun `custom voice profiles are not rewritten`() {
        val profile = DefaultJarvisVoiceProfile.copy(
            id = "owner-custom",
            preferredProvider = null
        )

        val migrated = migrateLegacyFridayVoiceProfile(profile)

        assertEquals(profile, migrated)
    }
}
