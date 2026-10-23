package com.xzm.realtimetranslate.util

import com.xzm.realtimetranslate.data.VideoSubtitleMode
import com.xzm.realtimetranslate.video.VideoCue
import org.junit.Assert.assertEquals
import org.junit.Test

class SrtFormatterTest {

    private fun cue(
        index: Int,
        start: Long,
        end: Long,
        source: String = "hello",
        translation: String = "你好",
    ) = VideoCue(index = index, startMs = start, endMs = end, source = source, translation = translation)

    @Test
    fun `timestamp uses srt field widths`() {
        assertEquals("00:00:00,000", SrtFormatter.timestamp(0))
        assertEquals("00:00:01,005", SrtFormatter.timestamp(1005))
        assertEquals("01:02:03,456", SrtFormatter.timestamp(3_723_456))
        assertEquals("00:00:00,000", SrtFormatter.timestamp(-50))
    }

    @Test
    fun `blocks are numbered from one with blank line separators`() {
        val text = SrtFormatter.format(
            listOf(
                cue(0, 0, 1000, translation = "第一句"),
                cue(1, 1200, 2400, translation = "第二句"),
            ),
        )
        assertEquals(
            "1\n00:00:00,000 --> 00:00:01,000\n第一句\n" +
                "\n" +
                "2\n00:00:01,200 --> 00:00:02,400\n第二句\n",
            text,
        )
    }

    @Test
    fun `blank translation falls back to the recognized text`() {
        val text = SrtFormatter.format(listOf(cue(0, 0, 1000, source = "raw text", translation = "  ")))
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nraw text\n", text)
    }

    @Test
    fun `carriage returns are normalized`() {
        val text = SrtFormatter.format(listOf(cue(0, 0, 1000, translation = "a\r\nb\rc")))
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\na\nb\nc\n", text)
    }

    @Test
    fun `a too short cue is stretched to the minimum duration`() {
        val text = SrtFormatter.format(listOf(cue(0, 5000, 5100)))
        assertEquals("1\n00:00:05,000 --> 00:00:05,800\n你好\n", text)
    }

    @Test
    fun `empty input produces empty output`() {
        assertEquals("", SrtFormatter.format(emptyList()))
    }

    @Test
    fun `both mode puts source on the first line and translation on the second`() {
        val text = SrtFormatter.format(
            listOf(cue(0, 0, 1000, source = "hello", translation = "你好")),
            VideoSubtitleMode.BOTH,
        )
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nhello\n你好\n", text)
    }

    @Test
    fun `source mode emits only the recognized text`() {
        val text = SrtFormatter.format(
            listOf(cue(0, 0, 1000, source = "hello", translation = "你好")),
            VideoSubtitleMode.SOURCE,
        )
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nhello\n", text)
    }

    @Test
    fun `both mode does not duplicate identical source and translation`() {
        // Recognize-only runs leave source == translation; emitting it twice would
        // show every line doubled in the burned-in video.
        val text = SrtFormatter.format(
            listOf(cue(0, 0, 1000, source = "hello", translation = "hello")),
            VideoSubtitleMode.BOTH,
        )
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nhello\n", text)
    }

    @Test
    fun `both mode falls back to whichever side is present`() {
        val text = SrtFormatter.format(
            listOf(
                cue(0, 0, 1000, source = "hello", translation = "  "),
                cue(1, 1000, 2000, source = "", translation = "你好"),
            ),
            VideoSubtitleMode.BOTH,
        )
        assertEquals(
            "1\n00:00:00,000 --> 00:00:01,000\nhello\n" +
                "\n" +
                "2\n00:00:01,000 --> 00:00:02,000\n你好\n",
            text,
        )
    }
}
