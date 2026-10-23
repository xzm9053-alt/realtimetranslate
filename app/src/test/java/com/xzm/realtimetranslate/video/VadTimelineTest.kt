package com.xzm.realtimetranslate.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VadTimelineTest {

    @Test
    fun `a plausible reported start is trusted`() {
        val span = VadTimeline.resolve(
            reportedStart = 64_000,
            lengthSamples = 32_000,
            totalFedSamples = 128_000,
            lastStartSample = 0,
        )
        assertEquals(64_000L, span.startSample)
        assertEquals(96_000L, span.endSample)
        assertEquals(32_000L, span.lengthSamples)
    }

    @Test
    fun `a start beyond the fed audio falls back to total minus length`() {
        val span = VadTimeline.resolve(
            reportedStart = 999_999,
            lengthSamples = 16_000,
            totalFedSamples = 80_000,
            lastStartSample = 0,
        )
        assertEquals(64_000L, span.startSample)
        assertEquals(80_000L, span.endSample)
    }

    @Test
    fun `a start before the previous segment falls back`() {
        val span = VadTimeline.resolve(
            reportedStart = 1_000,
            lengthSamples = 8_000,
            totalFedSamples = 100_000,
            lastStartSample = 50_000,
        )
        assertEquals(92_000L, span.startSample)
    }

    @Test
    fun `negative reported starts are rejected`() {
        val span = VadTimeline.resolve(
            reportedStart = -4,
            lengthSamples = 4_000,
            totalFedSamples = 20_000,
            lastStartSample = 0,
        )
        assertEquals(16_000L, span.startSample)
    }

    @Test
    fun `no fed audio yields a zero length span`() {
        val span = VadTimeline.resolve(0, 0, 0, 0)
        assertEquals(0L, span.startSample)
        assertEquals(0L, span.endSample)
    }

    @Test
    fun `samples convert to milliseconds at 16k`() {
        assertEquals(0L, VadTimeline.toMillis(0))
        assertEquals(1_000L, VadTimeline.toMillis(16_000))
        assertEquals(1_500L, VadTimeline.toMillis(24_000))
    }
}
