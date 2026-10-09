package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The log viewer's "errors only" switch is the difference between a readable report and
 * a wall of OCR heartbeats, so the line format and the filter that reads it are pinned
 * together here. The stack-frame case is the one that matters: a crash trace whose
 * frames get filtered out is worse than no filter at all.
 */
class LogLineTest {

    @Test
    fun `a header line parses into time, level, tag and message`() {
        val entry = LogLine.parse(
            "2026-07-14 22:35:01.123 E OpenAiCompatChatEngine: TRANSPROF fail#3 code=401"
        )
        assertEquals('E', entry.level)
        assertEquals("OpenAiCompatChatEngine", entry.tag)
        assertEquals("TRANSPROF fail#3 code=401", entry.message)
    }

    @Test
    fun `a message may contain colons`() {
        val entry = LogLine.parse("2026-07-14 22:35:01.123 I Foo: a: b: c")
        assertEquals("Foo", entry.tag)
        assertEquals("a: b: c", entry.message)
    }

    @Test
    fun `a stack frame is a continuation, not an entry`() {
        val entry = LogLine.parse("\tat com.xzm.realtimetranslate.Foo.bar(Foo.kt:12)")
        assertNull(entry.level)
        assertNull(entry.tag)
    }

    @Test
    fun `filterErrors keeps warnings and errors with their stack frames`() {
        val lines = listOf(
            "2026-07-14 22:35:01.123 I ScreenTextSessionService: TRANSPROF ocrloopidle rounds=40",
            "2026-07-14 22:35:02.123 E OpenAiCompatChatEngine: TRANSPROF fail#7 err=IOException",
            "\tat com.xzm.realtimetranslate.translate.OpenAiCompatChatEngine.translate(OpenAiCompatChatEngine.kt:141)",
            "\t... 12 more",
            "2026-07-14 22:35:03.123 D ScreenTextCapturer: acquireLatestImage ok",
            "2026-07-14 22:35:04.123 W ScreenTextCapturer: copyRegion failed region=Rect(0, 0 - 10, 10)",
        )

        assertEquals(
            listOf(
                lines[1],
                lines[2],
                lines[3],
                lines[5],
            ),
            LogLine.filterErrors(lines),
        )
    }

    @Test
    fun `a continuation before any header is not kept`() {
        // A window that opens mid-stack must not resurrect frames of an entry whose
        // header the reader never saw — they would read as belonging to the next error.
        val lines = listOf(
            "\tat orphan",
            "2026-07-14 22:35:01.123 E Foo: boom",
            "\tat real.frame(Foo.kt:1)",
        )
        assertEquals(listOf(lines[1], lines[2]), LogLine.filterErrors(lines))
    }

    @Test
    fun `filterErrors on an empty list is empty`() {
        assertEquals(emptyList<String>(), LogLine.filterErrors(emptyList()))
    }
}
