package com.xzm.realtimetranslate.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The commit policy is the whole feature: everything the user sees as a growing draft
 * comes from these few lines, so the interesting cases are the ones that would make the
 * subtitle flicker or freeze — a model that changes its mind, and a prefix that would
 * have to shrink.
 *
 * A note on the expected values: settling always lags the newest hypothesis by one
 * decode. `h1, h2` can only confirm the part of `h1` that `h2` repeats, so the draft the
 * user reads is one tick behind what the model last said. That lag is the price of never
 * showing text the model is about to take back.
 */
class StablePrefixTest {

    @Test
    fun `longest common prefix stops at the first disagreement`() {
        assertEquals("我想", StablePrefix.longestCommonPrefix("我想要", "我想去"))
        assertEquals("", StablePrefix.longestCommonPrefix("abc", "xyz"))
        assertEquals("abc", StablePrefix.longestCommonPrefix("abc", "abcd"))
        assertEquals("abc", StablePrefix.longestCommonPrefix("abcd", "abc"))
        assertEquals("", StablePrefix.longestCommonPrefix("", "abc"))
        assertEquals("", StablePrefix.longestCommonPrefix("abc", ""))
        assertEquals("完全相同", StablePrefix.longestCommonPrefix("完全相同", "完全相同"))
    }

    @Test
    fun `the first hypothesis is taken at face value`() {
        // Waiting for two agreeing hypotheses would delay the very first draft by a
        // whole tick, and the first tick is where a draft is most obviously useful.
        // The trade is one visible correction shortly after, nothing more.
        val prefix = StablePrefix()
        assertEquals("你好", prefix.accept("你好"))
    }

    @Test
    fun `a hypothesis confirms only what the previous one already said`() {
        val prefix = StablePrefix()
        assertEquals("今天天气", prefix.accept("今天天气"))
        // "不错" is new here, but nothing has repeated it yet.
        assertNull(prefix.accept("今天天气不错"))
        assertEquals("今天天气不错", prefix.accept("今天天气不错啊"))
        assertEquals("今天天气不错啊", prefix.accept("今天天气不错啊，"))
        assertEquals("今天天气不错啊", prefix.text)
    }

    @Test
    fun `an unchanged hypothesis reports nothing new`() {
        val prefix = StablePrefix()
        assertEquals("你好", prefix.accept("你好"))
        // Identical decode of the same buffer: there is nothing to redraw, and
        // re-emitting would push a duplicate frame at the overlay every tick.
        assertNull(prefix.accept("你好"))
    }

    @Test
    fun `a model that changes its mind never deletes text already shown`() {
        val prefix = StablePrefix()
        prefix.accept("我想要")
        assertNull(prefix.accept("我想要去"))

        // The re-decode now hears something else entirely. What two passes already
        // agreed on stands — the draft stops growing instead of shrinking under the
        // reader's eyes, and the final sentence replaces the line anyway.
        assertNull(prefix.accept("我想留在这里"))
        assertEquals("我想要", prefix.text)
    }

    @Test
    fun `growth resumes once the hypotheses agree again`() {
        val prefix = StablePrefix()
        prefix.accept("我想要")
        assertNull(prefix.accept("我想留在这里"))       // 与已显示的部分不一致 → 冻结
        assertEquals("我想留在这里", prefix.accept("我想留在这里了")) // 模型稳住 → 继续长
        assertEquals("我想留在这里了", prefix.accept("我想留在这里了呢"))
    }

    @Test
    fun `reset forgets everything and starts over`() {
        val prefix = StablePrefix()
        prefix.accept("第一句")
        prefix.accept("第一句话")

        prefix.reset()
        assertEquals("", prefix.text)
        // A fresh utterance gets the same "trust the first hypothesis" treatment.
        assertEquals("第二句", prefix.accept("第二句"))
    }

    @Test
    fun `english text grows as the spaces settle`() {
        val prefix = StablePrefix()
        assertEquals("hello wor", prefix.accept("hello wor"))
        assertNull(prefix.accept("hello world"))
        assertEquals("hello world", prefix.accept("hello world how"))
    }
}
