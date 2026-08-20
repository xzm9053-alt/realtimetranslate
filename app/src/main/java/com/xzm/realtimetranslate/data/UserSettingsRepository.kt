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
        val modelMirrorUrl = stringPreferencesKey("model_mirror_url")
        val huggingfaceToken = stringPreferencesKey("huggingface_token")
        val historyMode = stringPreferencesKey("history_mode")
        val historyLimit = intPreferencesKey("history_limit")
        val vadMinSilenceSec = floatPreferencesKey("vad_min_silence_sec")
        val vadMaxSpeechSec = floatPreferencesKey("vad_max_speech_sec")
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
            prefs[Keys.modelMirrorUrl] = next.modelMirrorUrl
            prefs[Keys.huggingfaceToken] = next.huggingfaceToken
            prefs[Keys.historyMode] = next.historyMode.name
            prefs[Keys.historyLimit] = next.historyLimit
            prefs[Keys.vadMinSilenceSec] = next.vadMinSilenceSec
            prefs[Keys.vadMaxSpeechSec] = next.vadMaxSpeechSec
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
        modelMirrorUrl = this[Keys.modelMirrorUrl] ?: UserSettings.Defaults.MODEL_MIRROR_URL,
        huggingfaceToken = this[Keys.huggingfaceToken] ?: UserSettings.Defaults.HF_TOKEN,
        historyMode = HistoryMode.fromStorage(this[Keys.historyMode]),
        historyLimit = this[Keys.historyLimit] ?: UserSettings.Defaults.HISTORY_LIMIT,
        vadMinSilenceSec = this[Keys.vadMinSilenceSec] ?: UserSettings.Defaults.VAD_MIN_SILENCE_SEC,
        vadMaxSpeechSec = this[Keys.vadMaxSpeechSec] ?: UserSettings.Defaults.VAD_MAX_SPEECH_SEC,
    )

    /** Migrate the pre-3-option boolean: true→BOTH, false/absent→TRANSLATION. */
    private fun legacyDisplayMode(p: Preferences): SubtitleDisplayMode =
        when (p[Keys.legacyBilingual]) {
            true -> SubtitleDisplayMode.BOTH
            else -> SubtitleDisplayMode.TRANSLATION
        }
}
