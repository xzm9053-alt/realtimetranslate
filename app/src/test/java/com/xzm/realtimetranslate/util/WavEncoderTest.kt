package com.xzm.realtimetranslate.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {

    @Test
    fun `encodePcm16leToWav generates valid 44-byte RIFF WAVE header`() {
        val sampleRate = 16_000
        val channels: Short = 1
        val bitsPerSample: Short = 16
        // 100 samples of 16-bit audio = 200 bytes
        val fakePcm = ByteArray(200) { (it % 128).toByte() }

        val wav = WavEncoder.encodePcm16leToWav(
            pcmData = fakePcm,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
        )

        // Total size = 44 byte header + 200 byte payload
        assertEquals(244, wav.size)

        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        // Chunk ID: "RIFF"
        val riff = ByteArray(4)
        buffer.get(riff)
        assertEquals("RIFF", String(riff))

        // ChunkSize: 36 + data size
        val chunkSize = buffer.getInt()
        assertEquals(36 + fakePcm.size, chunkSize)

        // Format: "WAVE"
        val wave = ByteArray(4)
        buffer.get(wave)
        assertEquals("WAVE", String(wave))

        // Subchunk1ID: "fmt "
        val fmt = ByteArray(4)
        buffer.get(fmt)
        assertEquals("fmt ", String(fmt))

        // Subchunk1Size: 16 for PCM
        val subchunk1Size = buffer.getInt()
        assertEquals(16, subchunk1Size)

        // AudioFormat: 1 (PCM)
        val audioFormat = buffer.getShort()
        assertEquals(1.toShort(), audioFormat)

        // NumChannels
        val numChannels = buffer.getShort()
        assertEquals(channels, numChannels)

        // SampleRate
        val actualSampleRate = buffer.getInt()
        assertEquals(sampleRate, actualSampleRate)

        // ByteRate: SampleRate * NumChannels * BitsPerSample/8 = 16000 * 1 * 2 = 32000
        val byteRate = buffer.getInt()
        assertEquals(32000, byteRate)

        // BlockAlign: NumChannels * BitsPerSample/8 = 2
        val blockAlign = buffer.getShort()
        assertEquals(2.toShort(), blockAlign)

        // BitsPerSample: 16
        val actualBits = buffer.getShort()
        assertEquals(bitsPerSample, actualBits)

        // Subchunk2ID: "data"
        val dataHeader = ByteArray(4)
        buffer.get(dataHeader)
        assertEquals("data", String(dataHeader))

        // Subchunk2Size: payload size
        val dataSize = buffer.getInt()
        assertEquals(fakePcm.size, dataSize)

        // Payload data
        val payload = ByteArray(fakePcm.size)
        buffer.get(payload)
        assertArrayEquals(fakePcm, payload)
    }

    @Test
    fun `encodePcm16leToWav works with empty PCM`() {
        val emptyPcm = ByteArray(0)
        val wav = WavEncoder.encodePcm16leToWav(emptyPcm)

        assertEquals(44, wav.size)
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        val riff = ByteArray(4)
        buffer.get(riff)
        assertEquals("RIFF", String(riff))

        val chunkSize = buffer.getInt()
        assertEquals(36, chunkSize)
    }
}
