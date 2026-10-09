package com.xzm.realtimetranslate.data

import androidx.annotation.StringRes
import com.xzm.realtimetranslate.R

/**
 * All user-facing settings that are safe to put in DataStore (not the API key).
 */
data class UserSettings(
    val endpoint: String = Defaults.ENDPOINT,
    val modelId: String = Defaults.MODEL_ID,
    val sourceLanguageCode: String = Defaults.SOURCE_LANGUAGE,
    val targetLanguageCode: String = Defaults.TARGET_LANGUAGE,
    val fontSizeSp: Float = Defaults.FONT_SIZE_SP,
    val backgroundAlpha: Float = Defaults.BACKGROUND_ALPHA,
    val displayMode: SubtitleDisplayMode = Defaults.DISPLAY_MODE,
    val sourceTextColor: Long = Defaults.SOURCE_TEXT_COLOR,
    val translationTextColor: Long = Defaults.TRANSLATION_TEXT_COLOR,
    val overlayX: Int = Defaults.OVERLAY_X,
    val overlayY: Int = Defaults.OVERLAY_Y,
    val overlayWidthDp: Int = Defaults.OVERLAY_WIDTH_DP,
    val overlayHeightDp: Int = Defaults.OVERLAY_HEIGHT_DP,
    val audioSourceMode: AudioSourceMode = Defaults.AUDIO_SOURCE,
    // Translation engine (DeepSeek / Microsoft free) and ASR model source.
    val translationEngine: TranslationEngineType = Defaults.TRANSLATION_ENGINE,
    val deepseekModel: String = Defaults.DEEPSEEK_MODEL,
    val deepseekBaseUrl: String = Defaults.DEEPSEEK_BASE_URL,
    val zhipuModel: String = Defaults.ZHIPU_MODEL,
    val zhipuBaseUrl: String = Defaults.ZHIPU_BASE_URL,
    val geminiModel: String = Defaults.GEMINI_MODEL,
    val geminiBaseUrl: String = Defaults.GEMINI_BASE_URL,
    // Generic OpenAI-compatible engine. Unlike the built-in engines there is no
    // ship-time default to fall back on, so blank means "not configured yet" and the
    // request fails with a readable error rather than silently pointing somewhere else.
    val genericBaseUrl: String = Defaults.GENERIC_BASE_URL,
    val genericModel: String = Defaults.GENERIC_MODEL,
    // How this engine spells "don't think" — unknowable in advance, so the user picks.
    val genericThinkingOffStyle: ThinkingOffStyle = Defaults.GENERIC_THINKING_OFF_STYLE,
    // Global "deep thinking" switch for AI-style engines (DeepSeek / Zhipu GLM).
    // Off by default: thinking adds ~1.7ms per reasoning character of first-token
    // latency, which is what made DeepSeek feel slow. No effect on the pure
    // translation APIs (Microsoft / Google) — see TranslationEngineType.isLlm.
    val aiDeepThinking: Boolean = Defaults.AI_DEEP_THINKING,
    val modelMirrorUrl: String = Defaults.MODEL_MIRROR_URL,
    val huggingfaceToken: String = Defaults.HF_TOKEN,
    val historyMode: HistoryMode = Defaults.HISTORY_MODE,
    val historyLimit: Int = Defaults.HISTORY_LIMIT,
    val vadMinSilenceSec: Float = Defaults.VAD_MIN_SILENCE_SEC,
    val vadMaxSpeechSec: Float = Defaults.VAD_MAX_SPEECH_SEC,
    // Screen-region OCR recognizer script (ML Kit bundled models).
    val ocrScript: OcrScript = Defaults.OCR_SCRIPT,
    // Screen-OCR capture-area outline: a subtle box kept on screen after the
    // user picks a region so they can see which part is being translated.
    val ocrRegionOutlineEnabled: Boolean = Defaults.OCR_OUTLINE_ENABLED,
    // Opaque outline color (alpha applied separately via OCR_OUTLINE_ALPHA).
    val ocrRegionOutlineColor: Long = Defaults.OCR_OUTLINE_COLOR,
    // Border opacity 0.1..1.0 — "明显程度" of the outline.
    val ocrRegionOutlineAlpha: Float = Defaults.OCR_OUTLINE_ALPHA,
    // Cache of the last live model check, so dead models stay marked across restarts
    // instead of costing another round of requests. Not a user preference.
    val discoveredModels: List<String> = emptyList(),
    /** Model id → [com.xzm.realtimetranslate.translate.ModelAvailability] name. */
    val modelAvailability: Map<String, String> = emptyMap(),
    /** Epoch millis of that check; 0 = never run. */
    val modelsCheckedAt: Long = 0L,
    // Writes the original and translated text into a separate log file. Off by
    // default: it is the user's own content, and the diagnostic report is meant to be
    // forwarded to the author.
    val diagnosticLogContent: Boolean = Defaults.DIAGNOSTIC_LOG_CONTENT,
) {
    object Defaults {
        // Kept for schema compatibility; the engine now reads DeepSeek settings directly.
        const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val MODEL_ID = "gemini-3.5-live-translate-preview"
        const val SOURCE_LANGUAGE = "auto"
        const val TARGET_LANGUAGE = "zh-Hans"
        const val DEEPSEEK_MODEL = "deepseek-v4-flash"
        const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
        const val ZHIPU_MODEL = "glm-4-flash"
        const val ZHIPU_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
        // Gemini via its OpenAI-compatibility layer (the native API has a different
        // request shape). 3.8 Flash because Google retired the previous default
        // (gemini-2.5-flash) for new API users with an HTTP 404 — see
        // migrateGeminiModel and ModelPresets.
        const val GEMINI_MODEL = "gemini-3.8-flash"
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai"
        // Generic engine: the user supplies both, and neither has a sensible default.
        const val GENERIC_BASE_URL = ""
        const val GENERIC_MODEL = ""
        val GENERIC_THINKING_OFF_STYLE: ThinkingOffStyle = ThinkingOffStyle.NONE
        const val AI_DEEP_THINKING = false
        const val MODEL_MIRROR_URL = "https://hf-mirror.com"
        const val HF_TOKEN = ""
        val TRANSLATION_ENGINE: TranslationEngineType = TranslationEngineType.DEEPSEEK
        const val FONT_SIZE_SP = 18f
        const val BACKGROUND_ALPHA = 0.65f
        val DISPLAY_MODE: SubtitleDisplayMode = SubtitleDisplayMode.TRANSLATION
        // Subtitle text colors, ARGB as Long. Defaults keep the current look:
        // source semi-transparent white (alpha 200), translation opaque white.
        const val SOURCE_TEXT_COLOR = 0xC8FFFFFF
        const val TRANSLATION_TEXT_COLOR = 0xFFFFFFFF
        const val OVERLAY_X = 24
        const val OVERLAY_Y = -1
        const val OVERLAY_WIDTH_DP = 360
        const val OVERLAY_HEIGHT_DP = 120
        val AUDIO_SOURCE: AudioSourceMode = AudioSourceMode.MEDIA
        val HISTORY_MODE: HistoryMode = HistoryMode.SAVE_ALL
        // Max entries kept in AUTO_CLEAR mode (configurable, 5..200).
        const val HISTORY_LIMIT = 20
        const val HISTORY_LIMIT_MIN = 5
        const val HISTORY_LIMIT_MAX = 200
        // Silero VAD 切句参数（秒）。
        const val VAD_MIN_SILENCE_SEC = 0.2f
        const val VAD_MAX_SPEECH_SEC = 2f
        // Default OCR script for screen-region text recognition. AUTO runs the
        // Latin + Japanese recognizers and picks the better result (English/Japanese
        // screens), matching the default "auto" source language.
        val OCR_SCRIPT: OcrScript = OcrScript.AUTO
        // Capture-area outline defaults keep the subtle look (accent, ~50% alpha).
        const val OCR_OUTLINE_ENABLED = true
        const val OCR_OUTLINE_COLOR = 0xFF2E7CF6
        const val OCR_OUTLINE_ALPHA = 0.5f
        const val DIAGNOSTIC_LOG_CONTENT = false
    }
}

data class LanguageOption(
    val code: String,
    @StringRes val labelRes: Int,
)

object SupportedLanguages {
    val targetOptions: List<LanguageOption> = listOf(
        LanguageOption("zh-Hans", R.string.lang_zh_hans),
        LanguageOption("zh-Hant", R.string.lang_zh_hant),
        LanguageOption("en", R.string.lang_en),
        LanguageOption("ja", R.string.lang_ja),
        LanguageOption("ko", R.string.lang_ko),
        LanguageOption("es", R.string.lang_es),
        LanguageOption("fr", R.string.lang_fr),
        LanguageOption("de", R.string.lang_de),
        LanguageOption("ru", R.string.lang_ru),
        LanguageOption("pt-BR", R.string.lang_pt_br),
        LanguageOption("pt-PT", R.string.lang_pt_pt),
        LanguageOption("it", R.string.lang_it),
        LanguageOption("ar", R.string.lang_ar),
        LanguageOption("hi", R.string.lang_hi),
        LanguageOption("th", R.string.lang_th),
        LanguageOption("vi", R.string.lang_vi),
        LanguageOption("id", R.string.lang_id),
        LanguageOption("tr", R.string.lang_tr),
        LanguageOption("pl", R.string.lang_pl),
        LanguageOption("nl", R.string.lang_nl),
        LanguageOption("uk", R.string.lang_uk),
    )

    val sourceOptions: List<LanguageOption> = listOf(
        LanguageOption("auto", R.string.lang_auto),
    ) + targetOptions

    fun labelResOf(code: String): Int =
        (sourceOptions + targetOptions).firstOrNull { it.code == code }?.labelRes
            ?: R.string.lang_en
}
