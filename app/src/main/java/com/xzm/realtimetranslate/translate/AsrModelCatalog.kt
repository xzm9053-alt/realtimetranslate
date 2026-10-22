package com.xzm.realtimetranslate.translate

/**
 * WebSocket protocol family a model speaks.
 *
 * RUN_TASK is the DashScope `run-task` duplex protocol shared by Fun-ASR,
 * Paraformer and Qwen-Audio-ASR-Streaming.
 * OPENAI_REALTIME is the OpenAI-Realtime-compatible protocol used by Qwen3-ASR.
 * Qwen3-ASR is not offered yet: it needs a second recognizer implementation.
 */
enum class AsrProtocol {
    RUN_TASK,
    OPENAI_REALTIME,
}

/**
 * Metadata for selectable ASR models in Settings.
 */
data class AsrModelPreset(
    val id: String,
    val name: String,
    val description: String,
    val recommended: Boolean = false,
    /** Model family, used to group the picker into a two-level menu. */
    val group: String = "",
    val protocol: AsrProtocol = AsrProtocol.RUN_TASK,
)

object AsrModelCatalog {

    const val GROUP_FUN_ASR = "Fun-ASR"
    const val GROUP_QWEN_AUDIO_STREAMING = "Qwen-Audio 流式"
    const val GROUP_PARAFORMER = "Paraformer"

    /**
     * 阿里云百炼（DashScope）实时流式语音识别模型清单。
     *
     * 只收录官方文档中仍在售、且支持多语种的实时模型：
     *  - 已下线的 paraformer-realtime-v1 / -dialect-v1 / conformer-* 不再列出；
     *  - 仅支持中文的 8k 电话模型（paraformer-realtime-8k-v2、
     *    fun-asr-flash-8k-realtime*）不列出；
     *  - 录音文件转写类（fun-asr、paraformer-v2、*-filetrans）不是流式，不列出；
     *  - `*-asr-flash-message` 是短语音的同步 HTTP 接口，同样不走 run-task
     *    WebSocket，放进来只会「连上就失败」。
     */
    val ALIYUN_REALTIME_MODELS: List<AsrModelPreset> = listOf(
        AsrModelPreset(
            id = "fun-asr-realtime",
            name = "Fun-ASR 实时（稳定版）",
            description = "官方主力。中文含粤语吴语闽南语等方言，另支持英日韩越泰印尼马来菲印地阿拉伯法德西葡俄意荷瑞典丹芬挪希腊波兰捷克匈牙利罗马尼亚保加利亚克罗地亚斯洛伐克语",
            recommended = true,
            group = GROUP_FUN_ASR,
        ),
        AsrModelPreset(
            id = "fun-asr-realtime-2026-02-28",
            name = "Fun-ASR 实时（2026-02-28 快照）",
            description = "固定版本快照，中、英、日及方言。适合需要结果稳定的长期部署",
            group = GROUP_FUN_ASR,
        ),
        AsrModelPreset(
            id = "qwen-audio-3.1-asr-flash-streaming",
            name = "Qwen-Audio 3.1 流式（最新）",
            description = "30+ 语种及方言，支持热词与 Prompt 上下文增强，适合会议课堂等持续转写",
            recommended = true,
            group = GROUP_QWEN_AUDIO_STREAMING,
        ),
        AsrModelPreset(
            id = "qwen-audio-3.0-asr-flash-streaming",
            name = "Qwen-Audio 3.0 流式",
            description = "上一代 Qwen-Audio 流式模型，30+ 语种及方言，按音频时长计费",
            group = GROUP_QWEN_AUDIO_STREAMING,
        ),
        AsrModelPreset(
            id = "paraformer-realtime-v2",
            name = "Paraformer v2 实时",
            description = "上一代工业级流式模型，中英日韩德法俄七语种。官方建议迁移到 Fun-ASR",
            group = GROUP_PARAFORMER,
        ),
    )

    /** Presets grouped by family, preserving catalog order. */
    val ALIYUN_GROUPS: List<Pair<String, List<AsrModelPreset>>> =
        ALIYUN_REALTIME_MODELS.groupBy { it.group }.map { it.key to it.value }

    fun aliyunPreset(id: String): AsrModelPreset? =
        ALIYUN_REALTIME_MODELS.firstOrNull { it.id == id }

    /**
     * 通用 OpenAI 兼容接口常用的优质语音识别模型预设（硅基流动、Groq、第三方中转等）
     */
    val GENERIC_OPENAI_MODELS: List<AsrModelPreset> = listOf(
        AsrModelPreset(
            id = "FunAudioLLM/SenseVoiceSmall",
            name = "SenseVoice Small（硅基流动免费）",
            description = "硅基流动免费提供，云端阿里 SenseVoice 模型，中英日韩粤全能极速",
            recommended = true,
        ),
        AsrModelPreset(
            id = "openai/whisper-large-v3",
            name = "Whisper Large v3（官方旗舰）",
            description = "OpenAI 官方开源旗舰模型，99+ 语言最高精度（硅基流动/Groq支持）",
            recommended = true,
        ),
        AsrModelPreset(
            id = "openai/whisper-large-v3-turbo",
            name = "Whisper Large v3 Turbo（极速版）",
            description = "Whisper v3 涡轮极速版，大幅降低延迟且精度近乎无损（硅基/Groq支持）",
            recommended = true,
        ),
        AsrModelPreset(
            id = "whisper-1",
            name = "Whisper 1 (OpenAI 标准名)",
            description = "官方标准模型名，适配绝大多数第三方中转站与自建 Whisper 端点",
        ),
        AsrModelPreset(
            id = "Tele-AI/TeleSpeech-ASR",
            name = "TeleSpeech ASR（电信星辰大模型）",
            description = "电信自研中文/方言高精度语音转录大模型（硅基流动支持）",
        ),
    )
}
