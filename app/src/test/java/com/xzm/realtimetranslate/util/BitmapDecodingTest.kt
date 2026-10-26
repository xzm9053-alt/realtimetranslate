package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapDecodingTest {

    @Test
    fun `already small enough decodes at full size`() {
        assertEquals(1, BitmapDecoding.sampleSizeFor(1000, 800, 2000))
        assertEquals(1, BitmapDecoding.sampleSizeFor(2000, 1200, 2000))
    }

    @Test
    fun `large photo is halved`() {
        assertEquals(2, BitmapDecoding.sampleSizeFor(4000, 3000, 2000))
    }

    @Test
    fun `very large photo keeps halving`() {
        assertEquals(4, BitmapDecoding.sampleSizeFor(8000, 6000, 2000))
    }

    @Test
    fun `longest side decides, not the shorter one`() {
        // 3000 wide x 200 tall: the height alone would not need sampling.
        assertEquals(2, BitmapDecoding.sampleSizeFor(3000, 200, 2000))
        // A very long strip still stops at the first factor that fits.
        assertEquals(4, BitmapDecoding.sampleSizeFor(5000, 100, 2000))
    }

    @Test
    fun `stops at the first factor that fits`() {
        // 3999/2 = 1999, which already fits, so 2 is the answer and the loop must not
        // overshoot to 4.
        assertEquals(2, BitmapDecoding.sampleSizeFor(3999, 100, 2000))
    }

    @Test
    fun `degenerate input falls back to full size`() {
        assertEquals(1, BitmapDecoding.sampleSizeFor(0, 0, 2000))
        assertEquals(1, BitmapDecoding.sampleSizeFor(-5, 100, 2000))
        assertEquals(1, BitmapDecoding.sampleSizeFor(4000, 3000, 0))
    }
}
