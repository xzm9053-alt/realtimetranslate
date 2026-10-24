package com.xzm.realtimetranslate.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGeometryTest {

    @Test
    fun `scale back divides coordinates by the preprocessing factor`() {
        val upscaled = PixelRect(100, 50, 300, 150)
        assertEquals(PixelRect(50, 25, 150, 75), upscaled.scaleToSource(2f, 2f))
    }

    @Test
    fun `offset adds a region origin`() {
        assertEquals(PixelRect(110, 250, 310, 350), PixelRect(10, 50, 210, 150).offset(100, 200))
    }

    @Test
    fun `union covers all lines of a block`() {
        val block = OcrBlock(
            listOf(
                OcrLine("第一行", PixelRect(10, 10, 110, 40)),
                OcrLine("第二行", PixelRect(20, 50, 100, 80)),
            ),
        )
        assertEquals(PixelRect(10, 10, 110, 80), block.rect)
        assertEquals("第一行\n第二行", block.text)
    }

    @Test
    fun `intersection detects a status-bar overlap`() {
        val statusBand = PixelRect(0, 0, 1080, 80)
        assertTrue(statusBand.intersects(PixelRect(100, 40, 200, 90)))
        assertFalse(statusBand.intersects(PixelRect(100, 100, 200, 140)))
    }

    @Test
    fun `degenerate boxes still produce non-negative sizes`() {
        val box = PixelRect(50, 50, 50, 50)
        assertEquals(0, box.width)
        assertEquals(0, box.height)
    }
}
