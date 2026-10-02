package com.xzm.realtimetranslate.data

import androidx.annotation.StringRes
import com.xzm.realtimetranslate.R

/**
 * Which backend turns a recognized sentence into Chinese subtitles.
 *
 * The capability flags exist so that call sites never hard-code an engine name:
 * [requiresApiKey] drives the credential gate (5 call sites) and the settings UI,
 * [isLlm] marks engines that have a "deep thinking" mode so the global
 * AI-thinking switch can be enabled/disabled sensibly.
 */
enum class TranslationEngineType(
    val requiresApiKey: Boolean,
    val isLlm: Boolean,
    /** Label of this engine's API-key field; null when the engine needs no key. */
    @StringRes val keyLabelRes: Int?,
) {
    DEEPSEEK(requiresApiKey = true, isLlm = true, keyLabelRes = R.string.settings_deepseek_key),
    ZHIPU(requiresApiKey = true, isLlm = true, keyLabelRes = R.string.settings_zhipu_key),
    MICROSOFT(requiresApiKey = false, isLlm = false, keyLabelRes = null),
    GOOGLE_FREE(requiresApiKey = false, isLlm = false, keyLabelRes = null),
    GOOGLE_API(requiresApiKey = true, isLlm = false, keyLabelRes = R.string.settings_google_key),
    ;

    companion object {
        fun fromStorage(value: String?): TranslationEngineType =
            entries.firstOrNull { it.name == value } ?: DEEPSEEK
    }
}
