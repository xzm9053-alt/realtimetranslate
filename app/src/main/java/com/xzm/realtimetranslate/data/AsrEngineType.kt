package com.xzm.realtimetranslate.data

import androidx.annotation.StringRes
import com.xzm.realtimetranslate.R

/**
 * Which ASR backend turns captured speech PCM audio into source text.
 */
enum class AsrEngineType(
    val apiKeyRequirement: ApiKeyRequirement,
    val isCloud: Boolean,
    val isStreaming: Boolean,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
) {
    /**
     * Local SenseVoice on-device recognition via sherpa-onnx + Silero VAD.
     * Completely offline, zero network traffic, zero cost.
     */
    LOCAL_SENSEVOICE(
        apiKeyRequirement = ApiKeyRequirement.NONE,
        isCloud = false,
        isStreaming = false,
        titleRes = R.string.asr_engine_local,
        summaryRes = R.string.asr_engine_local_summary,
    ),

    /**
     * Aliyun DashScope FunASR / Paraformer full-duplex real-time WebSocket streaming.
     * Millisecond typewriter partial transcripts, low latency.
     * Reuses DashScope API Key.
     */
    ALIYUN_FUN_ASR(
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        isCloud = true,
        isStreaming = true,
        titleRes = R.string.asr_engine_aliyun,
        summaryRes = R.string.asr_engine_aliyun_summary,
    ),

    /**
     * Generic OpenAI-compatible HTTP POST /v1/audio/transcriptions.
     * Supports SiliconFlow (free SenseVoice/Whisper), Groq, local Faster-Whisper, etc.
     */
    GENERIC_OPENAI(
        apiKeyRequirement = ApiKeyRequirement.OPTIONAL,
        isCloud = true,
        isStreaming = false,
        titleRes = R.string.asr_engine_generic,
        summaryRes = R.string.asr_engine_generic_summary,
    );

    val requiresApiKey: Boolean get() = apiKeyRequirement == ApiKeyRequirement.REQUIRED
    val showsApiKeyField: Boolean get() = apiKeyRequirement != ApiKeyRequirement.NONE

    companion object {
        fun fromStorage(name: String?): AsrEngineType =
            entries.firstOrNull { it.name == name } ?: LOCAL_SENSEVOICE
    }
}
