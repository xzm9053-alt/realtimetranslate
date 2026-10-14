package com.xzm.realtimetranslate.data

import androidx.annotation.StringRes
import com.xzm.realtimetranslate.R

/**
 * Whether an engine can run without an API key.
 *
 * Three states rather than a boolean because "needs a key to work" and "shows a key
 * field" are different questions. The generic slot must show the field while allowing
 * it to stay empty — Ollama and LM Studio have no key to give.
 */
enum class ApiKeyRequirement {
    /** A key is mandatory; without one the session must not start. */
    REQUIRED,

    /** The field is shown, but an empty value is allowed. */
    OPTIONAL,

    /** The engine authenticates another way, or not at all. No field is shown. */
    NONE,
}

/**
 * Which backend turns a recognized sentence into Chinese subtitles.
 *
 * The capability flags exist so that call sites never hard-code an engine name:
 * [requiresApiKey] drives the credential gate (7 call sites across 5 files),
 * [showsApiKeyField] drives the settings UI, and [hasPresetModels] tells the model
 * picker whether it has an offline baseline or must be filled from the provider's
 * own catalogue.
 */
enum class TranslationEngineType(
    val apiKeyRequirement: ApiKeyRequirement,
    val isLlm: Boolean,
    /** Label of this engine's API-key field; null when no key field is shown. */
    @StringRes val keyLabelRes: Int?,
    /**
     * True when the picker has an offline baseline to show. False both for engines with
     * no model to choose at all (the keyless translation APIs) and for the generic slot,
     * whose models belong to a provider only `GET /models` can describe.
     */
    val hasPresetModels: Boolean = true,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
) {
    DEEPSEEK(
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        isLlm = true,
        keyLabelRes = R.string.settings_deepseek_key,
        titleRes = R.string.engine_deepseek,
        summaryRes = R.string.engine_deepseek_summary,
    ),
    ZHIPU(
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        isLlm = true,
        keyLabelRes = R.string.settings_zhipu_key,
        titleRes = R.string.engine_zhipu,
        summaryRes = R.string.engine_zhipu_summary,
    ),
    GEMINI(
        apiKeyRequirement = ApiKeyRequirement.REQUIRED,
        isLlm = true,
        keyLabelRes = R.string.settings_gemini_key,
        titleRes = R.string.engine_gemini,
        summaryRes = R.string.engine_gemini_summary,
    ),
    MICROSOFT(
        apiKeyRequirement = ApiKeyRequirement.NONE,
        isLlm = false,
        keyLabelRes = null,
        hasPresetModels = false,
        titleRes = R.string.engine_microsoft,
        summaryRes = R.string.engine_microsoft_summary,
    ),
    GOOGLE_FREE(
        apiKeyRequirement = ApiKeyRequirement.NONE,
        isLlm = false,
        keyLabelRes = null,
        hasPresetModels = false,
        titleRes = R.string.engine_google_free,
        summaryRes = R.string.engine_google_free_summary,
    ),

    /**
     * Any OpenAI-compatible endpoint the user points us at — a relay, OpenRouter,
     * SiliconFlow, a local Ollama. The key is [ApiKeyRequirement.OPTIONAL]: a local
     * server has none, so a session must never be blocked for want of one. A hosted
     * provider with a missing key therefore fails per sentence with its own HTTP 401
     * instead of at session start, which is the price of allowing the empty case.
     */
    OPENAI_COMPAT(
        apiKeyRequirement = ApiKeyRequirement.OPTIONAL,
        isLlm = true,
        keyLabelRes = R.string.settings_generic_key,
        hasPresetModels = false,
        titleRes = R.string.engine_generic,
        summaryRes = R.string.engine_generic_summary,
    ),
    ;

    /** True only when a session must not start without a stored key. */
    val requiresApiKey: Boolean get() = apiKeyRequirement == ApiKeyRequirement.REQUIRED

    /** True when the settings screen shows a key field (and a "save key" button). */
    val showsApiKeyField: Boolean get() = apiKeyRequirement != ApiKeyRequirement.NONE

    companion object {
        /**
         * Stored names are the enum constant names. Anything unrecognised — a fresh
         * install, or a value from an engine that has since been removed (e.g. the
         * retired `GOOGLE_API`) — falls back to [DEEPSEEK].
         */
        fun fromStorage(value: String?): TranslationEngineType =
            entries.firstOrNull { it.name == value } ?: DEEPSEEK
    }
}
