package com.xzm.realtimetranslate.ocr

/** A translated-once unit: [key] is the de-duplication identity, [rect] the block's union box. */
data class TextTarget(val key: String, val text: String, val rect: PixelRect)

/**
 * Decides which blocks of a recognized page are worth sending to the translation
 * engine. Keeps the expensive path (network + user credits) away from text that is
 * already in the target language, noise glyphs, and repeat frames.
 */
object TranslationPlanner {

    fun plan(
        page: OcrPage,
        targetLang: String,
        alreadyTranslated: Set<String> = emptySet(),
    ): List<TextTarget> {
        val seen = HashSet<String>()
        val targets = ArrayList<TextTarget>(page.blocks.size)
        for (block in page.blocks) {
            val key = normalize(block.text)
            if (key.isEmpty()) continue
            if (key in alreadyTranslated) continue
            if (!shouldTranslate(key, targetLang)) continue
            if (!seen.add(key)) continue
            targets += TextTarget(key = key, text = block.text, rect = block.rect)
        }
        return targets
    }

    fun shouldTranslate(text: String, targetLang: String): Boolean {
        if (text.filterNot { it.isWhitespace() }.length < 2) return false
        return when {
            targetLang.startsWith("zh") -> !isAlreadyChinese(text)
            targetLang.startsWith("en") -> !isAlreadyLatin(text)
            else -> true
        }
    }

    fun normalize(text: String): String = WHITESPACE.replace(text, " ").trim()

    private val WHITESPACE = Regex("\\s+")

    /** Mostly non-ASCII, no kana/hangul → it is already Chinese, translating again would garble it. */
    private fun isAlreadyChinese(text: String): Boolean {
        if (text.any { it in '぀'..'ヿ' || it in '가'..'힣' }) return false
        if (text.count { it.isLetterOrDigit() } == 0) return false
        return text.count { it.code > 0x7F }.toFloat() / text.length > 0.6f
    }

    private fun isAlreadyLatin(text: String): Boolean {
        if (text.any { it in '一'..'鿿' || it in '぀'..'ヿ' || it in '가'..'힣' }) return false
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        val ascii = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        return ascii.toFloat() / letters > 0.9f
    }
}
