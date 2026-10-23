package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    @Test
    fun `blank input produces no chunks`() {
        assertTrue(TextChunker.split("").isEmpty())
        assertTrue(TextChunker.split("   \n\t ").isEmpty())
    }

    @Test
    fun `short text stays in one chunk`() {
        val chunks = TextChunker.split("hello world")
        assertEquals(listOf("hello world"), chunks)
    }

    @Test
    fun `text at exactly the limit is not split`() {
        val text = "a".repeat(20)
        assertEquals(listOf(text), TextChunker.split(text, maxChars = 20))
    }

    @Test
    fun `long text is cut at a sentence end`() {
        val sentence = "This is a sentence. "
        val text = sentence.repeat(20).trim()
        val chunks = TextChunker.split(text, maxChars = 100)
        assertTrue(chunks.size > 1)
        chunks.dropLast(1).forEach { chunk ->
            assertTrue("chunk should end on punctuation: $chunk", chunk.last() == '.')
            assertTrue(chunk.length <= 100)
        }
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun `text without punctuation is hard cut at the limit`() {
        val text = "x".repeat(250)
        val chunks = TextChunker.split(text, maxChars = 100)
        assertEquals(3, chunks.size)
        assertEquals(100, chunks[0].length)
        assertEquals(100, chunks[1].length)
        assertEquals(50, chunks[2].length)
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun `a punctuation mark too early in the window is ignored`() {
        val text = "Hi. " + "y".repeat(299)
        val chunks = TextChunker.split(text, maxChars = 100)
        assertEquals(100, chunks[0].length)
    }

    @Test
    fun `chinese sentence ends are respected`() {
        val text = "第一句话。" + "这是一段没有标点的文字".repeat(10) + "。结尾。"
        val chunks = TextChunker.split(text, maxChars = 50)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.length <= 50) }
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun `chunks never contain only whitespace`() {
        val text = "word ".repeat(400).trim()
        val chunks = TextChunker.split(text, maxChars = 30)
        chunks.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `illegal limit is rejected`() {
        try {
            TextChunker.split("abc", maxChars = 0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
