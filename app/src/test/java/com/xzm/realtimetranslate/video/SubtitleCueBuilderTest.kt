package com.xzm.realtimetranslate.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCueBuilderTest {

    private fun text(start: Long, end: Long, source: String, translation: String = "T:$source") =
        TimedText(startMs = start, endMs = end, source = source, translation = translation)

    @Test
    fun `segments without terminal punctuation are merged`() {
        val out = SubtitleCueBuilder.build(
            listOf(
                text(0, 1000, "hello there"),
                text(1100, 2000, "how are you"),
            ),
            durationMs = 5000,
        )
        assertEquals(1, out.size)
        assertEquals("hello there how are you", out[0].source)
        assertEquals("T:hello there T:how are you", out[0].translation)
        assertEquals(0L, out[0].startMs)
        assertEquals(2000L, out[0].endMs)
    }

    @Test
    fun `a finished sentence is never merged forward`() {
        val out = SubtitleCueBuilder.build(
            listOf(
                text(0, 1000, "hello。"),
                text(1050, 2000, "next line"),
            ),
            durationMs = 5000,
        )
        assertEquals(2, out.size)
        assertEquals("hello。", out[0].source)
    }

    @Test
    fun `merging stops before the maximum duration`() {
        val out = SubtitleCueBuilder.build(
            listOf(
                text(0, 6000, "long start"),
                text(6050, 9000, "tail part"),
            ),
            durationMs = 20_000,
        )
        assertEquals(2, out.size)
    }

    @Test
    fun `identical text repeated within the dedup window is dropped`() {
        val out = SubtitleCueBuilder.build(
            listOf(
                text(0, 1000, "same。"),
                text(1200, 2200, "same。"),
            ),
            durationMs = 5000,
        )
        assertEquals(1, out.size)
        assertEquals(0L, out[0].startMs)
    }

    @Test
    fun `identical text outside the dedup window is kept`() {
        val out = SubtitleCueBuilder.build(
            listOf(
                text(0, 1000, "same。"),
                text(3000, 4000, "same。"),
            ),
            durationMs = 5000,
        )
        assertEquals(2, out.size)
    }

    @Test
    fun `short cues are stretched and pushed apart`() {
        val out = SubtitleCueBuilder.build(
            listOf(
                text(0, 200, "a。"),
                text(210, 300, "b。"),
            ),
            durationMs = 10_000,
        )
        assertEquals(2, out.size)
        assertEquals(0L, out[0].startMs)
        assertEquals(800L, out[0].endMs)
        assertEquals(840L, out[1].startMs)
        assertTrue(out[1].endMs >= out[1].startMs + SubtitleCueBuilder.MIN_CUE_MS)
    }

    @Test
    fun `cues are clamped to the video duration`() {
        val out = SubtitleCueBuilder.build(
            listOf(text(0, 9000, "spilling over")),
            durationMs = 3000,
        )
        assertEquals(1, out.size)
        assertEquals(3000L, out[0].endMs)
    }

    @Test
    fun `cues starting after the video ends are dropped`() {
        val out = SubtitleCueBuilder.build(
            listOf(
                text(0, 1000, "inside。"),
                text(5000, 6000, "outside。"),
            ),
            durationMs = 3000,
        )
        assertEquals(1, out.size)
        assertEquals("inside。", out[0].source)
    }

    @Test
    fun `blank recognitions are dropped`() {
        val out = SubtitleCueBuilder.build(
            listOf(text(0, 1000, "  "), text(1200, 2000, "real。")),
            durationMs = 5000,
        )
        assertEquals(1, out.size)
        assertEquals("real。", out[0].source)
    }

    @Test
    fun `unsorted input is sorted by start time`() {
        val out = SubtitleCueBuilder.build(
            listOf(text(3000, 4000, "second。"), text(0, 1000, "first。")),
            durationMs = 5000,
        )
        assertEquals(listOf("first。", "second。"), out.map { it.source })
    }
}
