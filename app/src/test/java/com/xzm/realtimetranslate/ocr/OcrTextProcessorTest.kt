package com.xzm.realtimetranslate.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextProcessorTest {

    @Test
    fun testCjkSpacingCleaned() {
        val raw = "今 天 天 气 真 好"
        val result = OcrTextProcessor.cleanAndJoinLines(raw)
        assertEquals("今天天气真好", result)
    }

    @Test
    fun testCjkLineUnwrapping() {
        // Line without punctuation should merge seamlessly in CJK
        val raw = "为了完成这个任务\n我们需要团结合作"
        val result = OcrTextProcessor.cleanAndJoinLines(raw)
        assertEquals("为了完成这个任务我们需要团结合作", result)
    }

    @Test
    fun testCjkLineKeptWhenPunctuationEnds() {
        // Line with sentence end punctuation should keep newline
        val raw = "这是第一句话。\n这是第二句话！"
        val result = OcrTextProcessor.cleanAndJoinLines(raw)
        assertEquals("这是第一句话。\n这是第二句话！", result)
    }

    @Test
    fun testLatinLineUnwrappingWithSpace() {
        // English lines without punctuation should merge with a single space
        val raw = "The quick brown fox\njumps over the lazy dog"
        val result = OcrTextProcessor.cleanAndJoinLines(raw)
        assertEquals("The quick brown fox jumps over the lazy dog", result)
    }

    @Test
    fun testLatinLineKeptWhenPunctuationEnds() {
        val raw = "Hello world.\nHow are you today?"
        val result = OcrTextProcessor.cleanAndJoinLines(raw)
        assertEquals("Hello world.\nHow are you today?", result)
    }

    @Test
    fun testMixedCjkAndLatinLineUnwrapping() {
        val raw = "我们在使用 Claude Code\n进行项目开发"
        val result = OcrTextProcessor.cleanAndJoinLines(raw)
        assertEquals("我们在使用 Claude Code 进行项目开发", result)
    }
}
