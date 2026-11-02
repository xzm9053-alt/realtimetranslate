package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseTextNormalizerTest {

    @Test
    fun `toSimplified converts common traditional characters correctly`() {
        val input = "這是一個測試，包含電腦與軟體資訊，發送數據並確認。"
        val expected = "这是一个测试，包含电脑与软体资讯，发送数据并确认。"
        assertEquals(expected, ChineseTextNormalizer.toSimplified(input))
    }

    @Test
    fun `toSimplified converts anime and game style traditional characters`() {
        val input = "說話體力點數會不會恢復？氣體過後變成了敵人。"
        val expected = "说话体力点数会不会恢复？气体过后变成了敌人。"
        assertEquals(expected, ChineseTextNormalizer.toSimplified(input))
    }

    @Test
    fun `toSimplified preserves latin punctuation and already simplified text`() {
        val input = "LiveTranslate 2026: 已经是简体中文了 123! @#$"
        assertEquals(input, ChineseTextNormalizer.toSimplified(input))
    }

    @Test
    fun `isSimplifiedTarget correctly identifies simplified Chinese targets`() {
        assertTrue(ChineseTextNormalizer.isSimplifiedTarget("zh-Hans"))
        assertTrue(ChineseTextNormalizer.isSimplifiedTarget("zh-CN"))
        assertTrue(ChineseTextNormalizer.isSimplifiedTarget("zh"))
        assertTrue(ChineseTextNormalizer.isSimplifiedTarget("Simplified Chinese (简体中文)"))
        assertFalse(ChineseTextNormalizer.isSimplifiedTarget("zh-Hant"))
        assertFalse(ChineseTextNormalizer.isSimplifiedTarget("en"))
        assertFalse(ChineseTextNormalizer.isSimplifiedTarget("ja"))
    }
}
