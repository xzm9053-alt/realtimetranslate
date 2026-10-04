package com.xzm.realtimetranslate.translate

/** Log tag kept stable — `adb logcat -s GeminiTranslationEngine:V` filters on it. */
private const val GEMINI_LOG_TAG = "GeminiTranslationEngine"

/**
 * Google Gemini through its **OpenAI-compatibility layer**, not the native API.
 *
 * ```
 * POST https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
 * Authorization: Bearer <GEMINI_API_KEY>
 * ```
 *
 * The compat layer accepts the OpenAI chat-completions shape verbatim — `messages`,
 * `stream:true`, `choices[0].delta.content` — so everything is inherited from
 * [OpenAiCompatChatEngine]. Key from Google AI Studio.
 *
 * The only thing that differs is how it spells "don't think": the compat layer silently
 * drops parameters it doesn't recognise, so DeepSeek/Zhipu's `{"thinking":{"type":
 * "disabled"}}` would be ignored and the model would think before every sentence — which
 * for subtitles means seconds of dead air per line. Gemini wants a flat
 * `reasoning_effort`, declared as `thinkingOffStyle` in `geminiConfig()`. Because that
 * field is now per-engine config rather than a method override, this class is pure
 * wiring.
 *
 * **The knob's reach is not fully established.** Third-party reports say the Gemini 3
 * line accepts only `thinking_level` (and `thinkingBudget: 0`) and ignores
 * `reasoning_effort`, while others describe 3.8 Flash as non-reasoning to begin with —
 * in which case the parameter is moot. Google's own docs were unreachable when this was
 * written, so neither claim is verified here.
 *
 * The instrument that settles it is the `reason=` field in the TRANSPROF log, which
 * counts accumulated `reasoning_content`: `reason=0` means thinking really is off, and
 * a standing `reason>0` means this parameter does nothing for the selected model. If
 * that turns out to be the case, the fix is the `thinking_config` / `thinking_level`
 * shape, not another guess here.
 */
class GeminiTranslationEngine(
    apiKey: String,
    config: suspend () -> LlmRequestConfig,
) : OpenAiCompatChatEngine(
    apiKey = apiKey,
    config = config,
    providerName = "Gemini",
    logTag = GEMINI_LOG_TAG,
)
