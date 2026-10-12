package com.xzm.realtimetranslate.service

import com.xzm.realtimetranslate.util.CjkTextSpacers

/**
 * 纯 Kotlin 流式双语文本缓冲状态机（零 Android SDK 依赖）。
 * 接管字幕文本的增量拼接、累计重写、草稿世代（Epoch）隔离、CJK 排版以及历史滚动截断。
 */
class TextStreamBuffer(
    val maxHistoryChars: Int = 800,
    val maxSentenceChars: Int = 500,
    val maxExportChars: Int = 200_000,
) {
    private val historyText = StringBuilder()
    private val currentSentence = StringBuilder()
    private var pendingDraft: String = ""
    private val fullExport = StringBuilder()

    /**
     * 当前活动世代号。每次分句切断或新句开始递增，用于废弃网络或解码乱序到达的陈旧草稿。
     */
    var currentEpoch: Long = 0L
        private set

    /**
     * 开启新句子世代（VAD 切段、新句定稿或流式重置）。
     * 将当前句固化至滚动历史中，清空临时草稿，返回新世代号。
     */
    @Synchronized
    fun startNewEpoch(): Long {
        finalizeCurrent()
        pendingDraft = ""
        currentEpoch++
        return currentEpoch
    }

    /**
     * 接收定稿文本更新。
     * 支持大模型流式累计全量重写（Cumulative Rewrite）或普通追加分段。
     * @param epoch 必须匹配当前活动世代，否则忽略以防乱序并发覆盖
     */
    @Synchronized
    fun appendFinal(epoch: Long, chunk: String): Boolean {
        if (epoch != currentEpoch) return false
        pendingDraft = "" // 正式文字到来，清除临时草稿

        if (chunk.isEmpty()) return true

        // 累计重写判定：若新内容以当前句为前缀且更长，直接整句替换，避免重复堆叠
        if (currentSentence.isNotEmpty() &&
            chunk.length >= currentSentence.length &&
            chunk.startsWith(currentSentence.toString())
        ) {
            val delta = chunk.substring(currentSentence.length)
            currentSentence.clear()
            currentSentence.append(chunk)
            // 导出全量文本只追加增量部分
            if (delta.isNotEmpty()) {
                CjkTextSpacers.appendWithSmartSpacing(fullExport, delta)
            }
        } else if (currentSentence.endsWith(chunk)) {
            // 完全重复文本，忽略
        } else {
            // 普通增量追加
            CjkTextSpacers.appendWithSmartSpacing(currentSentence, chunk)
            CjkTextSpacers.appendWithSmartSpacing(fullExport, chunk)
        }

        trimExportIfNeeded()
        if (currentSentence.length > maxSentenceChars) {
            finalizeCurrent()
        }
        return true
    }

    /**
     * 更新未决草稿（仅在尾部展示，不固化至历史或导出缓冲）。
     */
    @Synchronized
    fun updateDraft(epoch: Long, draft: String): Boolean {
        if (epoch != currentEpoch) return false
        pendingDraft = draft
        return true
    }

    /**
     * 清空当前草稿。
     */
    @Synchronized
    fun clearDraft(epoch: Long): Boolean {
        if (epoch != currentEpoch) return false
        pendingDraft = ""
        return true
    }

    /**
     * 获取用于当前 UI 渲染的展示文本（滚动历史 + 当前句 + 草稿）。
     */
    @Synchronized
    fun getDisplayText(): String {
        val sb = StringBuilder()
        if (historyText.isNotEmpty()) {
            sb.append(historyText)
        }
        if (currentSentence.isNotEmpty()) {
            CjkTextSpacers.appendWithSmartSpacing(sb, currentSentence.toString())
        }
        if (pendingDraft.isNotEmpty()) {
            CjkTextSpacers.appendWithSmartSpacing(sb, pendingDraft)
        }
        return sb.toString()
    }

    /**
     * 获取当前句（包含草稿）。
     */
    @Synchronized
    fun getCurrentWithDraft(): String {
        val sb = StringBuilder()
        if (currentSentence.isNotEmpty()) {
            sb.append(currentSentence)
        }
        if (pendingDraft.isNotEmpty()) {
            CjkTextSpacers.appendWithSmartSpacing(sb, pendingDraft)
        }
        return sb.toString()
    }

    /**
     * 获取当前完整历史导出内容。
     */
    @Synchronized
    fun getFullExportText(): String {
        return fullExport.toString()
    }

    /**
     * 重置所有缓冲。
     */
    @Synchronized
    fun reset() {
        historyText.clear()
        currentSentence.clear()
        pendingDraft = ""
        fullExport.clear()
        currentEpoch = 0L
    }

    private fun finalizeCurrent() {
        if (currentSentence.isEmpty()) return
        CjkTextSpacers.appendWithSmartSpacing(historyText, currentSentence.toString())
        currentSentence.clear()

        // 滚动历史防溢出截断
        if (historyText.length > maxHistoryChars) {
            historyText.delete(0, historyText.length - maxHistoryChars)
        }
    }

    private fun trimExportIfNeeded() {
        if (fullExport.length > maxExportChars) {
            fullExport.delete(0, fullExport.length - maxExportChars)
        }
    }
}
