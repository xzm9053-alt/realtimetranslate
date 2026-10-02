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
import com.xzm.realtimetranslate.translate.ModelAvailability
import com.xzm.realtimetranslate.translate.ModelPresets
import com.xzm.realtimetranslate.translate.ModelProbe
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

    // ---- Gemini credential + model fields (drafts) ----
    private val _geminiKey = MutableStateFlow(TextFieldValue(apiKeyStore.getGeminiKey()))
    val geminiKey: StateFlow<TextFieldValue> = _geminiKey.asStateFlow()

    private val _geminiModel = MutableStateFlow(
        TextFieldValue(UserSettings.Defaults.GEMINI_MODEL),
    )
    val geminiModel: StateFlow<TextFieldValue> = _geminiModel.asStateFlow()

    private val _geminiBaseUrl = MutableStateFlow(
        TextFieldValue(UserSettings.Defaults.GEMINI_BASE_URL),
    )
    val geminiBaseUrl: StateFlow<TextFieldValue> = _geminiBaseUrl.asStateFlow()

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

    // ---- 模型可用性检测（联网，结果缓存到 DataStore 供下次启动复用）----
    // ModelProbe 而非 ModelAvailability：可用性是要缓存的，detail（服务端原话）只在
    // 本次会话里给用户看为什么不通，重启后只剩状态、没有原因——可以接受，也是刻意的。
    private val _modelProbes = MutableStateFlow<Map<String, ModelProbe>>(emptyMap())
    val modelProbes: StateFlow<Map<String, ModelProbe>> = _modelProbes.asStateFlow()

    private val _discoveredModels = MutableStateFlow<List<String>>(emptyList())
    val discoveredModels: StateFlow<List<String>> = _discoveredModels.asStateFlow()

    private val _modelsCheckedAt = MutableStateFlow(0L)
    val modelsCheckedAt: StateFlow<Long> = _modelsCheckedAt.asStateFlow()

    private val _checkingModels = MutableStateFlow(false)
    val checkingModels: StateFlow<Boolean> = _checkingModels.asStateFlow()

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
            _geminiModel.value = TextFieldValue(
                s.geminiModel.ifBlank { UserSettings.Defaults.GEMINI_MODEL },
            )
            _geminiBaseUrl.value = TextFieldValue(
                s.geminiBaseUrl.ifBlank { UserSettings.Defaults.GEMINI_BASE_URL },
            )
            _mirrorUrl.value = TextFieldValue(
                s.modelMirrorUrl.ifBlank { UserSettings.Defaults.MODEL_MIRROR_URL },
            )
            _hfToken.value = TextFieldValue(s.huggingfaceToken)
            _modelStatus.value = describeModelStatus()
            _discoveredModels.value = s.discoveredModels
            _modelsCheckedAt.value = s.modelsCheckedAt
            // 缓存里只有状态名，恢复出来的 probe 没有 detail。
            _modelProbes.value = s.modelAvailability.mapNotNull { (model, name) ->
                ModelAvailability.entries.firstOrNull { it.name == name }
                    ?.let { model to ModelProbe(it) }
            }.toMap()
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

    fun setGeminiKey(value: TextFieldValue) {
        _geminiKey.value = value
    }

    fun updateGeminiModel(value: TextFieldValue) {
        _geminiModel.value = value
        update { it.copy(geminiModel = value.text.trim()) }
    }

    fun updateGeminiBaseUrl(value: TextFieldValue) {
        _geminiBaseUrl.value = value
        update { it.copy(geminiBaseUrl = value.text.trim()) }
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
     * The draft text behind each engine's key field. Together with [draftModelFor] this
     * is the one place that still switches on the engine — the drafts are distinct text
     * fields, and there is no dispatch to share with [TranslationEngineFactory].
     */
    private fun draftKeyFor(type: TranslationEngineType): String = when (type) {
        TranslationEngineType.DEEPSEEK -> _deepSeekKey.value.text
        TranslationEngineType.ZHIPU -> _zhipuKey.value.text
        TranslationEngineType.GEMINI -> _geminiKey.value.text
        TranslationEngineType.MICROSOFT, TranslationEngineType.GOOGLE_FREE -> ""
    }.trim()

    /** Same one-place exception as [draftKeyFor], for the model field. */
    private fun draftModelFor(type: TranslationEngineType): String = when (type) {
        TranslationEngineType.DEEPSEEK -> _deepSeekModel.value.text
        TranslationEngineType.ZHIPU -> _zhipuModel.value.text
        TranslationEngineType.GEMINI -> _geminiModel.value.text
        TranslationEngineType.MICROSOFT, TranslationEngineType.GOOGLE_FREE -> ""
    }.trim()

    /**
     * Asks the current engine which of its models are really usable, and caches the
     * verdicts so they survive a restart instead of costing another round of requests.
     *
     * The answer comes from calling each model, not from the provider's published list:
     * Google keeps retired models in its catalogue while every call to one answers 404,
     * which is the exact failure this exists to catch.
     */
    fun checkModels() {
        if (_checkingModels.value) return
        val engineType = settings.value.translationEngine
        val candidates = modelsToProbe(engineType)
        if (candidates.isEmpty()) return
        if (!apiKeyStore.hasKeyFor(engineType)) {
            val label = app.getString(engineType.keyLabelRes!!)
            _testResult.value = "❌ " + app.getString(R.string.msg_need_api_key, label)
            return
        }
        viewModelScope.launch {
            _checkingModels.value = true
            // 与连接测试同理：先把草稿 Key 落盘，否则刚粘贴的 Key 还没生效。
            apiKeyStore.setKeyFor(engineType, draftKeyFor(engineType))
            val s = settingsRepository.settings.first()
            val outcome = runCatching {
                TranslationEngineFactory.checkModels(s, apiKeyStore, candidates)
            }
            outcome.getOrNull()?.let { check ->
                _modelProbes.value = check.probes
                _discoveredModels.value = check.discovered
                _modelsCheckedAt.value = System.currentTimeMillis()
                val probes = _modelProbes.value
                val discovered = _discoveredModels.value
                val checkedAt = _modelsCheckedAt.value
                update {
                    it.copy(
                        discoveredModels = discovered,
                        modelAvailability = probes.mapValues { entry -> entry.value.availability.name },
                        modelsCheckedAt = checkedAt,
                    )
                }
            }
            outcome.exceptionOrNull()?.let { _testResult.value = "❌ 检测失败：${it.message}" }
            _checkingModels.value = false
        }
    }

    /**
     * Everything worth asking about: the picker's list plus whatever model is actually
     * in use, capped so a large provider catalogue cannot turn one tap into dozens of
     * requests. Presets come first, so a cap only ever trims the discovered extras.
     */
    private fun modelsToProbe(type: TranslationEngineType): List<String> {
        val visible = ModelPresets.visible(type, _discoveredModels.value)
        return (visible.take(MAX_MODEL_PROBES) + draftModelFor(type))
            .filter { it.isNotBlank() }
            .distinct()
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

/** Each probe is one request, so one tap must not fan out over a whole catalogue. */
private const val MAX_MODEL_PROBES = 12

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
