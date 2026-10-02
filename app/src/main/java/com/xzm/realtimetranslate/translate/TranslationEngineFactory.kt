package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.data.ApiKeyStore
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
        TranslationEngineType.MICROSOFT -> MicrosoftFreeTranslationEngine()
        TranslationEngineType.GOOGLE_FREE -> GoogleFreeTranslationEngine()
        TranslationEngineType.GOOGLE_API -> GoogleApiTranslationEngine(apiKey = apiKey)
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
}

private fun UserSettings.deepSeekConfig(): LlmRequestConfig = LlmRequestConfig(
    baseUrl = deepseekBaseUrl.ifBlank { UserSettings.Defaults.DEEPSEEK_BASE_URL },
    model = deepseekModel.ifBlank { UserSettings.Defaults.DEEPSEEK_MODEL },
    thinking = aiDeepThinking,
)

private fun UserSettings.zhipuConfig(): LlmRequestConfig = LlmRequestConfig(
    baseUrl = zhipuBaseUrl.ifBlank { UserSettings.Defaults.ZHIPU_BASE_URL },
    model = zhipuModel.ifBlank { UserSettings.Defaults.ZHIPU_MODEL },
    thinking = aiDeepThinking,
)
