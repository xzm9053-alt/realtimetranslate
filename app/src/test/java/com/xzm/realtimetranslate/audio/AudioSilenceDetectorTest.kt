package com.xzm.realtimetranslate.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSilenceDetectorTest {

    /** 可推进的时钟，避免用 Thread.sleep 把单测拖慢。 */
    private class FakeClock(var now: Long = 1_000L) : () -> Long {
        override fun invoke(): Long = now
        fun advance(ms: Long) { now += ms }
    }

    private fun silent(count: Int = 1600) = ShortArray(count) { 0 }

    /** 底噪：非零但低于阈值。 */
    private fun quiet(count: Int = 1600) = ShortArray(count) { if (it % 2 == 0) 10 else -10 }

    private fun loud(count: Int = 1600) = ShortArray(count) { if (it % 2 == 0) 2000 else -2000 }

    @Test
    fun testDigitalSilenceReportedAsDigitalNotQuiet() {
        val clock = FakeClock()
        val states = mutableListOf<CaptureState>()
        var alerts = 0
        val detector = AudioSilenceDetector(
            audioSourceTag = "TestMedia",
            silenceThresholdRms = 15.0f,
            warningDurationMs = 10_000L,
            zeroDurationMs = 3_000L,
            onSilenceAlert = { _, _ -> alerts++ },
            onStateChanged = { state, _ -> states.add(state) },
            timeProvider = clock,
        )

        detector.processPcm(silent(), 1600)
        clock.advance(3_100L)
        detector.processPcm(silent(), 1600)

        assertEquals(CaptureState.DIGITAL_SILENCE, detector.currentState)
        assertEquals(1, alerts)
        assertTrue(states.contains(CaptureState.DIGITAL_SILENCE))
    }

    @Test
    fun testQuietIsNotReportedAsError() {
        val clock = FakeClock()
        var alerts = 0
        val detector = AudioSilenceDetector(
            audioSourceTag = "TestMedia",
            silenceThresholdRms = 15.0f,
            warningDurationMs = 10_000L,
            zeroDurationMs = 3_000L,
            onSilenceAlert = { _, _ -> alerts++ },
            timeProvider = clock,
        )

        detector.processPcm(quiet(), 1600)
        clock.advance(11_000L)
        detector.processPcm(quiet(), 1600)

        assertEquals(CaptureState.QUIET, detector.currentState)
        assertEquals("quiet must never raise the digital-silence alert", 0, alerts)
    }

    @Test
    fun testDigitalSilenceBelowThresholdDurationDoesNotAlert() {
        val clock = FakeClock()
        var alerts = 0
        val detector = AudioSilenceDetector(
            audioSourceTag = "TestMedia",
            zeroDurationMs = 3_000L,
            onSilenceAlert = { _, _ -> alerts++ },
            timeProvider = clock,
        )

        detector.processPcm(silent(), 1600)
        clock.advance(2_000L)
        detector.processPcm(silent(), 1600)

        assertFalse(detector.isAlertTriggered)
        assertEquals(0, alerts)
    }

    @Test
    fun testSoundRestoredFiresOnceAndResets() {
        val clock = FakeClock()
        var restored = 0
        val detector = AudioSilenceDetector(
            audioSourceTag = "TestMedia",
            zeroDurationMs = 3_000L,
            onSoundRestored = { restored++ },
            timeProvider = clock,
        )

        detector.processPcm(silent(), 1600)
        clock.advance(3_100L)
        detector.processPcm(silent(), 1600)
        assertTrue(detector.isAlertTriggered)

        detector.processPcm(loud(), 1600)
        detector.processPcm(loud(), 1600)

        assertEquals(1, restored)
        assertFalse(detector.isAlertTriggered)
    }

    @Test
    fun testAlertCanRetriggerAfterRecovery() {
        val clock = FakeClock()
        var alerts = 0
        val detector = AudioSilenceDetector(
            audioSourceTag = "TestMedia",
            zeroDurationMs = 3_000L,
            onSilenceAlert = { _, _ -> alerts++ },
            timeProvider = clock,
        )

        detector.processPcm(silent(), 1600)
        clock.advance(3_100L)
        detector.processPcm(silent(), 1600)
        assertEquals(1, alerts)

        detector.processPcm(loud(), 1600)
        clock.advance(1_000L)

        // 出过声之后需要更长的零才算真断（8s）。
        detector.processPcm(silent(), 1600)
        clock.advance(8_100L)
        detector.processPcm(silent(), 1600)
        assertEquals("latch must reset so a second silence alerts again", 2, alerts)
    }

    @Test
    fun testShortGapAfterSoundDoesNotAlert() {
        val clock = FakeClock()
        var alerts = 0
        val detector = AudioSilenceDetector(
            audioSourceTag = "TestMedia",
            zeroDurationMs = 3_000L,
            onSilenceAlert = { _, _ -> alerts++ },
            timeProvider = clock,
        )

        detector.processPcm(loud(), 1600)
        detector.processPcm(silent(), 1600)
        clock.advance(4_000L)
        detector.processPcm(silent(), 1600)

        assertEquals("a 4s playback gap must not be reported as digital silence", 0, alerts)
        assertFalse(detector.isAlertTriggered)
    }

    @Test
    fun testRmsExactlyAtThresholdIsNotSilent() {
        val clock = FakeClock()
        val detector = AudioSilenceDetector(
            audioSourceTag = "TestMedia",
            silenceThresholdRms = 15.0f,
            warningDurationMs = 10_000L,
            timeProvider = clock,
        )
        // RMS 恰好 15.0：每个采样都是 15 或 -15。
        val atThreshold = ShortArray(1600) { if (it % 2 == 0) 15 else -15 }
        detector.processPcm(atThreshold, 1600)
        clock.advance(11_000L)
        detector.processPcm(atThreshold, 1600)

        assertEquals(CaptureState.FLOWING, detector.currentState)
        assertFalse(detector.isAlertTriggered)
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
