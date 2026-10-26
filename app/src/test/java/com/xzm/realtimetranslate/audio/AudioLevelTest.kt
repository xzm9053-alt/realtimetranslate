package com.xzm.realtimetranslate.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLevelTest {

    @Test
    fun testSilenceNormalizesToZero() {
        assertEquals(0f, AudioLevel.normalize(0.0), 0.0001f)
    }

    @Test
    fun testTopOfScaleNormalizesToOne() {
        // -10 dBFS 就是条子顶部；满量程（0 dBFS）同样封顶。
        assertEquals(1f, AudioLevel.normalize(10362.0), 0.01f)
        assertEquals(1f, AudioLevel.normalize(32768.0), 0.0001f)
    }

    @Test
    fun testTypicalSpeechOccupiesUpperHalf() {
        // 实测人声峰值约 -20 dBFS（rms≈3277），应落在条子上半截而不是贴底。
        val level = AudioLevel.normalize(3277.0)
        assertTrue("typical speech should reach the upper half, got $level", level > 0.6f)
    }

    @Test
    fun testMonotonic() {
        val samples = listOf(0.0, 1.0, 10.0, 100.0, 1000.0, 10000.0, 32768.0)
        var previous = -1f
        for (rms in samples) {
            val level = AudioLevel.normalize(rms)
            assertTrue("level for rms=$rms should not decrease", level >= previous)
            previous = level
        }
    }

    @Test
    fun testLogarithmicNotLinear() {
        // 线性刻度下 100/32768 ≈ 0.003，几乎贴底。对数刻度下应明显抬起。
        val level = AudioLevel.normalize(100.0)
        assertTrue("log scale should lift this well above the linear 0.003, got $level", level > 0.15f)
    }

    @Test
    fun testBelowFloorClampsToZero() {
        assertEquals(0f, AudioLevel.normalize(1.0), 0.0001f)
    }
}
