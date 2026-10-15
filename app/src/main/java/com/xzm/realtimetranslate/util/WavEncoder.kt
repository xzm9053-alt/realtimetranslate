package com.xzm.realtimetranslate.util

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin/JVM utility to prepend standard 44-byte RIFF/WAVE headers to raw PCM16LE audio.
 * Used for multipart/form-data file uploads to standard /v1/audio/transcriptions endpoints
 * (SiliconFlow, Groq, Whisper, etc.).
 */
object WavEncoder {

    /**
     * Wrap 16kHz 16-bit mono PCM little-endian byte array with standard WAV header.
     */
    fun encodePcm16leToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16_000,
        channels: Short = 1,
        bitsPerSample: Short = 16,
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val totalDataLen = pcmData.size
        val totalAudioLen = totalDataLen + 36

        val buffer = ByteBuffer.allocate(44 + totalDataLen).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalAudioLen)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt sub-chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size (16 for PCM)
        buffer.putShort(1) // AudioFormat (1 for PCM)
        buffer.putShort(channels)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample)

        // data sub-chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(totalDataLen)

        // Audio payload
        buffer.put(pcmData)

        return buffer.array()
    }
}
