package com.xzm.realtimetranslate.translate

/** Log tag kept stable — `adb logcat -s ZhipuTranslationEngine:V` filters on it. */
private const val ZHIPU_LOG_TAG = "ZhipuTranslationEngine"

/**
 * Zhipu GLM (智谱) chat completions.
 *
 * The protocol is byte-for-byte the same shape as DeepSeek's — OpenAI-compatible
 * endpoint at `{baseUrl}/chat/completions`, `Bearer` auth, `data:`-framed SSE,
 * `choices[0].delta.content` — so this is only the provider name and log tag.
 *
 * One difference worth knowing: the `thinking` field is shared with DeepSeek, but
 * Zhipu's glm-5.3 family *forces* thinking and answers HTTP 400 (code 1210) when
 * asked to disable it. [OpenAiCompatChatEngine] latches that and retries without
 * the field, so a user who types such a model name still gets translations.
 */
class ZhipuTranslationEngine(
    apiKey: String,
    config: suspend () -> LlmRequestConfig,
) : OpenAiCompatChatEngine(
    apiKey = apiKey,
    config = config,
    providerName = "智谱 GLM",
    logTag = ZHIPU_LOG_TAG,
)
