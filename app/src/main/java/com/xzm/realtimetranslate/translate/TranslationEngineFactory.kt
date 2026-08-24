package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings

/**
 * Builds the user-selected translation engine from current settings.
 * Shared by the audio subtitle pipeline ([RealtimeTranslationClient]) and the
 * screen-region OCR session service so the two modes pick the same engine.
 */
object TranslationEngineFactory {
    fun create(settings: UserSettings, apiKey: String): TranslationEngine =
        when (settings.translationEngine) {
            TranslationEngineType.DEEPSEEK -> DeepSeekTranslationEngine(
                apiKey = apiKey,
                baseUrl = settings.deepseekBaseUrl.ifBlank { "https://api.deepseek.com" },
                model = settings.deepseekModel.ifBlank { "deepseek-v4-flash" },
            )
            TranslationEngineType.MICROSOFT -> MicrosoftFreeTranslationEngine()
        }
}
