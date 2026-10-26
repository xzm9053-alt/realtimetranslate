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

    /**
     * 判断两段跨帧 OCR 文本在语义上是否等价，用于过滤屏幕不动时的微小 OCR 抖动。
     * - 忽略空白与换行差异
     * - 忽略末尾标点（.,!?~…）与符号漂移
     * - 针对较长句子，容忍不超过 2 字符的 OCR 识别字形微小跳变（>=88% 相似度）
     */
    fun isEquivalent(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.isBlank() || b.isBlank()) return false
        val cleanA = a.trim()
        val cleanB = b.trim()
        if (cleanA == cleanB) return true

        // 提取字符核心（字母与汉字数字，忽略标点符号和空格的波动）
        val coreA = cleanA.filter { it.isLetterOrDigit() }.lowercase()
        val coreB = cleanB.filter { it.isLetterOrDigit() }.lowercase()
        if (coreA == coreB) return true
        if (coreA.isEmpty() || coreB.isEmpty()) return false

        // 边界字符 1~2 字符噪点（例如开头/末尾少识别了一个小标点或单字符）
        val diffLen = kotlin.math.abs(coreA.length - coreB.length)
        if (diffLen <= 2 && (coreA.startsWith(coreB) || coreB.startsWith(coreA) || coreA.endsWith(coreB) || coreB.endsWith(coreA))) {
            return true
        }

        // 长文本编辑距离容忍（例如 20+ 字符的长句中由于子像素渲染偶尔有一个字符置信度微变）
        val maxLen = maxOf(coreA.length, coreB.length)
        if (maxLen >= 8) {
            val dist = levenshteinDistance(coreA, coreB, maxDist = 3)
            if (dist <= 2 && (1f - dist.toFloat() / maxLen) >= 0.88f) {
                return true
            }
        }
        return false
    }

    fun levenshteinDistance(s1: String, s2: String, maxDist: Int = Int.MAX_VALUE): Int {
        val len1 = s1.length
        val len2 = s2.length
        if (kotlin.math.abs(len1 - len2) > maxDist) return maxDist + 1
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)
        for (i in 1..len1) {
            curr[0] = i
            var minInRow = curr[0]
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,       // deletion
                    curr[j - 1] + 1,   // insertion
                    prev[j - 1] + cost, // substitution
                )
                minInRow = minOf(minInRow, curr[j])
            }
            if (minInRow > maxDist) return maxDist + 1
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }
}
