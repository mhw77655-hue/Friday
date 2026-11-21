package ai.friday.assistant.voice.provider

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceAudioLoaderTest {

    @Test
    fun readsPcm16MonoReferenceWithoutChangingItsSampleRate() {
        val file = writeWave(sampleRate = 24_000, channels = 1, seconds = 2)
        val loaded = ReferenceAudioLoader.load(file)
        assertEquals(24_000, loaded.sampleRate)
        assertEquals(48_000, loaded.samples.size)
        assertTrue(loaded.samples.any { kotlin.math.abs(it) > 0.01f })
        file.delete()
    }

    @Test
    fun downmixesStereoReferenceToMono() {
        val file = writeWave(sampleRate = 16_000, channels = 2, seconds = 2)
        val loaded = ReferenceAudioLoader.load(file)
        assertEquals(16_000, loaded.sampleRate)
        assertEquals(32_000, loaded.samples.size)
        file.delete()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCompressedOrNonPcmWave() {
        val file = writeWave(sampleRate = 24_000, channels = 1, seconds = 2, format = 3)
        try {
            ReferenceAudioLoader.load(file)
        } finally {
            file.delete()
        }
    }

    private fun writeWave(sampleRate: Int, channels: Int, seconds: Int, format: Int = 1): File {
        val frameCount = sampleRate * seconds
        val pcm = ByteArray(frameCount * channels * 2)
        var index = 0
        for (frame in 0 until frameCount) {
            val sample = (kotlin.math.sin(frame * 2.0 * Math.PI * 440.0 / sampleRate) * 12_000).toInt().toShort()
            repeat(channels) {
                pcm[index++] = sample.toByte()
                pcm[index++] = (sample.toInt() shr 8).toByte()
            }
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
            fun le16(value: Int) { out.writeByte(value and 0xff); out.writeByte((value shr 8) and 0xff) }
            fun le32(value: Int) {
                out.writeByte(value and 0xff)
                out.writeByte((value shr 8) and 0xff)
                out.writeByte((value shr 16) and 0xff)
                out.writeByte((value shr 24) and 0xff)
            }
            ascii("RIFF"); le32(36 + pcm.size); ascii("WAVE")
            ascii("fmt "); le32(16); le16(format); le16(channels)
            le32(sampleRate); le32(sampleRate * channels * 2)
            le16(channels * 2); le16(16)
            ascii("data"); le32(pcm.size); out.write(pcm)
        }
        return File.createTempFile("friday-reference-", ".wav").apply { writeBytes(bytes.toByteArray()) }
    }
}
