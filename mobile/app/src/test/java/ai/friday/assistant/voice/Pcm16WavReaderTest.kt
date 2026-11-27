package ai.friday.assistant.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class Pcm16WavReaderTest {
    @Test
    fun `reads ordinary little endian mono pcm16 wav`() {
        val pcm = byteArrayOf(0x00, 0x10, 0x00, 0x20, 0x00, 0x30, 0x00, 0x40)
        withWav(pcm, channels = 1, bitsPerSample = 16) { file ->
            assertArrayEquals(pcm, Pcm16WavReader.read(file, expectedChannels = 1))
        }
    }

    @Test
    fun `reads stereo pcm16 and skips padded ancillary chunks`() {
        val pcm = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0)
        withWav(pcm, channels = 2, bitsPerSample = 16, includeOddJunkChunk = true) { file ->
            assertArrayEquals(pcm, Pcm16WavReader.read(file, expectedChannels = 2))
        }
    }

    @Test
    fun `rejects a channel mismatch`() {
        withWav(byteArrayOf(1, 0, 2, 0), channels = 1, bitsPerSample = 16) { file ->
            assertNull(Pcm16WavReader.read(file, expectedChannels = 2))
        }
    }

    @Test
    fun `rejects non pcm16 data`() {
        withWav(byteArrayOf(1, 0, 2, 0), channels = 1, bitsPerSample = 8) { file ->
            assertNull(Pcm16WavReader.read(file, expectedChannels = 1))
        }
    }

    private fun withWav(
        pcm: ByteArray,
        channels: Int,
        bitsPerSample: Int,
        includeOddJunkChunk: Boolean = false,
        block: (File) -> Unit
    ) {
        val body = ByteArrayOutputStream()
        body.write("WAVE".toByteArray(Charsets.US_ASCII))
        if (includeOddJunkChunk) {
            body.write("JUNK".toByteArray(Charsets.US_ASCII))
            body.writeUInt32Le(1)
            body.write(0x55)
            body.write(0) // RIFF chunk padding byte
        }
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        body.write("fmt ".toByteArray(Charsets.US_ASCII))
        body.writeUInt32Le(16)
        body.writeUInt16Le(1) // WAVE_FORMAT_PCM
        body.writeUInt16Le(channels)
        body.writeUInt32Le(16_000)
        body.writeUInt32Le(16_000 * blockAlign)
        body.writeUInt16Le(blockAlign)
        body.writeUInt16Le(bitsPerSample)
        body.write("data".toByteArray(Charsets.US_ASCII))
        body.writeUInt32Le(pcm.size.toLong())
        body.write(pcm)

        val riffBody = body.toByteArray()
        val wav = ByteArrayOutputStream()
        wav.write("RIFF".toByteArray(Charsets.US_ASCII))
        wav.writeUInt32Le(riffBody.size.toLong())
        wav.write(riffBody)

        val file = File.createTempFile("friday-pcm16-", ".wav")
        try {
            file.writeBytes(wav.toByteArray())
            block(file)
        } finally {
            file.delete()
        }
    }

    private fun ByteArrayOutputStream.writeUInt16Le(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeUInt32Le(value: Long) {
        write((value and 0xFF).toInt())
        write(((value ushr 8) and 0xFF).toInt())
        write(((value ushr 16) and 0xFF).toInt())
        write(((value ushr 24) and 0xFF).toInt())
    }
}
