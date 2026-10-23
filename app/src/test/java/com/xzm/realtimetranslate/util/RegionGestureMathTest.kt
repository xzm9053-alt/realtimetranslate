package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionGestureMathTest {

    private val start = RegionBounds(left = 100, top = 200, right = 500, bottom = 400)

    @Test
    fun `drag resizes only the bottom right corner`() {
        val next = RegionGestureMath.resizeFromBottomRight(start, dx = 60, dy = 40, 1080, 1920)
        assertEquals(100, next.left)
        assertEquals(200, next.top)
        assertEquals(560, next.right)
        assertEquals(440, next.bottom)
    }

    @Test
    fun `resize never shrinks below the minimum region`() {
        val next = RegionGestureMath.resizeFromBottomRight(
            start,
            dx = -10_000,
            dy = -10_000,
            screenW = 1080,
            screenH = 1920,
        )
        assertEquals(start.left + RegionGestureMath.MIN_REGION_PX, next.right)
        assertEquals(start.top + RegionGestureMath.MIN_REGION_PX, next.bottom)
    }

    @Test
    fun `resize is clamped to the screen`() {
        val next = RegionGestureMath.resizeFromBottomRight(
            start,
            dx = 10_000,
            dy = 10_000,
            screenW = 1080,
            screenH = 1920,
        )
        assertEquals(1080, next.right)
        assertEquals(1920, next.bottom)
    }

    @Test
    fun `move keeps the size and follows the finger`() {
        val next = RegionGestureMath.moveClamped(start, dx = -30, dy = 50, 1080, 1920)
        assertEquals(70, next.left)
        assertEquals(250, next.top)
        assertEquals(start.width, next.width)
        assertEquals(start.height, next.height)
    }

    @Test
    fun `move is clamped so the region stays fully on screen`() {
        val next = RegionGestureMath.moveClamped(start, dx = 10_000, dy = 10_000, 1080, 1920)
        assertEquals(1080 - start.width, next.left)
        assertEquals(1920 - start.height, next.top)
        assertEquals(1080, next.right)
        assertEquals(1920, next.bottom)
    }

    @Test
    fun `a region larger than the screen is pinned to the top left`() {
        val huge = RegionBounds(0, 0, 2000, 3000)
        val next = RegionGestureMath.moveClamped(huge, dx = 100, dy = 100, 1080, 1920)
        assertEquals(0, next.left)
        assertEquals(0, next.top)
    }
}
