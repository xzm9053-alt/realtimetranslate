package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report is the only thing the author gets to see, so its layout is pinned rather
 * than trusted: the sections must all be present even when a section is empty (a missing
 * heading reads as "the app failed to include it"), and the content-log warning has to
 * be the first line, where a user pasting the file into a chat will actually see it.
 */
class DiagnosticReportTest {

    private fun input(
        lines: List<String> = emptyList(),
        crash: String? = null,
        content: List<String>? = null,
    ) = DiagnosticReport.Input(
        appName = "LiveTranslate",
        versionName = "0.1.0",
        versionCode = 7,
        packageName = "com.xzm.realtimetranslate",
        generatedAtMillis = 1_700_000_000_000L,
        device = "device: Xiaomi/Redmi K40 (alioth)",
        settings = "engine: DEEPSEEK\nmodel: deepseek-v4-flash",
        crash = crash,
        lines = lines,
        contentLines = content,
    )

    @Test
    fun `the report carries version, device, settings and the log tail`() {
        val report = DiagnosticReport.build(input(lines = listOf("2026-07-14 22:35:01.123 I Foo: hi")))

        assertTrue(report.contains("LiveTranslate v0.1.0(7)"))
        assertTrue(report.contains("com.xzm.realtimetranslate"))
        assertTrue(report.contains("device: Xiaomi/Redmi K40 (alioth)"))
        assertTrue(report.contains("model: deepseek-v4-flash"))
        assertTrue(report.contains("2026-07-14 22:35:01.123 I Foo: hi"))
    }

    @Test
    fun `every section heading is present even when its body is empty`() {
        val report = DiagnosticReport.build(input())

        for (heading in listOf("设备", "设置", "崩溃记录", "关键指标", "日志尾部")) {
            assertTrue("missing section: $heading", report.contains(heading))
        }
    }

    @Test
    fun `a recorded crash is inlined and an absent one is explained`() {
        assertTrue(
            DiagnosticReport.build(input(crash = "java.lang.RuntimeException: boom"))
                .contains("java.lang.RuntimeException: boom")
        )
        // Silence would read as "the report forgot it"; the limits of the handler are
        // spelled out instead, since a native crash leaves no record here.
        assertTrue(DiagnosticReport.build(input()).contains("原生崩溃不会留下记录"))
    }

    @Test
    fun `extractProf keeps only the profile markers`() {
        val lines = listOf(
            "2026-07-14 22:35:01.123 I Foo: TRANSPROF done#1 code=200",
            "2026-07-14 22:35:01.223 I Foo: some heartbeat",
            "2026-07-14 22:35:01.323 I Foo: MODELPROBE model=x status=OK",
            "2026-07-14 22:35:01.423 I Foo: OCRMAP region=Rect(0, 0 - 10, 10)",
        )

        assertEquals(
            listOf(lines[0], lines[2], lines[3]),
            DiagnosticReport.extractProf(lines),
        )
    }

    @Test
    fun `a marker quoted inside a message is not mistaken for a profile line`() {
        // User text or an error string can contain the word; only a line whose message
        // *starts* with the marker is ours.
        val line = "2026-07-14 22:35:01.123 W Foo: user reported TRANSPROF looked wrong"
        assertEquals(emptyList<String>(), DiagnosticReport.extractProf(listOf(line)))
    }

    @Test
    fun `content lines put the warning on the very first line`() {
        val report = DiagnosticReport.build(input(content = listOf("Hello → 你好")))

        assertTrue(report.lines().first().startsWith("***"))
        assertTrue(report.contains("Hello → 你好"))
    }

    @Test
    fun `without content logging there is no warning and no content section`() {
        val report = DiagnosticReport.build(input(lines = listOf("2026-07-14 22:35:01.123 I Foo: hi")))

        assertTrue(!report.startsWith("***"))
        assertTrue(!report.contains("原文/译文 (最后"))
    }

    @Test
    fun `the file name is sortable and ends in txt`() {
        val name = DiagnosticReport.fileName(1_700_000_000_000L)

        assertTrue(name.startsWith("realtimetranslate-diagnostic-"))
        assertTrue(name.endsWith(".txt"))
        assertTrue(Regex("""realtimetranslate-diagnostic-\d{8}-\d{6}\.txt""").matches(name))
    }
}
