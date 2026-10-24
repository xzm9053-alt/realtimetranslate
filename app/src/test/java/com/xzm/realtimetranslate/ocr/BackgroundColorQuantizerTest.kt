package com.xzm.realtimetranslate.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundColorQuantizerTest {

    @Test
    fun `most frequent color wins`() {
        val white = 0xFFFFFFFF.toInt()
        val red = 0xFFFF0000.toInt()
        val pixels = IntArray(10) { if (it < 7) white else red }
        assertEquals(white, ColorQuantizer.dominant(pixels))
    }

    @Test
    fun `near identical shades quantize together`() {
        val a = 0xFFFFFFFF.toInt()
        val b = 0xFFFEFEFE.toInt()
        val pixels = intArrayOf(a, a, b, b)
        assertEquals(a, ColorQuantizer.dominant(pixels))
    }

    @Test
    fun `transparent pixels are ignored`() {
        val black = 0xFF000000.toInt()
        val pixels = intArrayOf(0x00000000, black, black)
        assertEquals(black, ColorQuantizer.dominant(pixels))
    }

    @Test
    fun `empty input falls back to white`() {
        assertEquals(0xFFFFFFFF.toInt(), ColorQuantizer.dominant(IntArray(0)))
    }
}
