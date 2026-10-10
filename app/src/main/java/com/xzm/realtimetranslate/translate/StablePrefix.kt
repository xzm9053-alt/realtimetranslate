package com.xzm.realtimetranslate.translate

/**
 * LocalAgreement-2: decode the same growing audio buffer more than once and treat the
 * longest common prefix of two consecutive hypotheses as settled text.
 *
 * 为什么是"两次结果取公共前缀"，而不是直接显示最新一次：
 *  - 模型每次重解码都可能改口（尤其句尾），直接显示最新结果 = 字幕不停被改写；
 *  - 两次都同意的部分才算数，所以草稿只会**往外长**，不会往回缩。
 *
 * 至于为什么能用在 SenseVoice 这种非流式模型上：它不需要时间戳，只需要"同一段音频
 * 再跑一次"，所以本地模型不用换、不用下载、不用联网也能有"边说边出"的效果。
 * 参考 Macháček et al., *Turning Whisper into Real-Time Transcription System*
 * (IJCNLP-AACL 2023)。
 *
 * 非线程安全：调用方独占（这里是 ASR 的识别线程）。
 */
class StablePrefix {

    private var previous = ""
    private var committed = ""
    private var seeded = false

    /** 当前这句已经稳下来的部分。 */
    val text: String get() = committed

    /**
     * 送入一次针对当前缓冲的识别结果；稳下来的部分变长时返回它，否则返回 null。
     *
     * 第一次送入直接采信（"两句才敢说"会让头一秒什么都看不到），往后就要求两次一致。
     */
    fun accept(hypothesis: String): String? {
        val stable = if (seeded) longestCommonPrefix(previous, hypothesis) else hypothesis
        seeded = true
        previous = hypothesis
        // 只增不减：模型后来改口时，已经给用户看过的那截不能凭空消失，
        // 只是草稿停在那里，等两次结果重新对上再继续长。
        if (stable.length <= committed.length) return null
        committed = stable
        return committed
    }

    /** 新的一句开始了（VAD 切走了一段），之前稳下来的全部作废。 */
    fun reset() {
        previous = ""
        committed = ""
        seeded = false
    }

    companion object {
        fun longestCommonPrefix(a: String, b: String): String {
            val limit = minOf(a.length, b.length)
            var i = 0
            while (i < limit && a[i] == b[i]) i++
            return a.substring(0, i)
        }
    }
}
