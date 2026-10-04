package com.xzm.realtimetranslate.translate

/** Log tag kept stable — `adb logcat -s GenericOpenAiEngine:V` filters on it. */
private const val GENERIC_LOG_TAG = "GenericOpenAiEngine"

/**
 * The user-supplied OpenAI-compatible endpoint: a relay, OpenRouter, SiliconFlow, a
 * local Ollama. Everything — SSE streaming, the transient-failure retry, the
 * thinking-parameter fallback, model discovery and probing — lives in
 * [OpenAiCompatChatEngine], so this class is pure wiring.
 *
 * Two things are unusual here, both because the backend is unknown at build time:
 * the base URL and model come from user settings with **no fallback default** (blank
 * must fail loudly rather than silently point at another provider), and the
 * "don't think" spelling is a user choice ([com.xzm.realtimetranslate.data.ThinkingOffStyle])
 * whose default sends nothing at all.
 *
 * [config] is resolved per request, so editing the base URL / model / thinking style
 * applies to the next sentence without restarting the session. The API key is the one
 * exception: it is captured when the engine is built, so changing it needs a new session.
 */
class GenericOpenAiEngine(
    apiKey: String,
    config: suspend () -> LlmRequestConfig,
) : OpenAiCompatChatEngine(
    apiKey = apiKey,
    config = config,
    providerName = "自定义 API",
    logTag = GENERIC_LOG_TAG,
)
