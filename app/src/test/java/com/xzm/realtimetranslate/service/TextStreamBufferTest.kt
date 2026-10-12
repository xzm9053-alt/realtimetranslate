package com.xzm.realtimetranslate.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStreamBufferTest {

    @Test
    fun testEpochIsolation() {
        val buffer = TextStreamBuffer()
        val epoch1 = buffer.startNewEpoch()
        assertEquals(1L, epoch1)

        // 正常写入当前世代
        assertTrue(buffer.appendFinal(epoch1, "第一句话"))
        assertEquals("第一句话", buffer.getDisplayText())

        // 切换到世代 2
        val epoch2 = buffer.startNewEpoch()
        assertEquals(2L, epoch2)

        // 来自旧世代 1 的延迟乱序包应被拒绝
        assertFalse(buffer.appendFinal(epoch1, "旧数据"))
        assertFalse(buffer.updateDraft(epoch1, "旧草稿"))

        // 正常写入世代 2
        assertTrue(buffer.appendFinal(epoch2, "第二句话"))
        assertEquals("第一句话第二句话", buffer.getDisplayText())
    }

    @Test
    fun testCumulativeRewrite() {
        val buffer = TextStreamBuffer()
        val epoch = buffer.startNewEpoch()

        // 模拟 LLM 累计重写流：每个 chunk 都是前缀累计
        buffer.appendFinal(epoch, "你")
        buffer.appendFinal(epoch, "你好")
        buffer.appendFinal(epoch, "你好世界")

        // 应当直接替换当前句，而不是堆叠成 "你你好你好世界"
        assertEquals("你好世界", buffer.getDisplayText())
    }

    @Test
    fun testDraftDoesNotPolluteExport() {
        val buffer = TextStreamBuffer()
        val epoch = buffer.startNewEpoch()

        buffer.updateDraft(epoch, "正在说的草稿...")
        assertEquals("正在说的草稿...", buffer.getDisplayText())
        // 导出中不应有未决草稿
        assertEquals("", buffer.getFullExportText())

        // 正式文字到来，草稿被覆盖
        buffer.appendFinal(epoch, "定稿文字。")
        assertEquals("定稿文字。", buffer.getDisplayText())
        assertEquals("定稿文字。", buffer.getFullExportText())
    }

    @Test
    fun testHistoryScrollTruncation() {
        val buffer = TextStreamBuffer(maxHistoryChars = 20)
        val epoch1 = buffer.startNewEpoch()
        buffer.appendFinal(epoch1, "这是一段超过二十个字符的很长很长的历史测试文字")
        buffer.startNewEpoch() // 触发 finalizeCurrent

        // 历史长度应当被裁切至不超过 20 个字符
        assertTrue(buffer.getDisplayText().length <= 20)
    }

    @Test
    fun testSmartSpacingInDisplayText() {
        val buffer = TextStreamBuffer()
        val epoch1 = buffer.startNewEpoch()
        buffer.appendFinal(epoch1, "Hello")
        val epoch2 = buffer.startNewEpoch()
        buffer.appendFinal(epoch2, "world")

        // 历史中的 Hello 与当前句的 world 应当以空格智能连接
        assertEquals("Hello world", buffer.getDisplayText())

        // 加上草稿
        buffer.updateDraft(epoch2, "today")
        assertEquals("Hello world today", buffer.getDisplayText())
    }
}
