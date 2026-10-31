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
    // 「边说边出」：句子还没说完就先上草稿字幕，说完再整句替换。
    // 默认开：这是本 App 在"实时感"上最明显的一步，代价是本地多跑几次识别
    // （纯 CPU，不花钱、不联网）+ 每句多一两次翻译请求（见 RealtimeTranslationClient）。
    val partialTranscripts: Boolean = Defaults.PARTIAL_TRANSCRIPTS,
    // Screen-region OCR recognizer script (ML Kit bundled models).
    val ocrScript: OcrScript = Defaults.OCR_SCRIPT,
    // Screen-OCR capture-area outline: a subtle box kept on screen after the
    // user picks a region so they can see which part is being translated.
    val ocrRegionOutlineEnabled: Boolean = Defaults.OCR_OUTLINE_ENABLED,
    // Opaque outline color (alpha applied separately via OCR_OUTLINE_ALPHA).
    val ocrRegionOutlineColor: Long = Defaults.OCR_OUTLINE_COLOR,
    // Border opacity 0.1..1.0 — "明显程度" of the outline.
    val ocrRegionOutlineAlpha: Float = Defaults.OCR_OUTLINE_ALPHA,
    // Full-screen cover mode: paint the translation over the original text.
    // Bilingual keeps the original in a smaller, dimmer line above the translation.
    val coverBilingual: Boolean = Defaults.COVER_BILINGUAL,
    // Multiplier on the auto-fit font size of a cover block (0.8..1.6).
    val coverFontScale: Float = Defaults.COVER_FONT_SCALE,
    // "AUTO" samples the background color behind each block; "DARK" always uses black.
    val coverBlockStyle: String = Defaults.COVER_BLOCK_STYLE,
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
    // ASR Engine selection and settings
    val asrEngine: AsrEngineType = Defaults.ASR_ENGINE,
    val aliyunAsrModel: String = Defaults.ALIYUN_ASR_MODEL,
    // 阿里百炼业务空间 ID。官方已将百炼迁到业务空间专属域名
    // ({WorkspaceId}.cn-beijing.maas.aliyuncs.com)，新模型必须走新域名。
    // 留空时回退到旧域名 dashscope.aliyuncs.com，保证现有用户不被影响。
    val aliyunWorkspaceId: String = Defaults.ALIYUN_WORKSPACE_ID,
    // 译文段落排版：0 = 不分段（连续输出，保持现状）；
    // N = 每翻完 N 句，下一句译文另起一行。翻译仍逐句进行，只改排版，不影响速度与费用。
    val sentencesPerParagraph: Int = Defaults.SENTENCES_PER_PARAGRAPH,
    /**
     * Which language(s) the video subtitles carry — burn-in, on-screen preview and the
     * exported SRT all read this one value so the three never disagree.
     */
    val videoSubtitleMode: VideoSubtitleMode = Defaults.VIDEO_SUBTITLE_MODE,
    // 视频字幕的语音识别引擎：默认本地 SenseVoice（免 Key 离线），
    // 需要五种以外的语言时切云端 Whisper。与声音同传的 asrEngine 互不影响。
    val videoAsrEngine: VideoAsrEngine = Defaults.VIDEO_ASR_ENGINE,
    // 阿里百炼 FunASR 静音断句门槛（毫秒）：说话停顿超过此阈值即判定为句子结束并送翻。默认 400ms。
    val aliyunAsrSilenceMs: Int = Defaults.ALIYUN_ASR_SILENCE_MS,
    val genericAsrBaseUrl: String = Defaults.GENERIC_ASR_BASE_URL,
    val genericAsrApiKey: String = Defaults.GENERIC_ASR_API_KEY,
    val genericAsrModel: String = Defaults.GENERIC_ASR_MODEL,
    // 译文草稿预览：默认关闭。关闭时仅在定稿(onSegment)时翻译整句，彻底消除译文闪烁与语序颠倒重写；
    // 开启时对未定稿的草稿片段进行低频防抖预览翻译。原文打字机不受此开关影响，始终保持极速流式。
    val draftTranslationPreview: Boolean = Defaults.DRAFT_TRANSLATION_PREVIEW,
    // 静音检测告警：持续 10 秒绝对静音时输出日志与柔性提示，排查内录受限或麦克风故障。
    val silenceDetectionEnabled: Boolean = Defaults.SILENCE_DETECTION_ENABLED,
    // 常驻悬浮球开关：在所有 App 上方显示便捷悬浮球，承载多模式切换。
    val hubEnabled: Boolean = Defaults.HUB_ENABLED,
    // 声音同传仅识别不翻译：只提取语音生成字幕，不调用大模型翻译，完全离线免 API Key
    val voiceRecognizeOnly: Boolean = Defaults.VOICE_RECOGNIZE_ONLY,
) {
    object Defaults {
        const val VOICE_RECOGNIZE_ONLY = false
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
        // Silero VAD 切句参数（秒）：默认静音 0.2s，单句上限 2.0s 保证极速切句出词。
        const val VAD_MIN_SILENCE_SEC = 0.2f
        const val VAD_MAX_SPEECH_SEC = 2.0f
        const val PARTIAL_TRANSCRIPTS = true
        // Default OCR script for screen-region text recognition. AUTO runs the
        // Latin + Japanese recognizers and picks the better result (English/Japanese
        // screens), matching the default "auto" source language.
        val OCR_SCRIPT: OcrScript = OcrScript.AUTO
        // Capture-area outline defaults keep the subtle look (accent, ~50% alpha).
        const val OCR_OUTLINE_ENABLED = true
        const val OCR_OUTLINE_COLOR = 0xFF2E7CF6
        const val OCR_OUTLINE_ALPHA = 0.5f
        const val COVER_BILINGUAL = false
        const val COVER_FONT_SCALE = 1.0f
        const val COVER_BLOCK_STYLE = "AUTO"
        const val DIAGNOSTIC_LOG_CONTENT = false
        val ASR_ENGINE: AsrEngineType = AsrEngineType.LOCAL_SENSEVOICE
        const val ALIYUN_ASR_MODEL = "fun-asr-realtime"
        const val ALIYUN_WORKSPACE_ID = ""
        // 0 = 不分段（连续输出）；取值范围 0..SENTENCES_PER_PARAGRAPH_MAX。
        const val SENTENCES_PER_PARAGRAPH = 0
        const val SENTENCES_PER_PARAGRAPH_MAX = 5
        val VIDEO_SUBTITLE_MODE: VideoSubtitleMode = VideoSubtitleMode.TRANSLATION
        val VIDEO_ASR_ENGINE: VideoAsrEngine = VideoAsrEngine.LOCAL
        const val ALIYUN_ASR_SILENCE_MS = 200
        const val GENERIC_ASR_BASE_URL = "https://api.siliconflow.cn/v1"
        const val GENERIC_ASR_API_KEY = ""
        const val GENERIC_ASR_MODEL = "FunAudioLLM/SenseVoiceSmall"
        const val DRAFT_TRANSLATION_PREVIEW = true
        const val SILENCE_DETECTION_ENABLED = true
        const val HUB_ENABLED = false
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
