package com.xzm.realtimetranslate.ui.settings

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.HistoryMode
import com.xzm.realtimetranslate.data.OcrScript
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.data.UserSettingsRepository
import com.xzm.realtimetranslate.translate.DeepSeekTranslationEngine
import com.xzm.realtimetranslate.translate.MicrosoftFreeTranslationEngine
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

    fun saveDeepSeekKey() {
        apiKeyStore.setDeepSeekKey(_deepSeekKey.value.text.trim())
        _testResult.value = "✅"
    }

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
            val result = when (s.translationEngine) {
                TranslationEngineType.DEEPSEEK -> {
                    val key = _deepSeekKey.value.text.trim()
                    if (key.isBlank()) {
                        Result.failure(Exception("DeepSeek API Key 为空"))
                    } else {
                        apiKeyStore.setDeepSeekKey(key)
                        DeepSeekTranslationEngine(
                            apiKey = key,
                            baseUrl = s.deepseekBaseUrl.ifBlank { "https://api.deepseek.com" },
                            model = s.deepseekModel.ifBlank { "deepseek-v4-flash" },
                        ).testConnection(target)
                    }
                }
                TranslationEngineType.MICROSOFT ->
                    MicrosoftFreeTranslationEngine().testConnection(target)
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
