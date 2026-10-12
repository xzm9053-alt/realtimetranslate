package com.xzm.realtimetranslate.ocr

import com.xzm.realtimetranslate.util.CjkTextSpacers

/**
 * OCR 文本智能清洗与排版器：
 * 1. 清理 ML Kit 等 OCR 引擎在中文/日文/韩文中无意插入的多余空格（例如 "今 天 天 气 好" -> "今天天气好"）。
 * 2. 智能换行解开（Smart Line Unwrapping）：识别是屏幕UI狭窄导致的断行还是真正的句子终结。
 *    若行末无终结标点（。！？!?…等），按语言规则平滑连接（CJK 无缝连接，西文以空格连接）；
 *    若行末为终结标点，则保留换行作为句/段落分界。
 */
object OcrTextProcessor {

    /**
     * 判断字符是否属于句子终结标点。
     */
    fun isSentenceEndingPunctuation(c: Char): Boolean {
        return c == '。' || c == '！' || c == '？' ||
            c == '.' || c == '!' || c == '?' ||
            c == '…' || c == '；' || c == ';'
    }

    /**
     * 清理 CJK 字符之间误插的半角空格，保留西文单词之间与中西混排边界的合法空格。
     */
    fun cleanCjkInternalSpacing(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == ' ' && sb.isNotEmpty() && i + 1 < text.length) {
                val prev = sb.last()
                val next = text[i + 1]
                // 若空格两侧均为 CJK 字符或 CJK 标点，剥离此空格
                if ((CjkTextSpacers.isCjkChar(prev) || CjkTextSpacers.isCjkPunctuation(prev)) &&
                    (CjkTextSpacers.isCjkChar(next) || CjkTextSpacers.isCjkPunctuation(next))
                ) {
                    i++
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /**
     * 全流程清洗与平滑换行连接。
     */
    fun cleanAndJoinLines(rawText: String): String {
        if (rawText.isBlank()) return ""
        val rawLines = rawText.lines()
            .map { cleanCjkInternalSpacing(it.trim()) }
            .filter { it.isNotEmpty() }
        if (rawLines.isEmpty()) return ""

        val sb = StringBuilder()
        for (line in rawLines) {
            if (sb.isEmpty()) {
                sb.append(line)
            } else {
                val lastChar = sb.last()
                // 剔除末尾可能包裹的括号、引号等外壳符号，判断是否有句子终结标点
                val trimmedEnd = sb.trimEnd('"', '\'', '”', '’', ')', '）', '」', '』', '》', '】')
                val isEnding = trimmedEnd.isNotEmpty() && isSentenceEndingPunctuation(trimmedEnd.last())

                if (isEnding) {
                    // 保留换行作为句子或段落分界
                    sb.append('\n').append(line)
                } else {
                    // 平滑合并换行
                    val firstCharOfNext = line.first()
                    if (CjkTextSpacers.shouldInsertSpace(lastChar, firstCharOfNext)) {
                        sb.append(' ').append(line)
                    } else {
                        sb.append(line)
                    }
                }
            }
        }
        return sb.toString()
    }
}
