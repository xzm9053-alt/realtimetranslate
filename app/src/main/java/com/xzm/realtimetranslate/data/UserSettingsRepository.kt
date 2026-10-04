package com.xzm.realtimetranslate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserSettingsRepository(private val context: Context) {
    private object Keys {
        val endpoint = stringPreferencesKey("endpoint")
        val modelId = stringPreferencesKey("model_id")
        val sourceLanguage = stringPreferencesKey("source_language")
        val targetLanguage = stringPreferencesKey("target_language")
        val fontSizeSp = floatPreferencesKey("font_size_sp")
        val backgroundAlpha = floatPreferencesKey("background_alpha")
        val displayMode = stringPreferencesKey("display_mode")
        // Legacy pre-3-option boolean key — read once for migration, never written.
        val legacyBilingual = booleanPreferencesKey("bilingual")
        val sourceTextColor = longPreferencesKey("source_text_color")
        val translationTextColor = longPreferencesKey("translation_text_color")
        val overlayX = intPreferencesKey("overlay_x")
        val overlayY = intPreferencesKey("overlay_y")
        val overlayWidthDp = intPreferencesKey("overlay_width_dp")
        val overlayHeightDp = intPreferencesKey("overlay_height_dp")
        val audioSourceMode = stringPreferencesKey("audio_source_mode")
        val translationEngine = stringPreferencesKey("translation_engine")
        val deepseekModel = stringPreferencesKey("deepseek_model")
        val deepseekBaseUrl = stringPreferencesKey("deepseek_base_url")
        val zhipuModel = stringPreferencesKey("zhipu_model")
        val zhipuBaseUrl = stringPreferencesKey("zhipu_base_url")
        val geminiModel = stringPreferencesKey("gemini_model")
        val geminiBaseUrl = stringPreferencesKey("gemini_base_url")
        val genericModel = stringPreferencesKey("generic_model")
        val genericBaseUrl = stringPreferencesKey("generic_base_url")
        val genericThinkingOffStyle = stringPreferencesKey("generic_thinking_off_style")
        val aiDeepThinking = booleanPreferencesKey("ai_deep_thinking")
        val modelMirrorUrl = stringPreferencesKey("model_mirror_url")
        val huggingfaceToken = stringPreferencesKey("huggingface_token")
        val historyMode = stringPreferencesKey("history_mode")
        val historyLimit = intPreferencesKey("history_limit")
        val vadMinSilenceSec = floatPreferencesKey("vad_min_silence_sec")
        val vadMaxSpeechSec = floatPreferencesKey("vad_max_speech_sec")
        val ocrScript = stringPreferencesKey("ocr_script")
        val ocrRegionOutlineEnabled = booleanPreferencesKey("ocr_region_outline_enabled")
        val ocrRegionOutlineColor = longPreferencesKey("ocr_region_outline_color")
        val ocrRegionOutlineAlpha = floatPreferencesKey("ocr_region_outline_alpha")
        val discoveredModels = stringPreferencesKey("discovered_models")
        val modelAvailability = stringPreferencesKey("model_availability")
        val modelsCheckedAt = longPreferencesKey("models_checked_at")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        prefs.toSettings()
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.endpoint] = next.endpoint
            prefs[Keys.modelId] = next.modelId
            prefs[Keys.sourceLanguage] = next.sourceLanguageCode
            prefs[Keys.targetLanguage] = next.targetLanguageCode
            prefs[Keys.fontSizeSp] = next.fontSizeSp
            prefs[Keys.backgroundAlpha] = next.backgroundAlpha
            prefs[Keys.displayMode] = next.displayMode.name
            prefs[Keys.sourceTextColor] = next.sourceTextColor
            prefs[Keys.translationTextColor] = next.translationTextColor
            prefs[Keys.overlayX] = next.overlayX
            prefs[Keys.overlayY] = next.overlayY
            prefs[Keys.overlayWidthDp] = next.overlayWidthDp
            prefs[Keys.overlayHeightDp] = next.overlayHeightDp
            prefs[Keys.audioSourceMode] = next.audioSourceMode.name
            prefs[Keys.translationEngine] = next.translationEngine.name
            prefs[Keys.deepseekModel] = next.deepseekModel
            prefs[Keys.deepseekBaseUrl] = next.deepseekBaseUrl
            prefs[Keys.zhipuModel] = next.zhipuModel
            prefs[Keys.zhipuBaseUrl] = next.zhipuBaseUrl
            prefs[Keys.geminiModel] = next.geminiModel
            prefs[Keys.geminiBaseUrl] = next.geminiBaseUrl
            prefs[Keys.genericModel] = next.genericModel
            prefs[Keys.genericBaseUrl] = next.genericBaseUrl
            prefs[Keys.genericThinkingOffStyle] = next.genericThinkingOffStyle.name
            prefs[Keys.aiDeepThinking] = next.aiDeepThinking
            prefs[Keys.modelMirrorUrl] = next.modelMirrorUrl
            prefs[Keys.huggingfaceToken] = next.huggingfaceToken
            prefs[Keys.historyMode] = next.historyMode.name
            prefs[Keys.historyLimit] = next.historyLimit
            prefs[Keys.vadMinSilenceSec] = next.vadMinSilenceSec
            prefs[Keys.vadMaxSpeechSec] = next.vadMaxSpeechSec
            prefs[Keys.ocrScript] = next.ocrScript.name
            prefs[Keys.ocrRegionOutlineEnabled] = next.ocrRegionOutlineEnabled
            prefs[Keys.ocrRegionOutlineColor] = next.ocrRegionOutlineColor
            prefs[Keys.ocrRegionOutlineAlpha] = next.ocrRegionOutlineAlpha
            prefs[Keys.discoveredModels] = ModelCacheCodec.encodeList(next.discoveredModels)
            prefs[Keys.modelAvailability] = ModelCacheCodec.encodeMap(next.modelAvailability)
            prefs[Keys.modelsCheckedAt] = next.modelsCheckedAt
        }
    }

    suspend fun resetSubtitleAppearance() {
        update {
            it.copy(
                fontSizeSp = UserSettings.Defaults.FONT_SIZE_SP,
                backgroundAlpha = UserSettings.Defaults.BACKGROUND_ALPHA,
                displayMode = UserSettings.Defaults.DISPLAY_MODE,
                sourceTextColor = UserSettings.Defaults.SOURCE_TEXT_COLOR,
                translationTextColor = UserSettings.Defaults.TRANSLATION_TEXT_COLOR,
            )
        }
    }

    private fun Preferences.toSettings(): UserSettings = UserSettings(
        endpoint = this[Keys.endpoint] ?: UserSettings.Defaults.ENDPOINT,
        modelId = this[Keys.modelId] ?: UserSettings.Defaults.MODEL_ID,
        sourceLanguageCode = this[Keys.sourceLanguage] ?: UserSettings.Defaults.SOURCE_LANGUAGE,
        targetLanguageCode = this[Keys.targetLanguage] ?: UserSettings.Defaults.TARGET_LANGUAGE,
        fontSizeSp = this[Keys.fontSizeSp] ?: UserSettings.Defaults.FONT_SIZE_SP,
        backgroundAlpha = this[Keys.backgroundAlpha] ?: UserSettings.Defaults.BACKGROUND_ALPHA,
        displayMode = this[Keys.displayMode]?.let { SubtitleDisplayMode.fromStorage(it) }
            ?: legacyDisplayMode(this),
        sourceTextColor = this[Keys.sourceTextColor] ?: UserSettings.Defaults.SOURCE_TEXT_COLOR,
        translationTextColor = this[Keys.translationTextColor]
            ?: UserSettings.Defaults.TRANSLATION_TEXT_COLOR,
        overlayX = this[Keys.overlayX] ?: UserSettings.Defaults.OVERLAY_X,
        overlayY = this[Keys.overlayY] ?: UserSettings.Defaults.OVERLAY_Y,
        overlayWidthDp = this[Keys.overlayWidthDp] ?: UserSettings.Defaults.OVERLAY_WIDTH_DP,
        overlayHeightDp = this[Keys.overlayHeightDp] ?: UserSettings.Defaults.OVERLAY_HEIGHT_DP,
        audioSourceMode = AudioSourceMode.fromStorage(this[Keys.audioSourceMode]),
        translationEngine = TranslationEngineType.fromStorage(this[Keys.translationEngine]),
        deepseekModel = this[Keys.deepseekModel] ?: UserSettings.Defaults.DEEPSEEK_MODEL,
        deepseekBaseUrl = this[Keys.deepseekBaseUrl] ?: UserSettings.Defaults.DEEPSEEK_BASE_URL,
        zhipuModel = this[Keys.zhipuModel] ?: UserSettings.Defaults.ZHIPU_MODEL,
        zhipuBaseUrl = this[Keys.zhipuBaseUrl] ?: UserSettings.Defaults.ZHIPU_BASE_URL,
        geminiModel = migrateGeminiModel(this[Keys.geminiModel]),
        geminiBaseUrl = this[Keys.geminiBaseUrl] ?: UserSettings.Defaults.GEMINI_BASE_URL,
        genericModel = this[Keys.genericModel] ?: UserSettings.Defaults.GENERIC_MODEL,
        genericBaseUrl = this[Keys.genericBaseUrl] ?: UserSettings.Defaults.GENERIC_BASE_URL,
        genericThinkingOffStyle = ThinkingOffStyle.fromStorage(this[Keys.genericThinkingOffStyle]),
        // Absent key (existing installs) → Defaults, i.e. false. No migration needed.
        aiDeepThinking = this[Keys.aiDeepThinking] ?: UserSettings.Defaults.AI_DEEP_THINKING,
        modelMirrorUrl = this[Keys.modelMirrorUrl] ?: UserSettings.Defaults.MODEL_MIRROR_URL,
        huggingfaceToken = this[Keys.huggingfaceToken] ?: UserSettings.Defaults.HF_TOKEN,
        historyMode = HistoryMode.fromStorage(this[Keys.historyMode]),
        historyLimit = this[Keys.historyLimit] ?: UserSettings.Defaults.HISTORY_LIMIT,
        vadMinSilenceSec = this[Keys.vadMinSilenceSec] ?: UserSettings.Defaults.VAD_MIN_SILENCE_SEC,
        vadMaxSpeechSec = this[Keys.vadMaxSpeechSec] ?: UserSettings.Defaults.VAD_MAX_SPEECH_SEC,
        ocrScript = OcrScript.fromStorage(this[Keys.ocrScript]),
        ocrRegionOutlineEnabled = this[Keys.ocrRegionOutlineEnabled]
            ?: UserSettings.Defaults.OCR_OUTLINE_ENABLED,
        ocrRegionOutlineColor = this[Keys.ocrRegionOutlineColor]
            ?: UserSettings.Defaults.OCR_OUTLINE_COLOR,
        ocrRegionOutlineAlpha = this[Keys.ocrRegionOutlineAlpha]
            ?: UserSettings.Defaults.OCR_OUTLINE_ALPHA,
        discoveredModels = ModelCacheCodec.decodeList(this[Keys.discoveredModels]),
        modelAvailability = ModelCacheCodec.decodeMap(this[Keys.modelAvailability]),
        modelsCheckedAt = this[Keys.modelsCheckedAt] ?: 0L,
    )

    /** Migrate the pre-3-option boolean: true→BOTH, false/absent→TRANSLATION. */
    private fun legacyDisplayMode(p: Preferences): SubtitleDisplayMode =
        when (p[Keys.legacyBilingual]) {
            true -> SubtitleDisplayMode.BOTH
            else -> SubtitleDisplayMode.TRANSLATION
        }
}

/** Shipped as this app's Gemini default until Google retired it for new API users. */
private const val RETIRED_GEMINI_MODEL = "gemini-2.5-flash"

/**
 * Changing a shipped default only reaches installs that never stored one — everyone
 * else keeps whatever is on disk. Without this rewrite an existing install would stay
 * pinned to the retired model and answer HTTP 404 forever, looking like a broken app
 * rather than a stale default.
 */
internal fun migrateGeminiModel(stored: String?): String =
    if (stored.isNullOrBlank() || stored == RETIRED_GEMINI_MODEL) {
        UserSettings.Defaults.GEMINI_MODEL
    } else {
        stored
    }

/**
 * DataStore holds primitives, so the model-check cache is stored as flat text: one id
 * per line, and `id<TAB>status` for the availability map.
 *
 * Both decoders drop malformed lines instead of failing: a corrupted cache is worth
 * losing, and losing it must never take the settings screen down with it.
 */
internal object ModelCacheCodec {

    fun encodeList(values: List<String>): String =
        values.filter { it.isNotBlank() }.joinToString("\n")

    fun decodeList(raw: String?): List<String> =
        raw?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    fun encodeMap(values: Map<String, String>): String =
        values.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString("\n") { "${it.key}\t${it.value}" }

    fun decodeMap(raw: String?): Map<String, String> =
        raw?.split("\n")
            ?.mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0 || tab == line.length - 1) {
                    null
                } else {
                    line.substring(0, tab) to line.substring(tab + 1)
                }
            }
            ?.toMap()
            ?: emptyMap()
}
