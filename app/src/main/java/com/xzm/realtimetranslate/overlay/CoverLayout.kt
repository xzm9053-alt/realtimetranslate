package com.xzm.realtimetranslate.overlay

import com.xzm.realtimetranslate.ocr.PixelRect
import kotlin.math.floor
import kotlin.math.max

/** What the cover layer is allowed to know about user settings. */
data class CoverStyle(
    val bilingual: Boolean = false,
    val fontScale: Float = 1.0f,
    val blockStyle: String = "AUTO",
)

/** One translated block: where it goes and what to paint behind it. */
data class CoverTarget(
    val rect: PixelRect,
    val text: String,
    val background: Int,
    /** Original text, only painted when bilingual mode is on. */
    val original: String? = null,
    val createdAtMs: Long = 0L,
    /**
     * Height of one original line, in the same pixels as [rect]. The translation is
     * sized from this so it matches the text it replaces; 0 falls back to [rect].
     */
    val sourceLineHeight: Float = 0f,
)

data class FittedText(val lines: List<String>, val fontSize: Float, val truncated: Boolean)

/**
 * Fits translated text into a box. Pure logic so the "never spills onto the next
 * line" rule can be tested without an Android runtime; [CoverView] supplies the
 * real Paint measurements.
 */
object CoverLayout {

    private const val ELLIPSIS = "…"

    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val source = text.trim()
        if (source.isEmpty()) return emptyList()

        val lines = ArrayList<String>()
        val line = StringBuilder()
        for (token in tokenize(source)) {
            if (measure(line.toString() + token) <= maxWidth) {
                line.append(token)
                continue
            }
            if (line.isNotEmpty()) {
                lines.add(line.toString())
                line.setLength(0)
            }
            val bare = token.trimStart()
            if (measure(bare) <= maxWidth) {
                line.append(bare)
                continue
            }
            for (ch in bare) {
                val piece = line.toString() + ch
                if (line.isNotEmpty() && measure(piece) > maxWidth) {
                    lines.add(line.toString())
                    line.setLength(0)
                }
                line.append(ch)
            }
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        return lines
    }

    fun fit(
        text: String,
        maxWidth: Float,
        maxHeight: Float,
        maxFontSize: Float,
        minFontSize: Float,
        measure: (String, Float) -> Float,
        lineHeight: (Float) -> Float,
    ): FittedText {
        var size = maxFontSize
        while (size >= minFontSize) {
            val lines = wrap(text, maxWidth) { measure(it, size) }
            if (lines.size * lineHeight(size) <= maxHeight) {
                return FittedText(lines, size, truncated = false)
            }
            size -= 1f
        }

        val lines = wrap(text, maxWidth) { measure(it, minFontSize) }
        val maxLines = max(1, floor(maxHeight / lineHeight(minFontSize)).toInt())
        val kept = lines.take(maxLines).toMutableList()
        if (kept.isEmpty()) return FittedText(emptyList(), minFontSize, truncated = true)
        var last = kept.last()
        while (last.isNotEmpty() && measure(last + ELLIPSIS, minFontSize) > maxWidth) {
            last = last.dropLast(1)
        }
        kept[kept.size - 1] = last.trimEnd() + ELLIPSIS
        return FittedText(kept, minFontSize, truncated = true)
    }

    /**
     * Splits [text] into wrap units. Each unit carries the whitespace that preceded
     * it so widths stay truthful; the first unit has none.
     */
    private fun tokenize(text: String): List<String> {
        val tokens = ArrayList<String>()
        val word = StringBuilder()
        var pendingSpace = false

        fun prefix() = if (pendingSpace && tokens.isNotEmpty()) " " else ""

        fun flushWord() {
            if (word.isNotEmpty()) {
                tokens.add(prefix() + word)
                word.setLength(0)
                pendingSpace = false
            }
        }

        for (ch in text) {
            when {
                ch.isWhitespace() -> {
                    flushWord()
                    pendingSpace = true
                }
                isBreakableChar(ch) -> {
                    flushWord()
                    tokens.add(prefix() + ch)
                    pendingSpace = false
                }
                else -> word.append(ch)
            }
        }
        flushWord()
        return tokens
    }

    private fun isBreakableChar(ch: Char): Boolean {
        val cp = ch.code
        return cp in 0x2E80..0x9FFF ||   // CJK radicals, kana, CJK unified
            cp in 0xAC00..0xD7AF ||      // Hangul
            cp in 0xF900..0xFAFF ||      // CJK compatibility ideographs
            cp in 0xFF00..0xFFEF         // fullwidth forms
    }
}
