package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CjkTextSpacersTest {

    @Test
    fun testIsCjkChar() {
        assertTrue(CjkTextSpacers.isCjkChar('你'))
        assertTrue(CjkTextSpacers.isCjkChar('好'))
        assertTrue(CjkTextSpacers.isCjkChar('あ')) // 日文平假名
        assertTrue(CjkTextSpacers.isCjkChar('ア')) // 日文片假名
        assertTrue(CjkTextSpacers.isCjkChar('한')) // 韩文谚文
        assertFalse(CjkTextSpacers.isCjkChar('A'))
        assertFalse(CjkTextSpacers.isCjkChar('1'))
        assertFalse(CjkTextSpacers.isCjkChar(' '))
    }

    @Test
    fun testIsCjkPunctuation() {
        assertTrue(CjkTextSpacers.isCjkPunctuation('，'))
        assertTrue(CjkTextSpacers.isCjkPunctuation('。'))
        assertTrue(CjkTextSpacers.isCjkPunctuation('、'))
        assertTrue(CjkTextSpacers.isCjkPunctuation('！'))
        assertTrue(CjkTextSpacers.isCjkPunctuation('“'))
        assertFalse(CjkTextSpacers.isCjkPunctuation(','))
        assertFalse(CjkTextSpacers.isCjkPunctuation('.'))
    }

    @Test
    fun testShouldInsertSpaceRules() {
        // 汉字 + 汉字 -> 不插入空格
        assertFalse(CjkTextSpacers.shouldInsertSpace('今', '天'))
        assertFalse(CjkTextSpacers.shouldInsertSpace('天', '气'))

        // 汉字 + 假名 -> 不插入空格
        assertFalse(CjkTextSpacers.shouldInsertSpace('私', 'は'))

        // 汉字 + 全角标点 -> 不插入空格
        assertFalse(CjkTextSpacers.shouldInsertSpace('好', '，'))
        assertFalse(CjkTextSpacers.shouldInsertSpace('，', '世'))

        // 英文 + 英文 -> 插入空格
        assertTrue(CjkTextSpacers.shouldInsertSpace('o', 'w')) // "hello" + "world"
        assertTrue(CjkTextSpacers.shouldInsertSpace('a', 'b'))

        // 中文 + 英文 -> 插入空格优化排版
        assertTrue(CjkTextSpacers.shouldInsertSpace('用', 'i'))
        assertTrue(CjkTextSpacers.shouldInsertSpace('e', '端'))

        // 含有已有空白 -> 不插入多余空格
        assertFalse(CjkTextSpacers.shouldInsertSpace(' ', 'a'))
        assertFalse(CjkTextSpacers.shouldInsertSpace('a', ' '))
    }

    @Test
    fun testAppendWithSmartSpacing() {
        val sb = StringBuilder("今天")
        CjkTextSpacers.appendWithSmartSpacing(sb, "天气")
        CjkTextSpacers.appendWithSmartSpacing(sb, "很好")
        assertEquals("今天天气很好", sb.toString())

        val englishSb = StringBuilder("Hello")
        CjkTextSpacers.appendWithSmartSpacing(englishSb, "World")
        assertEquals("Hello World", englishSb.toString())

        val mixedSb = StringBuilder("使用")
        CjkTextSpacers.appendWithSmartSpacing(mixedSb, "iPhone")
        CjkTextSpacers.appendWithSmartSpacing(mixedSb, "手机")
        assertEquals("使用 iPhone 手机", mixedSb.toString())
    }
}
