package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.ThinkingOffStyle
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings

/**
 * Builds the user-selected translation engine from current settings.
 *
 * This is the **only** place that switches on [TranslationEngineType] — the audio
 * subtitle pipeline ([RealtimeTranslationClient]), the screen-region OCR service and
 * the settings screen all come through here, so a new engine is one branch plus one
 * enum value instead of a `when` in every caller.
 */
object TranslationEngineFactory {

    /**
     * @param liveSettings re-read on every request by the LLM engines, so editing the
     *   model / base URL / thinking switch applies to the next sentence instead of
     *   requiring the user to restart the session. Defaults to a frozen snapshot for
     *   callers that have no live source (e.g. a one-shot connection test).
     */
    fun create(
        settings: UserSettings,
        apiKey: String,
        liveSettings: suspend () -> UserSettings = { settings },
    ): TranslationEngine = when (settings.translationEngine) {
        TranslationEngineType.DEEPSEEK -> DeepSeekTranslationEngine(
            apiKey = apiKey,
            config = { liveSettings().deepSeekConfig() },
        )
        TranslationEngineType.ZHIPU -> ZhipuTranslationEngine(
            apiKey = apiKey,
            config = { liveSettings().zhipuConfig() },
        )
        TranslationEngineType.GEMINI -> GeminiTranslationEngine(
            apiKey = apiKey,
            config = { liveSettings().geminiConfig() },
        )
        TranslationEngineType.OPENAI_COMPAT -> GenericOpenAiEngine(
            apiKey = apiKey,
            config = { liveSettings().genericConfig() },
        )
        TranslationEngineType.MICROSOFT -> MicrosoftFreeTranslationEngine()
        TranslationEngineType.GOOGLE_FREE -> GoogleFreeTranslationEngine()
    }

    /** Convenience overload: pulls the key belonging to [settings]'s engine. */
    fun create(
        settings: UserSettings,
        keys: ApiKeyStore,
        liveSettings: suspend () -> UserSettings = { settings },
    ): TranslationEngine =
        create(settings, keys.getKeyFor(settings.translationEngine), liveSettings)

    /** One-shot connectivity check for the settings screen. */
    suspend fun testConnection(
        settings: UserSettings,
        keys: ApiKeyStore,
        targetLang: String,
    ): Result<String> = create(settings, keys).testConnection(targetLang)

    /** Live model check for the settings screen: which of [candidates] still work. */
    suspend fun checkModels(
        settings: UserSettings,
        keys: ApiKeyStore,
        candidates: List<String>,
    ): ModelCheck = create(settings, keys).checkModels(candidates)
}

private fun UserSettings.deepSeekConfig(): LlmRequestConfig = LlmRequestConfig(
    baseUrl = deepseekBaseUrl.ifBlank { UserSettings.Defaults.DEEPSEEK_BASE_URL },
    model = deepseekModel.ifBlank { UserSettings.Defaults.DEEPSEEK_MODEL },
    thinking = aiDeepThinking,
    thinkingOffStyle = ThinkingOffStyle.THINKING_OBJECT,
)

private fun UserSettings.zhipuConfig(): LlmRequestConfig = LlmRequestConfig(
    baseUrl = zhipuBaseUrl.ifBlank { UserSettings.Defaults.ZHIPU_BASE_URL },
    model = zhipuModel.ifBlank { UserSettings.Defaults.ZHIPU_MODEL },
    thinking = aiDeepThinking,
    thinkingOffStyle = ThinkingOffStyle.THINKING_OBJECT,
)

// The compatibility layer silently drops a `thinking` object, so Gemini needs the flat
// spelling. This used to be a method override on GeminiTranslationEngine.
private fun UserSettings.geminiConfig(): LlmRequestConfig = LlmRequestConfig(
    baseUrl = geminiBaseUrl.ifBlank { UserSettings.Defaults.GEMINI_BASE_URL },
    model = geminiModel.ifBlank { UserSettings.Defaults.GEMINI_MODEL },
    thinking = aiDeepThinking,
    thinkingOffStyle = ThinkingOffStyle.REASONING_EFFORT,
)

private fun UserSettings.genericConfig(): LlmRequestConfig = LlmRequestConfig(
    // Deliberately no `ifBlank { default }`: this engine's whole point is that the
    // destination is the user's choice, so a blank URL must fail with its own error
    // instead of quietly sending their speech to some other provider.
    //
    // The suffix is stripped because the base class appends "/chat/completions" and
    // pasting the full endpoint out of a provider's docs is the common case.
    baseUrl = genericBaseUrl.trim().trimEnd('/').removeSuffix("/chat/completions"),
    model = genericModel.trim(),
    thinking = aiDeepThinking,
    thinkingOffStyle = genericThinkingOffStyle,
)
