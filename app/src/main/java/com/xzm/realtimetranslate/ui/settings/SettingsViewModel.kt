package com.xzm.realtimetranslate.ui.settings

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.HistoryMode
import com.xzm.realtimetranslate.data.OcrScript
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.data.UserSettingsRepository
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import com.xzm.realtimetranslate.util.DownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val app: LiveTranslateApp,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    // ---- DeepSeek credential + model fields (drafts) ----
    private val _deepSeekKey = MutableStateFlow(TextFieldValue(apiKeyStore.getDeepSeekKey()))
    val deepSeekKey: StateFlow<TextFieldValue> = _deepSeekKey.asStateFlow()

    private val _deepSeekModel = MutableStateFlow(
        TextFieldValue(UserSettings.Defaults.DEEPSEEK_MODEL),
    )
    val deepSeekModel: StateFlow<TextFieldValue> = _deepSeekModel.asStateFlow()

    private val _deepSeekBaseUrl = MutableStateFlow(
        TextFieldValue(UserSettings.Defaults.DEEPSEEK_BASE_URL),
    )
    val deepSeekBaseUrl: StateFlow<TextFieldValue> = _deepSeekBaseUrl.asStateFlow()

    // ---- Zhipu GLM credential + model fields (drafts) ----
    private val _zhipuKey = MutableStateFlow(TextFieldValue(apiKeyStore.getZhipuKey()))
    val zhipuKey: StateFlow<TextFieldValue> = _zhipuKey.asStateFlow()

    private val _zhipuModel = MutableStateFlow(
        TextFieldValue(UserSettings.Defaults.ZHIPU_MODEL),
    )
    val zhipuModel: StateFlow<TextFieldValue> = _zhipuModel.asStateFlow()

    private val _zhipuBaseUrl = MutableStateFlow(
        TextFieldValue(UserSettings.Defaults.ZHIPU_BASE_URL),
    )
    val zhipuBaseUrl: StateFlow<TextFieldValue> = _zhipuBaseUrl.asStateFlow()

    // ---- Google Cloud Translation credential (draft) ----
    private val _googleKey = MutableStateFlow(TextFieldValue(apiKeyStore.getGoogleKey()))
    val googleKey: StateFlow<TextFieldValue> = _googleKey.asStateFlow()

    private val _mirrorUrl = MutableStateFlow(
        TextFieldValue(UserSettings.Defaults.MODEL_MIRROR_URL),
    )
    val mirrorUrl: StateFlow<TextFieldValue> = _mirrorUrl.asStateFlow()

    private val _hfToken = MutableStateFlow(TextFieldValue(""))
    val hfToken: StateFlow<TextFieldValue> = _hfToken.asStateFlow()

    private val _modelStatus = MutableStateFlow("")
    val modelStatus: StateFlow<String> = _modelStatus.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            _deepSeekModel.value = TextFieldValue(
                s.deepseekModel.ifBlank { UserSettings.Defaults.DEEPSEEK_MODEL },
            )
            _deepSeekBaseUrl.value = TextFieldValue(
                s.deepseekBaseUrl.ifBlank { UserSettings.Defaults.DEEPSEEK_BASE_URL },
            )
            _zhipuModel.value = TextFieldValue(
                s.zhipuModel.ifBlank { UserSettings.Defaults.ZHIPU_MODEL },
            )
            _zhipuBaseUrl.value = TextFieldValue(
                s.zhipuBaseUrl.ifBlank { UserSettings.Defaults.ZHIPU_BASE_URL },
            )
            _mirrorUrl.value = TextFieldValue(
                s.modelMirrorUrl.ifBlank { UserSettings.Defaults.MODEL_MIRROR_URL },
            )
            _hfToken.value = TextFieldValue(s.huggingfaceToken)
            _modelStatus.value = describeModelStatus()
        }
        // Refresh the status line when the bundled models finish unpacking.
        viewModelScope.launch {
            app.modelManager.seededFlow.collect { if (it) _modelStatus.value = describeModelStatus() }
        }
    }

    fun setDeepSeekKey(value: TextFieldValue) {
        _deepSeekKey.value = value
    }

    fun updateDeepSeekModel(value: TextFieldValue) {
        _deepSeekModel.value = value
        update { it.copy(deepseekModel = value.text.trim()) }
    }

    fun updateDeepSeekBaseUrl(value: TextFieldValue) {
        _deepSeekBaseUrl.value = value
        update { it.copy(deepseekBaseUrl = value.text.trim()) }
    }

    fun setZhipuKey(value: TextFieldValue) {
        _zhipuKey.value = value
    }

    fun updateZhipuModel(value: TextFieldValue) {
        _zhipuModel.value = value
        update { it.copy(zhipuModel = value.text.trim()) }
    }

    fun updateZhipuBaseUrl(value: TextFieldValue) {
        _zhipuBaseUrl.value = value
        update { it.copy(zhipuBaseUrl = value.text.trim()) }
    }

    fun setGoogleKey(value: TextFieldValue) {
        _googleKey.value = value
    }

    /**
     * Global "deep thinking" switch for AI engines. Takes effect on the next sentence —
     * the engines re-read settings per request, so no session restart is needed.
     */
    fun setAiDeepThinking(enabled: Boolean) {
        update { it.copy(aiDeepThinking = enabled) }
    }

    /**
     * Japanese preset. Enabling writes the recommended VAD values into the sliders
     * (they stay adjustable afterwards); disabling only releases the language lock and
     * keeps whatever values are there. Either way it lands on the next session, since
     * the VAD config is fixed when [AsrEngine] is constructed.
     */
    fun updateMirrorUrl(value: TextFieldValue) {
        _mirrorUrl.value = value
        update { it.copy(modelMirrorUrl = value.text.trim()) }
    }

    fun updateHfToken(value: TextFieldValue) {
        _hfToken.value = value
        update { it.copy(huggingfaceToken = value.text.trim()) }
    }

    fun setEngine(type: TranslationEngineType) {
        update { it.copy(translationEngine = type) }
    }

    fun setOcrScript(script: OcrScript) {
        update { it.copy(ocrScript = script) }
    }

    fun setHistoryMode(mode: HistoryMode) {
        // Trim after persisting so surplus entries drop immediately (cap computed
        // from the state we're switching to, since `settings` refreshes async).
        val cap = if (mode == HistoryMode.SAVE_ALL) null else settings.value.historyLimit
        viewModelScope.launch {
            settingsRepository.update { it.copy(historyMode = mode) }
            app.historyRepository.trim(cap)
        }
    }

    fun setHistoryLimit(limit: Int) {
        val capped = limit.coerceIn(
            UserSettings.Defaults.HISTORY_LIMIT_MIN,
            UserSettings.Defaults.HISTORY_LIMIT_MAX,
        )
        val cap = if (settings.value.historyMode == HistoryMode.SAVE_ALL) null else capped
        viewModelScope.launch {
            settingsRepository.update { it.copy(historyLimit = capped) }
            app.historyRepository.trim(cap)
        }
    }

    /** Persists the selected engine's key draft without running a connection test. */
    fun saveApiKey() {
        val engineType = settings.value.translationEngine
        apiKeyStore.setKeyFor(engineType, draftKeyFor(engineType))
        _testResult.value = "✅"
    }

    /**
     * The draft text behind each engine's key field. This is the one place that still
     * switches on the engine — the drafts are three distinct text fields, and there is
     * no dispatch to share with [TranslationEngineFactory].
     */
    private fun draftKeyFor(type: TranslationEngineType): String = when (type) {
        TranslationEngineType.DEEPSEEK -> _deepSeekKey.value.text
        TranslationEngineType.ZHIPU -> _zhipuKey.value.text
        TranslationEngineType.GOOGLE_API -> _googleKey.value.text
        TranslationEngineType.MICROSOFT, TranslationEngineType.GOOGLE_FREE -> ""
    }.trim()

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun resetSubtitleAppearance() {
        viewModelScope.launch { settingsRepository.resetSubtitleAppearance() }
    }

    fun refreshModelStatus() {
        viewModelScope.launch { _modelStatus.value = describeModelStatus() }
    }

    fun downloadModels() {
        if (_downloading.value) return
        viewModelScope.launch {
            _downloading.value = true
            _downloadProgress.value = 0f
            _modelStatus.value = "下载中…"
            val manager = app.modelManager
            val s = settingsRepository.settings.first()
            val result = manager.downloadAll(
                mirrorBaseUrl = s.modelMirrorUrl,
                huggingfaceToken = s.huggingfaceToken.ifBlank { null },
            ) { p: DownloadProgress ->
                _downloadProgress.value = if (p.overall < 0f) _downloadProgress.value else p.overall
                _modelStatus.value = p.label
            }
            _downloading.value = false
            _modelStatus.value = result.fold(
                onSuccess = { "✅ 模型下载完成（${manager.paths.totalMb} MB）" },
                onFailure = { "❌ 下载失败：${it.message}" },
            )
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _testing.value = true
            _testResult.value = "测试中…"
            val s = settingsRepository.settings.first()
            val target = s.targetLanguageCode.ifBlank { "zh-Hans" }
            val engineType = s.translationEngine
            // 先把输入框里的草稿 Key 落盘再测，否则用户刚粘贴的 Key 还没生效。
            if (engineType.requiresApiKey) {
                apiKeyStore.setKeyFor(engineType, draftKeyFor(engineType))
            }
            val result = if (apiKeyStore.hasKeyFor(engineType)) {
                // 分派统一走 Factory，这里不再重复 when(engineType)。
                TranslationEngineFactory.testConnection(s, apiKeyStore, target)
            } else {
                val label = app.getString(engineType.keyLabelRes!!)
                Result.failure(Exception(app.getString(R.string.msg_need_api_key, label)))
            }
            _testResult.value = result.fold(
                onSuccess = { "✅ $it" },
                onFailure = { "❌ ${it.message}" },
            )
            _testing.value = false
        }
    }

    private fun describeModelStatus(): String {
        val manager = app.modelManager
        val p = manager.paths
        return when {
            p.isReady() -> "✅ 已就绪（${p.totalMb} MB）"
            manager.assetsBundled() -> "模型已内置，首次使用自动解压…"
            p.senseVoiceModel.isFile || p.tokens.isFile || p.sileroVad.isFile ->
                "⚠️ 模型不完整，请重新下载修复"
            else -> "未就绪，可在下方下载"
        }
    }
}

class SettingsViewModelFactory(
    private val app: LiveTranslateApp,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(app, settingsRepository, apiKeyStore) as T
    }
}
