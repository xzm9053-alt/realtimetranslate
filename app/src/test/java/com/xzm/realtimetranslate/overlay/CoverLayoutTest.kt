package com.xzm.realtimetranslate.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLayoutTest {

    private val measure: (String) -> Float = { it.length * 10f }

    private val measureAt: (String, Float) -> Float = { s, size -> s.length * size * 0.5f }

    private val lineHeight: (Float) -> Float = { it * 1.2f }

    @Test
    fun `short text stays on one line`() {
        assertEquals(listOf("hello"), CoverLayout.wrap("hello", 100f, measure))
    }

    @Test
    fun `latin text breaks at spaces`() {
        assertEquals(
            listOf("hello", "world foo"),
            CoverLayout.wrap("hello world foo", 100f, measure),
        )
    }

    @Test
    fun `cjk text breaks between characters`() {
        assertEquals(
            listOf("你好世界", "再见"),
            CoverLayout.wrap("你好世界再见", 40f, measure),
        )
    }

    @Test
    fun `a single too-wide token is hard-broken`() {
        val lines = CoverLayout.wrap("abcdefghij", 30f, measure)
        assertTrue(lines.all { it.length <= 3 })
    }

    @Test
    fun `empty text yields no lines`() {
        assertTrue(CoverLayout.wrap("", 100f, measure).isEmpty())
    }

    @Test
    fun `short text keeps the maximum font size`() {
        val fitted = CoverLayout.fit("hi", 100f, 40f, 28f, 8f, measureAt, lineHeight)
        assertEquals(28f, fitted.fontSize, 0.01f)
        assertFalse(fitted.truncated)
        assertEquals(listOf("hi"), fitted.lines)
    }

    @Test
    fun `long text shrinks until the box height is respected`() {
        val fitted = CoverLayout.fit("a".repeat(30), 50f, 36f, 28f, 8f, measureAt, lineHeight)
        assertTrue(fitted.fontSize < 28f)
        assertFalse(fitted.truncated)
        assertTrue(fitted.lines.size * lineHeight(fitted.fontSize) <= 36f + 0.01f)
    }

    @Test
    fun `text too long even at the minimum size is truncated`() {
        val fitted = CoverLayout.fit("a".repeat(400), 20f, 12f, 28f, 8f, measureAt, lineHeight)
        assertEquals(8f, fitted.fontSize, 0.01f)
        assertTrue(fitted.truncated)
        assertTrue(fitted.lines.size * lineHeight(fitted.fontSize) <= 12f + 0.01f)
        assertTrue(fitted.lines.last().endsWith("…"))
    }
}
