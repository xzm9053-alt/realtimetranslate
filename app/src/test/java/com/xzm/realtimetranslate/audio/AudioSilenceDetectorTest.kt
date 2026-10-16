package com.xzm.realtimetranslate.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSilenceDetectorTest {

    @Test
    fun testSilenceDetectionTriggerAndRecovery() {
        var alertCount = 0
        var restoredCount = 0

        val detector = AudioSilenceDetector(
            audioSourceTag = "TestAudio",
            silenceThresholdRms = 15.0f,
            warningDurationMs = 100L, // 单元测试缩短阈值
            onSilenceAlert = { _, _ -> alertCount++ },
            onSoundRestored = { _ -> restoredCount++ },
        )

        val silentBuffer = ShortArray(1600) { 0 }
        // 初始无告警
        detector.processPcm(silentBuffer, silentBuffer.size)
        assertEquals(0.0, detector.currentRms, 0.001)
        assertFalse(detector.isAlertTriggered)

        // 等待超过 100ms
        Thread.sleep(120L)
        detector.processPcm(silentBuffer, silentBuffer.size)
        assertTrue(detector.isAlertTriggered)
        assertEquals(1, alertCount)

        // 持续静音不重复触发
        detector.processPcm(silentBuffer, silentBuffer.size)
        assertEquals(1, alertCount)

        // 声音恢复（高能量正弦波或噪声）
        val loudBuffer = ShortArray(1600) { (it % 500 * 10).toShort() }
        detector.processPcm(loudBuffer, loudBuffer.size)
        assertTrue(detector.currentRms > 15.0)
        assertFalse(detector.isAlertTriggered)
        assertEquals(1, restoredCount)
    }

    @Test
    fun testPcmBytesCalculation() {
        val detector = AudioSilenceDetector(audioSourceTag = "ByteTest")
        val zeroBytes = ByteArray(3200) { 0 }
        detector.processPcmBytes(zeroBytes, zeroBytes.size)
        assertEquals(0.0, detector.currentRms, 0.001)

        // 构造两个振幅 1000 的 16-bit LE 采样: 1000 = 0x03E8 -> low=0xE8, high=0x03
        val sampleBytes = byteArrayOf(0xE8.toByte(), 0x03.toByte(), 0xE8.toByte(), 0x03.toByte())
        detector.processPcmBytes(sampleBytes, sampleBytes.size)
        assertEquals(1000.0, detector.currentRms, 0.01)
    }
}
