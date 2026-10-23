package com.xzm.realtimetranslate.util

/**
 * 把长文本切成适合单次翻译请求的大小。
 *
 * 优先在句末标点或换行处断开，实在找不到才硬切；块与块之间的顺序原样保留。
 */
object TextChunker {

    const val DEFAULT_MAX_CHARS = 3000

    private const val SENTENCE_ENDS = "。！？；.!?;\n"

    fun split(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxChars) return listOf(trimmed)

        val chunks = ArrayList<String>()
        var start = 0
        while (start < trimmed.length) {
            var end = minOf(start + maxChars, trimmed.length)
            if (end < trimmed.length) {
                val window = trimmed.substring(start, end)
                val cut = window.indexOfLast { it in SENTENCE_ENDS }
                // 太靠前的标点会把块切得过碎，不如直接按上限切。
                if (cut >= maxChars / 3) end = start + cut + 1
            }
            val chunk = trimmed.substring(start, end).trim()
            if (chunk.isNotEmpty()) chunks += chunk
            start = end
            while (start < trimmed.length && trimmed[start].isWhitespace()) start++
        }
        return chunks
    }
}
