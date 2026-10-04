package com.xzm.realtimetranslate.data

import androidx.annotation.StringRes
import com.xzm.realtimetranslate.R

/**
 * How the generic (user-supplied) engine spells "don't think".
 *
 * Every built-in LLM engine has one hard-coded spelling — DeepSeek and Zhipu take a
 * `{"thinking":{"type":"disabled"}}` object, Gemini's compatibility layer takes a flat
 * `reasoning_effort` — but the generic slot points at a service this app has never
 * seen. Guessing wrong is not harmless: a provider that does not recognise the field
 * either ignores it (thinking stays on, every sentence waits seconds) or rejects the
 * whole request with HTTP 400. So the user picks, and [NONE] is the default because
 * sending nothing is the only option that can never break a request.
 *
 * Stored by name in DataStore, following the same convention as [OcrScript]. The label
 * lives on the enum for the same reason [TranslationEngineType.keyLabelRes] does: the
 * options are rendered by iterating `entries`, so a new spelling is one enum value and
 * two strings instead of an enum value plus a `when` in the settings screen.
 */
enum class ThinkingOffStyle(@StringRes val labelRes: Int) {
    /** Send no parameter at all. The safe default for an unknown backend. */
    NONE(R.string.settings_generic_thinking_none),

    /** `{"thinking":{"type":"disabled"}}` — DeepSeek, Zhipu GLM and their clones. */
    THINKING_OBJECT(R.string.settings_generic_thinking_object),

    /** `"reasoning_effort":"none"` — Gemini's compatibility layer, OpenAI o-series style. */
    REASONING_EFFORT(R.string.settings_generic_thinking_effort),
    ;

    companion object {
        fun fromStorage(v: String?): ThinkingOffStyle =
            entries.firstOrNull { it.name == v } ?: NONE
    }
}
