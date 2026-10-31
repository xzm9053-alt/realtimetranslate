package com.xzm.realtimetranslate.video

import com.xzm.realtimetranslate.data.VideoAsrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 云端识别的两处纯逻辑：语言码归一化与 Float → PCM16 编码。
 * 这两处错了都不会报错，只会静默地识别成错的语言或出来一堆噪音。
 */
class CloudBatchRecognizerTest {

    @Test
    fun `language codes drop the region and lowercase`() {
        assertEquals("zh", CloudBatchRecognizer.isoLanguageOf("zh-Hans"))
        assertEquals("zh", CloudBatchRecognizer.isoLanguageOf("zh-Hant"))
        assertEquals("pt", CloudBatchRecognizer.isoLanguageOf("pt-BR"))
        assertEquals("en", CloudBatchRecognizer.isoLanguageOf("EN"))
    }

    @Test
    fun `auto language sends no hint`() {
        // Whisper 自带 LID；传 "auto" 过去反而是个它不认的代码。
        assertNull(CloudBatchRecognizer.isoLanguageOf("auto"))
        assertNull(CloudBatchRecognizer.isoLanguageOf("  "))
        assertNull(CloudBatchRecognizer.isoLanguageOf(""))
    }

    @Test
    fun `floats map to full-scale pcm16`() {
        val bytes = CloudBatchRecognizer.toPcm16le(floatArrayOf(0f, 1f, -1f, 0.5f))
        assertEquals(8, bytes.size)
        assertEquals(0, readShort(bytes, 0).toInt())
        assertEquals(Short.MAX_VALUE.toInt(), readShort(bytes, 2).toInt())
        assertEquals((-Short.MAX_VALUE.toInt()), readShort(bytes, 4).toInt())
        assertEquals((Short.MAX_VALUE / 2), readShort(bytes, 6).toInt())
    }

    @Test
    fun `out of range samples clamp instead of wrapping`() {
        // 不夹紧的话 1.5f 会溢出成负数，听感上就是爆音。
        val bytes = CloudBatchRecognizer.toPcm16le(floatArrayOf(2f, -2f))
        assertEquals(Short.MAX_VALUE.toInt(), readShort(bytes, 0).toInt())
        assertEquals((-Short.MAX_VALUE.toInt()), readShort(bytes, 2).toInt())
    }

    @Test
    fun `local engine whitelist matches what sensevoice knows`() {
        assertEquals(
            listOf("auto", "zh-Hans", "zh-Hant", "en", "ja", "ko"),
            VideoAsrEngine.LOCAL_SOURCE_CODES,
        )
        assertTrue(VideoAsrEngine.LOCAL.needsNetwork.not())
        assertTrue(VideoAsrEngine.CLOUD.needsNetwork)
    }

    private fun readShort(bytes: ByteArray, offset: Int): Short {
        val lo = bytes[offset].toInt() and 0xff
        val hi = bytes[offset + 1].toInt() shl 8
        return (hi or lo).toShort()
    }
}
