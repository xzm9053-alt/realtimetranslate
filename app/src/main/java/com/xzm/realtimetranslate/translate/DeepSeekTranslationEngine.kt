package com.xzm.realtimetranslate.translate

/** Log tag kept stable — `adb logcat -s DeepSeekTranslationEngine:V` filters on it. */
private const val DEEPSEEK_LOG_TAG = "DeepSeekTranslationEngine"

/**
 * DeepSeek chat completions. Protocol and streaming live in [OpenAiCompatChatEngine];
 * everything specific to DeepSeek is the provider name and the log tag.
 *
 * [config] is resolved per request, so changing the model / base URL / thinking
 * switch in Settings takes effect on the next sentence without restarting the session.
 */
class DeepSeekTranslationEngine(
    apiKey: String,
    config: suspend () -> LlmRequestConfig,
) : OpenAiCompatChatEngine(
    apiKey = apiKey,
    config = config,
    providerName = "DeepSeek",
    logTag = DEEPSEEK_LOG_TAG,
)
