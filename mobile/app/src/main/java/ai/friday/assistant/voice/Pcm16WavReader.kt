package ai.friday.assistant.voice

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads the PCM16 payload from a RIFF/WAVE file.
 *
 * RIFF numeric fields are little-endian. RandomAccessFile's readShort/readInt
 * are big-endian, so using them directly silently rejects ordinary WAV files.
 */
internal object Pcm16WavReader {
    fun read(file: File, expectedChannels: Int): ByteArray? {
        if (expectedChannels !in 1..2 || !file.isFile) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 12) return null
                if (readFourCc(raf) != "RIFF") return null
                val riffSize = readUInt32Le(raf)
                if (readFourCc(raf) != "WAVE") return null
                val riffEnd = 8L + riffSize
                if (riffEnd > raf.length() || riffEnd < 12L) return null

                var pcmFormat = false
                var channels = 0
                var bitsPerSample = 0

                while (raf.filePointer + 8L <= riffEnd) {
                    val chunkId = readFourCc(raf)
                    val chunkSize = readUInt32Le(raf)
                    val chunkStart = raf.filePointer
                    val chunkEnd = chunkStart + chunkSize
                    if (chunkEnd < chunkStart || chunkEnd > riffEnd || chunkSize > Int.MAX_VALUE) {
                        return null
                    }

                    when (chunkId) {
                        "fmt " -> {
                            if (chunkSize < 16L) return null
                            val audioFormat = readUInt16Le(raf)
                            channels = readUInt16Le(raf)
                            readUInt32Le(raf) // sample rate
                            readUInt32Le(raf) // byte rate
                            readUInt16Le(raf) // block align
                            bitsPerSample = readUInt16Le(raf)
                            pcmFormat = audioFormat == 1
                        }
                        "data" -> {
                            if (!pcmFormat || channels != expectedChannels || bitsPerSample != 16) {
                                return null
                            }
                            if (chunkSize == 0L || chunkSize % (channels * 2L) != 0L) return null
                            val pcm = ByteArray(chunkSize.toInt())
                            raf.readFully(pcm)
                            return pcm
                        }
                    }

                    raf.seek(chunkEnd + (chunkSize and 1L))
                }
                null
            }
        }.getOrNull()
    }

    private fun readFourCc(raf: RandomAccessFile): String {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readUInt16Le(raf: RandomAccessFile): Int {
        val low = raf.readUnsignedByte()
        val high = raf.readUnsignedByte()
        return low or (high shl 8)
    }

    private fun readUInt32Le(raf: RandomAccessFile): Long {
        val b0 = raf.readUnsignedByte().toLong()
        val b1 = raf.readUnsignedByte().toLong()
        val b2 = raf.readUnsignedByte().toLong()
        val b3 = raf.readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
