package com.xzm.realtimetranslate.video

/**
 * 把逐段识别结果整理成可以直接显示的字幕时间轴。
 *
 * 规则都在这里、且都是纯函数，因为它们决定了字幕"看起来对不对"，但很难在手
 * 机上复现：重叠、过短、重复、被片尾截断，每一项都用单测盯住。
 */
object SubtitleCueBuilder {

    /** 前一段没有句末标点、间隔又很短时，多半是 VAD 把一个句子切开了。 */
    const val MERGE_GAP_MS = 200L

    /** 合并后超过这个长度就不再合，否则一条字幕会在屏幕上挂太久。 */
    const val MERGE_MAX_DURATION_MS = 8_000L

    /** 再短的显示时间人眼来不及读完。 */
    const val MIN_CUE_MS = 800L

    /** 相邻字幕之间留的空隙，避免两条同屏。 */
    const val CUE_GAP_MS = 40L

    /** 同一句话在极短时间内重复出现（VAD 抖动）时只保留一次。 */
    const val DEDUP_WINDOW_MS = 1_500L

    private const val SENTENCE_ENDS = "。！？；.!?;…"

    fun build(raw: List<TimedText>, durationMs: Long): List<TimedText> {
        val merged = merge(raw.filter { it.source.isNotBlank() })
        val deduped = dedupe(merged)
        return retime(deduped, durationMs)
    }

    private fun merge(raw: List<TimedText>): List<TimedText> {
        val merged = ArrayList<TimedText>()
        for (cue in raw.sortedBy { it.startMs }) {
            val prev = merged.lastOrNull()
            if (prev != null && canMerge(prev, cue)) {
                merged[merged.size - 1] = prev.copy(
                    endMs = maxOf(prev.endMs, cue.endMs),
                    source = join(prev.source, cue.source),
                    translation = join(prev.translation, cue.translation),
                )
            } else {
                merged += cue
            }
        }
        return merged
    }

    private fun canMerge(prev: TimedText, next: TimedText): Boolean =
        next.startMs - prev.endMs <= MERGE_GAP_MS &&
            !endsSentence(prev.source) &&
            next.endMs - prev.startMs <= MERGE_MAX_DURATION_MS

    private fun endsSentence(text: String): Boolean {
        val last = text.trimEnd().lastOrNull() ?: return false
        return SENTENCE_ENDS.indexOf(last) >= 0
    }

    private fun join(a: String, b: String): String {
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        return "$a $b"
    }

    private fun dedupe(cues: List<TimedText>): List<TimedText> {
        val out = ArrayList<TimedText>(cues.size)
        for (cue in cues) {
            val prev = out.lastOrNull()
            val sameText = prev != null &&
                prev.source.trim() == cue.source.trim() &&
                cue.startMs - prev.startMs <= DEDUP_WINDOW_MS
            if (!sameText) out += cue
        }
        return out
    }

    private fun retime(cues: List<TimedText>, durationMs: Long): List<TimedText> {
        val out = ArrayList<TimedText>(cues.size)
        for (cue in cues) {
            var start = cue.startMs.coerceAtLeast(0L)
            val prev = out.lastOrNull()
            if (prev != null && start < prev.endMs + CUE_GAP_MS) {
                start = prev.endMs + CUE_GAP_MS
            }
            var end = maxOf(cue.endMs, start + MIN_CUE_MS)
            if (durationMs > 0) end = minOf(end, durationMs)
            if (end <= start) continue
            out += cue.copy(startMs = start, endMs = end)
        }
        return out
    }
}
