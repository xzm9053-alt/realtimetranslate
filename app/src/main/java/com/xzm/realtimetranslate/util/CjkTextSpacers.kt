package com.xzm.realtimetranslate.util

/**
 * 智能 CJK (中日韩) 文本连接与排版工具。
 * 解决常规以空格分隔西文单词逻辑在中文等无空格语言中导致生硬夹带空格（如“今天 我 去 超市”）的问题。
 */
object CjkTextSpacers {

    /**
     * 判断字符是否属于 CJK 表意文字或日韩字母假名
     */
    fun isCjkChar(c: Char): Boolean {
        val code = c.code
        return when {
            // CJK 统一表意文字基本区块及扩展 A~G
            code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0x20000..0x2A6DF -> true
            // 日文平假名、片假名、汉字注音
            code in 0x3040..0x309F ||
            code in 0x30A0..0x30FF ||
            code in 0x31F0..0x31FF -> true
            // 韩文谚文音节及字母
            code in 0xAC00..0xD7AF ||
            code in 0x1100..0x11FF ||
            code in 0x3130..0x318F -> true
            // CJK 兼容表意文字
            code in 0xF900..0xFAFF -> true
            else -> false
        }
    }

    /**
     * 判断字符是否属于 CJK 全角符号、全角标点
     */
    fun isCjkPunctuation(c: Char): Boolean {
        val code = c.code
        return when {
            // CJK 符号和标点 (3000-303F: 、。〈〉《》「」『』【】〔〕等)
            code in 0x3000..0x303F -> true
            // 全角 ASCII 变体及全角标点 (FF01-FF0F: ！＂＃＄％＆＇（）＊＋，－．／ 等)
            code in 0xFF01..0xFF5E -> true
            // 常见全角标点补充（引号、破折号、省略号、间隔号）
            code in 0x2018..0x201F ||
            code == '·'.code || code == '—'.code || code == '…'.code || code == '～'.code -> true
            else -> false
        }
    }

    /**
     * 判断在 [prev] 和 [next] 两个字符之间是否需要插入半角空格 ' '。
     * 规则：
     * 1. 任意一侧已有空白符或换行 -> 不插入。
     * 2. 两侧均为 CJK 字符或 CJK 标点 -> 绝不插入空格。
     * 3. 前后一侧为 CJK 标点 -> 绝不插入空格。
     * 4. 均为 Latin 字母/数字 -> 插入空格分隔单词。
     * 5. Latin 与 CJK 之间：默认根据中英文混排惯例插入单个空格（如 "iOS 客户端"）。
     */
    fun shouldInsertSpace(prev: Char, next: Char): Boolean {
        if (prev.isWhitespace() || next.isWhitespace()) return false

        val prevCjk = isCjkChar(prev)
        val nextCjk = isCjkChar(next)
        val prevPunct = isCjkPunctuation(prev)
        val nextPunct = isCjkPunctuation(next)

        // 包含全角 CJK 标点符号的任何接合处，都不追加空格
        if (prevPunct || nextPunct) return false

        // 汉字与汉字、汉字与假名/谚文相接，绝不插入空格
        if (prevCjk && nextCjk) return false

        // 西文/数字与西文/数字相接，必须插入空格分隔单词
        val prevLatinOrDigit = isLatinOrDigit(prev)
        val nextLatinOrDigit = isLatinOrDigit(next)
        if (prevLatinOrDigit && nextLatinOrDigit) return true

        // 汉字与西文/数字之间：标准排版插入空格隔开
        if ((prevCjk && nextLatinOrDigit) || (prevLatinOrDigit && nextCjk)) {
            return true
        }

        // 兜底西文标点处理：若前一个不是标点，后一个紧接西文，保持默认
        return false
    }

    private fun isLatinOrDigit(c: Char): Boolean {
        return (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9')
    }

    /**
     * 将 [chunk] 智能追加到 [buffer] 中，根据末尾与起始字符动态决定是否插入空格。
     */
    fun appendWithSmartSpacing(buffer: StringBuilder, chunk: String) {
        if (chunk.isEmpty()) return
        if (buffer.isNotEmpty()) {
            val lastChar = buffer.last()
            val firstChar = chunk.first()
            if (shouldInsertSpace(lastChar, firstChar)) {
                buffer.append(' ')
            }
        }
        buffer.append(chunk)
    }
}
