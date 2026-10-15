package com.xzm.realtimetranslate.translate

/**
 * Metadata for selectable ASR models in Settings.
 */
data class AsrModelPreset(
    val id: String,
    val name: String,
    val description: String,
    val recommended: Boolean = false,
)

object AsrModelCatalog {

    /**
     * 阿里云百炼（DashScope）官方支持的全部实时流式语音识别（ASR）模型全家桶清单
     */
    val ALIYUN_REALTIME_MODELS: List<AsrModelPreset> = listOf(
        AsrModelPreset(
            id = "fun-asr-realtime",
            name = "FunASR 实时流式（官方主力）",
            description = "新一代主力模型，中英日韩粤多语种高精识别，超低延迟毫秒级打字机推流",
            recommended = true,
        ),
        AsrModelPreset(
            id = "paraformer-realtime-v2",
            name = "Paraformer v2 实时流式（高精）",
            description = "工业级高精度流式识别模型，中英文混读优异，极高稳定性",
            recommended = true,
        ),
        AsrModelPreset(
            id = "paraformer-realtime-v1",
            name = "Paraformer v1 实时流式（经典）",
            description = "经典通用流式识别模型，覆盖日常中英文对话与视频音频",
        ),
        AsrModelPreset(
            id = "paraformer-realtime-dialect-v1",
            name = "Paraformer 方言专有流式模型",
            description = "针对粤语、四川话、上海话等多方言口音深度优化的流式模型",
        ),
        AsrModelPreset(
            id = "paraformer-realtime-8k-v2",
            name = "Paraformer v2 实时流式 (8k电话)",
            description = "针对 8kHz 电话音质或客服音频场景深度优化的实时模型",
        ),
        AsrModelPreset(
            id = "paraformer-realtime-8k-v1",
            name = "Paraformer v1 实时流式 (8k电话)",
            description = "经典 8kHz 采样率电话音质专用流式识别模型",
        ),
        AsrModelPreset(
            id = "conformer-realtime-v1",
            name = "Conformer v1 实时流式",
            description = "基于 Conformer 神经网络架构的经典实时语音识别模型",
        ),
        AsrModelPreset(
            id = "conformer-realtime-8k-v1",
            name = "Conformer v1 实时流式 (8k)",
            description = "8kHz 电话采样的 Conformer 实时语音识别模型",
        ),
    )

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
