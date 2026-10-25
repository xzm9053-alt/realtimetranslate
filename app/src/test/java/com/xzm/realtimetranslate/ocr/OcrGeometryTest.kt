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

    @Test
    fun `padded expands box by given delta in all directions`() {
        val rect = PixelRect(10, 20, 100, 200)
        assertEquals(PixelRect(5, 15, 105, 205), rect.padded(5))
    }

    @Test
    fun `overlapRatio measures intersection relative to smaller box`() {
        val a = PixelRect(0, 0, 100, 100) // area 10000
        val b = PixelRect(50, 0, 150, 100) // area 10000, overlap 50x100 = 5000 -> 0.5f
        assertEquals(0.5f, a.overlapRatio(b), 0.001f)

        val nonOverlapping = PixelRect(200, 200, 300, 300)
        assertEquals(0f, a.overlapRatio(nonOverlapping), 0.001f)

        val subset = PixelRect(10, 10, 60, 60) // area 2500, fully inside a -> 1.0f
        assertEquals(1.0f, a.overlapRatio(subset), 0.001f)
    }
}
