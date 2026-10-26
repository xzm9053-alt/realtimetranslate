package com.xzm.realtimetranslate.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPlannerTest {

    private fun page(vararg blocks: OcrBlock) = OcrPage(1080, 2400, blocks.toList())

    private fun block(vararg lines: Pair<String, PixelRect>) =
        OcrBlock(lines.map { OcrLine(it.first, it.second) })

    @Test
    fun `english blocks are planned with their union rect`() {
        val p = page(
            block(
                "Hello world" to PixelRect(10, 10, 300, 40),
                "this is a test" to PixelRect(10, 50, 320, 80),
            ),
        )
        val targets = TranslationPlanner.plan(p, "zh")
        assertEquals(1, targets.size)
        assertEquals("Hello world\nthis is a test", targets[0].text)
        assertEquals(PixelRect(10, 10, 320, 80), targets[0].rect)
    }

    @Test
    fun `identical text is translated once`() {
        val p = page(
            block("Settings" to PixelRect(0, 0, 100, 30)),
            block("Settings" to PixelRect(0, 100, 100, 130)),
        )
        assertEquals(1, TranslationPlanner.plan(p, "zh").size)
    }

    @Test
    fun `text already in the target language is skipped`() {
        val p = page(block("设置" to PixelRect(0, 0, 100, 30)))
        assertTrue(TranslationPlanner.plan(p, "zh").isEmpty())
    }

    @Test
    fun `english text is skipped when english is the target`() {
        val p = page(block("Open file" to PixelRect(0, 0, 200, 30)))
        assertTrue(TranslationPlanner.plan(p, "en").isEmpty())
    }

    @Test
    fun `single characters and symbols are skipped`() {
        val p = page(
            block(">" to PixelRect(0, 0, 20, 20)),
            block("OK" to PixelRect(0, 40, 60, 70)),
        )
        assertEquals(listOf("OK"), TranslationPlanner.plan(p, "zh").map { it.text })
    }

    @Test
    fun `already translated keys are not planned again`() {
        val p = page(block("Open file" to PixelRect(0, 0, 200, 30)))
        val first = TranslationPlanner.plan(p, "zh")
        assertTrue(TranslationPlanner.plan(p, "zh", setOf(first[0].key)).isEmpty())
    }

    @Test
    fun `normalization ignores whitespace differences`() {
        assertEquals(
            TranslationPlanner.normalize("  Hello   world "),
            TranslationPlanner.normalize("Hello world"),
        )
    }

    @Test
    fun `empty page yields no targets`() {
        assertTrue(TranslationPlanner.plan(OcrPage(1080, 2400, emptyList()), "zh").isEmpty())
    }

    @Test
    fun `comic mode sorts vertical Japanese lines right to left`() {
        // Right column: x 200..240, Left column: x 150..190
        val p = page(
            block(
                "左の行" to PixelRect(150, 50, 190, 250),
                "右の行" to PixelRect(200, 50, 240, 250),
            ),
        )
        val targets = TranslationPlanner.planComic(p, "zh")
        assertEquals(1, targets.size)
        // Right to Left: 右の行 should come before 左の行, and joined seamlessly
        assertEquals("右の行左の行", targets[0].text)
        assertEquals(PixelRect(150, 50, 240, 250), targets[0].rect)
    }

    @Test
    fun `comic mode merges adjacent vertical blocks into single speech bubble`() {
        // Two adjacent columns recognized by OCR as separate blocks in one bubble
        val p = page(
            block("何者だ" to PixelRect(300, 100, 340, 260)),
            block("お前…" to PixelRect(350, 100, 390, 260)),
        )
        val targets = TranslationPlanner.planComic(p, "zh")
        assertEquals(1, targets.size)
        assertEquals("お前…何者だ", targets[0].text)
        assertEquals(PixelRect(300, 100, 390, 260), targets[0].rect)
    }

    @Test
    fun `comic mode filters out huge sound effects without dialogue punctuation`() {
        val p = page(
            // Regular dialogue: font size ~37px per char, has punctuation
            block("逃げろ！" to PixelRect(100, 100, 140, 250)),
            // Giant SFX: font size 100px per char, Katakana without punctuation
            block("ドォン" to PixelRect(500, 500, 700, 800)),
        )
        val targets = TranslationPlanner.planComic(p, "zh")
        assertEquals(1, targets.size)
        assertEquals("逃げろ！", targets[0].text)
    }

    @Test
    fun `comic mode keeps short dialogue with punctuation`() {
        val p = page(
            block("だめ！" to PixelRect(100, 100, 140, 220)),
        )
        val targets = TranslationPlanner.planComic(p, "zh")
        assertEquals(1, targets.size)
        assertEquals("だめ！", targets[0].text)
    }

    @Test
    fun `comic mode seamlessly joins CJK lines without spaces`() {
        val p = page(
            block(
                "といいな" to PixelRect(150, 50, 190, 180),
                "明日は晴れる" to PixelRect(200, 50, 240, 200),
            ),
        )
        val targets = TranslationPlanner.planComic(p, "zh")
        assertEquals(1, targets.size)
        assertEquals("明日は晴れるといいな", targets[0].text)
    }
}
