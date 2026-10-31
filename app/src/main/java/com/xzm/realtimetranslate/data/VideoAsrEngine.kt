package com.xzm.realtimetranslate.data

/**
 * 视频字幕的语音识别引擎。
 *
 * 与声音同传的 [AsrEngineType] 分开：视频是整段文件、没有实时延迟压力，
 * 所以只需要「本地 / 云端」两种，不引入流式协议的选择。
 */
enum class VideoAsrEngine {

    /** 本地 SenseVoice：完全离线、免 Key，但只认中英日韩粤五种语言。 */
    LOCAL,

    /** 云端 Whisper（OpenAI 兼容 /audio/transcriptions）：99 种语言，需要网络与 Key。 */
    CLOUD,
    ;

    val needsNetwork: Boolean get() = this == CLOUD

    companion object {
        /**
         * 本地 SenseVoice 认得的源语言。切回本地时若当前选的是这之外的语种，
         * 必须退回 auto，否则下拉显示「自动」而状态里还留着西语之类的值，
         * 识别会静默出错。
         */
        val LOCAL_SOURCE_CODES: List<String> =
            listOf("auto", "zh-Hans", "zh-Hant", "en", "ja", "ko")

        fun fromStorage(name: String?): VideoAsrEngine =
            entries.firstOrNull { it.name == name } ?: LOCAL
    }
}
