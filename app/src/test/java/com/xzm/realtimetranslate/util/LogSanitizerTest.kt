package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Everything the app writes to disk goes through [LogSanitizer], and that file is meant
 * to be sent to a stranger over WeChat. So these pin the redaction rules themselves,
 * plus the property that ordinary text survives untouched — a sanitizer that mangles
 * normal logs is worse than none, because nobody will notice the mangling.
 */
class LogSanitizerTest {

    @Test
    fun `credential query parameters are redacted`() {
        assertEquals("…?key=***", LogSanitizer.sanitize("…?key=abc123"))
        assertEquals("…&api_key=***", LogSanitizer.sanitize("…&api_key=abc123"))
        assertEquals("…&APIKEY=***", LogSanitizer.sanitize("…&APIKEY=abc123"))
        assertEquals("…&access_token=***", LogSanitizer.sanitize("…&access_token=abc.def-ghi"))
        assertEquals("…&password=***", LogSanitizer.sanitize("…&password=hunter2"))
    }

    @Test
    fun `only the value is redacted, the rest of the url survives`() {
        assertEquals(
            "wss://host/ws?key=***&model=x&lang=zh",
            LogSanitizer.sanitize("wss://host/ws?key=SECRET&model=x&lang=zh"),
        )
    }

    @Test
    fun `bearer tokens are redacted`() {
        assertEquals(
            "Authorization: Bearer ***",
            LogSanitizer.sanitize("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc.def"),
        )
        assertEquals("bearer ***", LogSanitizer.sanitize("bearer abc123"))
    }

    @Test
    fun `vendor key shapes are redacted`() {
        assertEquals("AIza***", LogSanitizer.sanitize("AIza" + "A".repeat(35)))
        assertEquals("sk-***", LogSanitizer.sanitize("sk-" + "a".repeat(32)))
        assertEquals("hf_***", LogSanitizer.sanitize("hf_" + "a".repeat(30)))
    }

    @Test
    fun `a long base64 blob is elided`() {
        val blob = "A".repeat(300)
        assertEquals("[base64 omitted]", LogSanitizer.sanitize(blob))
    }

    @Test
    fun `ordinary text is left alone`() {
        val text = "TRANSPROF done#3 code=200 hdr=80 ttf=410 out=26 reason=0 — 译文已上屏，时长 1.2s"
        assertEquals(text, LogSanitizer.sanitize(text))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            LogSanitizer.sanitize("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"),
        )
    }

    @Test
    fun `several rules can hit the same line`() {
        val out = LogSanitizer.sanitize(
            "GET https://h/v1?key=SECRET Authorization: Bearer tok123 model=sk-" + "b".repeat(24)
        )
        assertFalse(out.contains("SECRET"))
        assertFalse(out.contains("tok123"))
        assertFalse(out.contains("b".repeat(24)))
        assertTrue(out.contains("?key=***"))
        assertTrue(out.contains("Bearer ***"))
        assertTrue(out.contains("sk-***"))
    }

    @Test
    fun `sanitizing is idempotent`() {
        val once = LogSanitizer.sanitize("GET https://h/v1?key=SECRET Bearer tok123")
        assertEquals(once, LogSanitizer.sanitize(once))
    }

    @Test
    fun `over-long input is truncated`() {
        val out = LogSanitizer.sanitize("x".repeat(500), maxLen = 100)
        assertTrue(out.startsWith("x".repeat(100)))
        assertTrue(out.endsWith("(truncated)"))
    }

    @Test
    fun `redactUrl is the same choke point`() {
        assertEquals("?key=***", LogSanitizer.redactUrl("?key=zzz"))
    }
}
