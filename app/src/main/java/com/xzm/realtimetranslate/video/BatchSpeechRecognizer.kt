package com.xzm.realtimetranslate.video

/**
 * 视频字幕用的「整段进、一句出」识别器。
 *
 * 与实时链路的 [com.xzm.realtimetranslate.translate.SpeechRecognizer] 不同：
 * 视频的 VAD 已经切好段，每次调用拿到的是完整的一段音频，不需要流式喂 PCM、
 * 也不需要处理部分结果。
 */
interface BatchSpeechRecognizer : AutoCloseable {

    /** 识别一段音频；返回 null 表示这段是静音/噪声，调用方应丢弃。 */
    suspend fun recognize(samples: FloatArray): String?
}
