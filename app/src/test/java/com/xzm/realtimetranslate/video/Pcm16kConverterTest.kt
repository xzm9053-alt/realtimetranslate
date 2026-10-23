package com.xzm.realtimetranslate.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pcm16kConverterTest {

    private fun pcm16(vararg samples: Int): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, value ->
            bytes[i * 2] = (value and 0xff).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
        }
        return bytes
    }

    private fun float32(vararg samples: Float): ByteArray {
        val bytes = ByteArray(samples.size * 4)
        samples.forEachIndexed { i, value ->
            val bits = value.toRawBits()
            bytes[i * 4] = (bits and 0xff).toByte()
            bytes[i * 4 + 1] = ((bits shr 8) and 0xff).toByte()
            bytes[i * 4 + 2] = ((bits shr 16) and 0xff).toByte()
            bytes[i * 4 + 3] = ((bits shr 24) and 0xff).toByte()
        }
        return bytes
    }

    @Test
    fun `16k mono pcm16 passes through unchanged`() {
        val converter = Pcm16kConverter(16_000, 1, PcmEncoding.PCM16)
        val out = converter.convert(pcm16(16384, -16384, 0, 8192))
        // Linear interpolation needs one trailing sample as an anchor.
        assertEquals(3, out.size)
        assertEquals(0.5f, out[0], 0.001f)
        assertEquals(-0.5f, out[1], 0.001f)
        assertEquals(0f, out[2], 0.001f)
    }

    @Test
    fun `stereo frames are downmixed to their average`() {
        val converter = Pcm16kConverter(16_000, 2, PcmEncoding.PCM16)
        val out = converter.convert(pcm16(16384, 0, 16384, 16384, 0, 0))
        assertEquals(2, out.size)
        assertEquals(0.25f, out[0], 0.001f)
        assertEquals(0.5f, out[1], 0.001f)
    }

    @Test
    fun `48k input is resampled to roughly one third the length`() {
        val frames = 4800
        val samples = IntArray(frames) { if (it % 2 == 0) 16384 else -16384 }
        val converter = Pcm16kConverter(48_000, 1, PcmEncoding.PCM16)
        val out = converter.convert(pcm16(*samples))
        assertEquals(1600.0, out.size.toDouble(), 3.0)
    }

    @Test
    fun `chunked input produces the same timeline as one shot`() {
        val frames = 4800
        val samples = IntArray(frames) { if (it % 2 == 0) 16384 else -16384 }
        val bytes = pcm16(*samples)

        val whole = Pcm16kConverter(48_000, 1, PcmEncoding.PCM16).convert(bytes)

        val chunkedConverter = Pcm16kConverter(48_000, 1, PcmEncoding.PCM16)
        val chunked = ArrayList<Float>()
        val step = 1600 * 2
        var offset = 0
        while (offset < bytes.size) {
            val size = minOf(step, bytes.size - offset)
            chunked += chunkedConverter.convert(bytes, offset, size).toList()
            offset += size
        }

        assertEquals(whole.size.toDouble(), chunked.size.toDouble(), 2.0)
        val head = minOf(whole.size, chunked.size, 200)
        for (i in 0 until head) {
            assertEquals("sample $i diverged", whole[i], chunked[i], 0.01f)
        }
    }

    @Test
    fun `float32 samples are decoded`() {
        val converter = Pcm16kConverter(16_000, 1, PcmEncoding.FLOAT32)
        val out = converter.convert(float32(0.25f, -0.75f, 1f))
        assertEquals(2, out.size)
        assertEquals(0.25f, out[0], 0.0001f)
        assertEquals(-0.75f, out[1], 0.0001f)
    }

    @Test
    fun `an incomplete trailing frame is carried to the next chunk`() {
        val converter = Pcm16kConverter(16_000, 2, PcmEncoding.PCM16)
        // Three and a half stereo frames; the half frame must not be decoded as audio.
        val bytes = pcm16(16384, 16384, 16384, 16384, 16384, 16384, 16384)
        val out = converter.convert(bytes)
        assertEquals(2, out.size)
        assertEquals(0.5f, out[0], 0.001f)
        assertEquals(0.5f, out[1], 0.001f)
    }

    @Test
    fun `8 bit pcm is centred on zero`() {
        val converter = Pcm16kConverter(16_000, 1, PcmEncoding.PCM8)
        val out = converter.convert(byteArrayOf(128.toByte(), 255.toByte(), 0))
        assertEquals(2, out.size)
        assertTrue(out[0] < 0.01f && out[0] > -0.01f)
        assertEquals(0.9921875f, out[1], 0.01f)
    }

    @Test
    fun `empty input yields no samples`() {
        val converter = Pcm16kConverter(16_000, 1, PcmEncoding.PCM16)
        assertEquals(0, converter.convert(ByteArray(0)).size)
        assertEquals(0, converter.convert(ByteArray(0), 0, 0).size)
    }

    @Test
    fun `android encodings map to the right pcm formats`() {
        assertEquals(PcmEncoding.PCM16, PcmEncoding.fromAndroidEncoding(2))
        assertEquals(PcmEncoding.PCM8, PcmEncoding.fromAndroidEncoding(3))
        assertEquals(PcmEncoding.FLOAT32, PcmEncoding.fromAndroidEncoding(4))
        assertEquals(PcmEncoding.PCM24, PcmEncoding.fromAndroidEncoding(21))
        assertEquals(PcmEncoding.PCM32, PcmEncoding.fromAndroidEncoding(22))
        assertEquals(PcmEncoding.PCM16, PcmEncoding.fromAndroidEncoding(99))
    }
}
