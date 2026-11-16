package com.jarvis.app.voice.provider

import java.io.File
import java.io.RandomAccessFile

internal data class ReferenceAudio(
    val samples: FloatArray,
    val sampleRate: Int
)

/**
 * Reads a small PCM16 RIFF/WAVE reference clip for Pocket TTS.
 *
 * Only uncompressed mono/stereo PCM is accepted. Input is bounded to 12 seconds
 * and 6 MiB before allocating sample arrays; stereo is downmixed to mono.
 */
internal object ReferenceAudioLoader {
    private const val MAX_FILE_BYTES = 6L * 1024L * 1024L
    private const val MAX_DURATION_SECONDS = 12.0
    private const val MIN_DURATION_SECONDS = 1.0

    fun load(file: File): ReferenceAudio {
        require(file.isFile) { "Reference WAV does not exist" }
        require(file.length() in 44L..MAX_FILE_BYTES) { "Reference WAV is empty or exceeds the 6 MiB safety limit" }

        RandomAccessFile(file, "r").use { raf ->
            fun fourCc(): String {
                val bytes = ByteArray(4)
                raf.readFully(bytes)
                return String(bytes, Charsets.US_ASCII)
            }
            fun uint32le(): Long = Integer.toUnsignedLong(Integer.reverseBytes(raf.readInt()))
            fun uint16le(): Int = java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(raf.readShort()))

            require(fourCc() == "RIFF") { "Reference audio must be a RIFF/WAVE file" }
            raf.skipBytes(4)
            require(fourCc() == "WAVE") { "Reference audio must be a RIFF/WAVE file" }

            var format = -1
            var channels = 0
            var sampleRate = 0
            var bitsPerSample = 0
            var data: ByteArray? = null

            while (raf.filePointer + 8L <= raf.length()) {
                val chunkId = fourCc()
                val chunkSize = uint32le()
                val chunkStart = raf.filePointer
                val chunkEnd = chunkStart + chunkSize
                require(chunkEnd <= raf.length()) { "Reference WAV contains a truncated chunk" }

                when (chunkId) {
                    "fmt " -> {
                        require(chunkSize >= 16L) { "Reference WAV format chunk is too short" }
                        format = uint16le()
                        channels = uint16le()
                        sampleRate = uint32le().toInt()
                        raf.skipBytes(6)
                        bitsPerSample = uint16le()
                    }
                    "data" -> {
                        require(chunkSize <= MAX_FILE_BYTES) { "Reference WAV audio data is too large" }
                        data = ByteArray(chunkSize.toInt())
                        raf.readFully(data)
                    }
                }
                raf.seek(chunkEnd + (chunkSize and 1L))
            }

            require(format == 1) { "Reference WAV must use uncompressed PCM" }
            require(channels in 1..2) { "Reference WAV must be mono or stereo" }
            require(bitsPerSample == 16) { "Reference WAV must use 16-bit PCM" }
            require(sampleRate in 16_000..48_000) { "Reference WAV sample rate must be between 16 and 48 kHz" }

            val pcm = data ?: throw IllegalArgumentException("Reference WAV has no audio data")
            val frameBytes = channels * 2
            val frameCount = pcm.size / frameBytes
            val duration = frameCount.toDouble() / sampleRate.toDouble()
            require(duration in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) {
                "Reference WAV must contain between 1 and 12 seconds of audio"
            }

            val mono = FloatArray(frameCount)
            var byteIndex = 0
            for (frame in 0 until frameCount) {
                var sum = 0
                repeat(channels) {
                    val raw = (pcm[byteIndex].toInt() and 0xFF) or
                        (pcm[byteIndex + 1].toInt() shl 8)
                    sum += raw.toShort().toInt()
                    byteIndex += 2
                }
                mono[frame] = (sum.toFloat() / channels / 32768f).coerceIn(-1f, 1f)
            }
            return ReferenceAudio(mono, sampleRate)
        }
    }
}
