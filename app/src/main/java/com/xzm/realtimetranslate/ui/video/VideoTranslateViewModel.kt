package com.xzm.realtimetranslate.ui.video

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.LanguageOption
import com.xzm.realtimetranslate.data.LocalAsrModelCatalog
import com.xzm.realtimetranslate.data.LocalAsrModelPreset
import com.xzm.realtimetranslate.data.SupportedLanguages
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.data.UserSettingsRepository
import com.xzm.realtimetranslate.data.VideoAsrEngine
import com.xzm.realtimetranslate.data.VideoSubtitleMode
import com.xzm.realtimetranslate.data.VideoSubtitleStyle
import com.xzm.realtimetranslate.service.SessionBus
import com.xzm.realtimetranslate.translate.AsrEngine
import com.xzm.realtimetranslate.translate.PassthroughTranslationEngine
import com.xzm.realtimetranslate.translate.TranslationEngine
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import com.xzm.realtimetranslate.util.ExportTranslator
import com.xzm.realtimetranslate.util.ModelDownloader
import com.xzm.realtimetranslate.util.SrtFormatter
import com.xzm.realtimetranslate.video.CloudBatchRecognizer
import com.xzm.realtimetranslate.video.LocalBatchRecognizer
import com.xzm.realtimetranslate.video.SubtitleBurnIn
import com.xzm.realtimetranslate.video.VideoCue
import com.xzm.realtimetranslate.video.VideoTranslationPipeline
import com.xzm.realtimetranslate.video.toVideoCue
import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

data class VideoTranslateUiState(
    val uri: String? = null,
    val displayName: String = "",
    val sourceCode: String = "auto",
    val targetCode: String = "zh-Hans",
    val recognizeOnly: Boolean = false,
    /** 视频字幕用哪个识别引擎；默认本地 SenseVoice。 */
    val asrEngine: VideoAsrEngine = VideoAsrEngine.LOCAL,
    val isRunning: Boolean = false,
    val stage: VideoTranslationPipeline.Stage? = null,
    val progress: Float = 0f,
    val segmentsDone: Int = 0,
    val cues: List<VideoCue> = emptyList(),
    val durationMs: Long = 0L,
    val failedTranslations: Int = 0,
    val error: String? = null,
    val message: String? = null,
    /** 压制字幕：把译文烧进画面，另存一个视频文件。 */
    val isBurning: Boolean = false,
    val burnProgress: Float = 0f,
    /** 单语 / 双语：压制、预览、SRT 导出三处共用同一个值。 */
    val subtitleMode: VideoSubtitleMode = VideoSubtitleMode.TRANSLATION,
    /** 字幕外观：同样三处共用，改完立即可见。 */
    val subtitleStyle: VideoSubtitleStyle = VideoSubtitleStyle(),

    // ===== 双引擎随全局与独立覆写 =====
    /** 全局设置中的翻译引擎 */
    val globalTranslationEngine: TranslationEngineType = TranslationEngineType.MICROSOFT,
    /** 当前视频任务的覆写翻译引擎；为 null 时表示跟随全局设置 */
    val overrideTranslationEngine: TranslationEngineType? = null,

    /** 全局设置中的本地 ASR 模型 ID */
    val globalLocalAsrModelId: String = LocalAsrModelCatalog.DEFAULT_MODEL_ID,
    /** 当前视频任务的覆写本地 ASR 模型 ID；为 null 时表示跟随全局设置 */
    val overrideLocalAsrModelId: String? = null,

    /** 当前根据有效识别引擎动态计算出的可用源语言列表 */
    val availableSourceOptions: List<LanguageOption> = SupportedLanguages.sourceOptions,
) {
    /** 实际生效的翻译引擎 */
    val effectiveTranslationEngine: TranslationEngineType
        get() = overrideTranslationEngine ?: globalTranslationEngine

    /** 实际生效的本地 ASR 模型 ID */
    val effectiveLocalAsrModelId: String
        get() = overrideLocalAsrModelId ?: globalLocalAsrModelId

    /** 实际生效的本地 ASR 预设信息 */
    val effectiveAsrPreset: LocalAsrModelPreset
        get() = LocalAsrModelCatalog.getPresetById(effectiveLocalAsrModelId)

    /** 是否处于独立覆写状态（任一引擎不随全局） */
    val isOverridden: Boolean
        get() = overrideTranslationEngine != null || overrideLocalAsrModelId != null

    val hasVideo: Boolean get() = uri != null
    val canStart: Boolean get() = hasVideo && !isRunning
    val canExport: Boolean get() = cues.isNotEmpty() && !isRunning && !isBurning
    val canBurn: Boolean get() = hasVideo && cues.isNotEmpty() && !isRunning && !isBurning
}

class VideoTranslateViewModel(
    app: Application,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val modelManager: ModelDownloader,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(VideoTranslateUiState())
    val state: StateFlow<VideoTranslateUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val target = settings.targetLanguageCode.takeIf { code ->
                    SupportedLanguages.targetOptions.any { it.code == code }
                } ?: "zh-Hans"
                _state.update { current ->
                    val globalEngine = settings.translationEngine
                    val globalAsrId = settings.localAsrModel
                    val effectiveAsrId = current.overrideLocalAsrModelId ?: globalAsrId
                    val effectivePreset = LocalAsrModelCatalog.getPresetById(effectiveAsrId)
                    val sourceOpts = if (current.asrEngine == VideoAsrEngine.CLOUD) {
                        SupportedLanguages.sourceOptions
                    } else {
                        SupportedLanguages.filterSourceOptionsForModel(effectivePreset)
                    }
                    val sanitizedSource = SupportedLanguages.sanitizeSourceLanguage(current.sourceCode, sourceOpts)
                    current.copy(
                        targetCode = target,
                        subtitleMode = settings.videoSubtitleMode,
                        asrEngine = settings.videoAsrEngine,
                        subtitleStyle = settings.videoSubtitleStyle,
                        globalTranslationEngine = globalEngine,
                        globalLocalAsrModelId = globalAsrId,
                        availableSourceOptions = sourceOpts,
                        sourceCode = sanitizedSource,
                    )
                }
            }
        }
    }

    fun setVideo(uri: Uri, displayName: String) {
        // The picker grants a read permission for this process; persisting it lets the
        // saved URI keep working after the app is restarted.
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        cancel()
        _state.update {
            it.copy(
                uri = uri.toString(),
                displayName = displayName,
                cues = emptyList(),
                durationMs = 0L,
                progress = 0f,
                segmentsDone = 0,
                failedTranslations = 0,
                error = null,
                message = null,
            )
        }
    }

    fun setSourceLanguage(code: String) {
        _state.update { it.copy(sourceCode = code) }
    }

    fun setTargetLanguage(code: String) {
        _state.update { it.copy(targetCode = code) }
    }

    /** 字幕单语/双语。存盘，下次进来还是这个选择；已有的字幕不重翻，
     * 但预览与导出立即跟着变。 */
    fun setSubtitleMode(mode: VideoSubtitleMode) {
        _state.update { it.copy(subtitleMode = mode) }
        viewModelScope.launch { settingsRepository.update { it.copy(videoSubtitleMode = mode) } }
    }

    /** 仅识别不翻译：只提取语音生成字幕，不调用大模型翻译，完全离线免 API Key */
    fun setRecognizeOnly(enabled: Boolean) {
        _state.update { it.copy(recognizeOnly = enabled) }
    }

    /** 字幕外观改动：存盘 + 立即刷新预览（不重跑识别）。 */
    fun setSubtitleStyle(style: VideoSubtitleStyle) {
        _state.update { it.copy(subtitleStyle = style) }
        viewModelScope.launch { settingsRepository.update { it.copy(videoSubtitleStyle = style) } }
    }

    fun resetSubtitleStyle() = setSubtitleStyle(VideoSubtitleStyle())

    /** 切换视频字幕的语音识别大类（本地离线 / 云端通用）。下次开始提取时生效。 */
    fun setAsrEngine(engine: VideoAsrEngine) {
        _state.update { current ->
            val sourceOpts = if (engine == VideoAsrEngine.CLOUD) {
                SupportedLanguages.sourceOptions
            } else {
                SupportedLanguages.filterSourceOptionsForModel(current.effectiveAsrPreset)
            }
            val source = SupportedLanguages.sanitizeSourceLanguage(current.sourceCode, sourceOpts)
            current.copy(
                asrEngine = engine,
                sourceCode = source,
                availableSourceOptions = sourceOpts,
            )
        }
        viewModelScope.launch { settingsRepository.update { it.copy(videoAsrEngine = engine) } }
    }

    /** 覆写当前视频翻译任务的翻译大模型引擎（null 表示恢复跟随全局） */
    fun setOverrideTranslationEngine(engine: TranslationEngineType?) {
        _state.update { it.copy(overrideTranslationEngine = engine) }
    }

    /** 覆写当前视频翻译任务的本地 ASR 识别模型（null 表示恢复跟随全局） */
    fun setOverrideLocalAsrModel(modelId: String?) {
        _state.update { current ->
            val effectiveAsrId = modelId ?: current.globalLocalAsrModelId
            val effectivePreset = LocalAsrModelCatalog.getPresetById(effectiveAsrId)
            val sourceOpts = if (current.asrEngine == VideoAsrEngine.CLOUD) {
                SupportedLanguages.sourceOptions
            } else {
                SupportedLanguages.filterSourceOptionsForModel(effectivePreset)
            }
            val sanitizedSource = SupportedLanguages.sanitizeSourceLanguage(current.sourceCode, sourceOpts)
            current.copy(
                overrideLocalAsrModelId = modelId,
                availableSourceOptions = sourceOpts,
                sourceCode = sanitizedSource,
            )
        }
    }

    /** 一键重置为跟随全局设置 */
    fun resetOverrides() {
        _state.update { current ->
            val effectivePreset = LocalAsrModelCatalog.getPresetById(current.globalLocalAsrModelId)
            val sourceOpts = if (current.asrEngine == VideoAsrEngine.CLOUD) {
                SupportedLanguages.sourceOptions
            } else {
                SupportedLanguages.filterSourceOptionsForModel(effectivePreset)
            }
            val sanitizedSource = SupportedLanguages.sanitizeSourceLanguage(current.sourceCode, sourceOpts)
            current.copy(
                overrideTranslationEngine = null,
                overrideLocalAsrModelId = null,
                availableSourceOptions = sourceOpts,
                sourceCode = sanitizedSource,
            )
        }
    }

    fun start() {
        val snapshot = _state.value
        val uri = snapshot.uri?.let(Uri::parse) ?: return
        if (snapshot.isRunning) return

        val sessionStatus = SessionBus.state.value.status
        if (sessionStatus == SessionBus.Status.Running || sessionStatus == SessionBus.Status.Starting) {
            _state.update {
                it.copy(error = getApplication<Application>().getString(R.string.video_need_stop_session))
            }
            return
        }

        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    isRunning = true,
                    error = null,
                    message = null,
                    cues = emptyList(),
                    progress = 0f,
                    segmentsDone = 0,
                    failedTranslations = 0,
                    stage = VideoTranslationPipeline.Stage.EXTRACTING,
                )
            }
            try {
                val settings = settingsRepository.settings.first()
                val targetEngine = snapshot.effectiveTranslationEngine
                val isRecognizeOnly = snapshot.recognizeOnly || targetEngine == TranslationEngineType.NONE
                if (!isRecognizeOnly && !apiKeyStore.hasKeyFor(targetEngine)) {
                    _state.update {
                        it.copy(
                            isRunning = false,
                            stage = null,
                            error = getApplication<Application>().getString(R.string.text_need_key),
                        )
                    }
                    return@launch
                }

                val ready = withContext(Dispatchers.IO) {
                    modelManager.seedFromAssetsIfNeeded() || modelManager.paths.isReady()
                }
                if (!ready && snapshot.asrEngine == VideoAsrEngine.LOCAL) {
                    _state.update {
                        it.copy(
                            isRunning = false,
                            stage = null,
                            error = getApplication<Application>().getString(R.string.video_need_model),
                        )
                    }
                    return@launch
                }

                if (snapshot.asrEngine == VideoAsrEngine.CLOUD) {
                    val missing = cloudAsrMissingReason(settings)
                    if (missing != null) {
                        _state.update {
                            it.copy(isRunning = false, stage = null, error = missing)
                        }
                        return@launch
                    }
                }

                val paths = modelManager.paths
                val modelDir = paths.senseVoiceModel.parentFile
                    ?: throw IllegalStateException("模型目录不可用")
                val engine: TranslationEngine = if (isRecognizeOnly) {
                    PassthroughTranslationEngine
                } else {
                    TranslationEngineFactory.create(
                        settings = settings.copy(translationEngine = targetEngine),
                        keys = apiKeyStore,
                        liveSettings = { settingsRepository.settings.first().copy(translationEngine = targetEngine) },
                        liveGlossaryPrompt = { (getApplication<Application>() as LiveTranslateApp).glossaryRepository.getEnabledTermsPrompt() },
                    )
                }

                val sourceCode = snapshot.sourceCode
                val useCloud = snapshot.asrEngine == VideoAsrEngine.CLOUD
                val effectivePreset = snapshot.effectiveAsrPreset
                val probeResult = withContext(Dispatchers.IO) {
                    LocalAsrModelCatalog.probe(getApplication(), effectivePreset.id)
                }

                val pipeline = VideoTranslationPipeline(
                    context = getApplication(),
                    vadModelPath = paths.sileroVad.absolutePath,
                    recognizerFactory = {
                        if (useCloud) {
                            CloudBatchRecognizer(
                                baseUrl = settings.genericAsrBaseUrl,
                                apiKey = apiKeyStore.getGenericAsrKey(),
                                model = settings.genericAsrModel,
                                language = sourceCode,
                            )
                        } else {
                            LocalBatchRecognizer(
                                preset = effectivePreset,
                                probeResult = probeResult,
                                fallbackModelDir = modelDir,
                                language = sourceCode,
                            )
                        }
                    },
                )

                val result = pipeline.run(
                    uri = uri,
                    translate = { text ->
                        if (isRecognizeOnly) {
                            text
                        } else {
                            engine.translate(text, snapshot.sourceCode, snapshot.targetCode).lastOrNull()
                        }
                    },
                    onProgress = { stage, fraction ->
                        _state.update {
                            it.copy(
                                stage = stage,
                                progress = if (stage == VideoTranslationPipeline.Stage.EXTRACTING) {
                                    fraction * EXTRACT_WEIGHT
                                } else {
                                    EXTRACT_WEIGHT + fraction * (1f - EXTRACT_WEIGHT)
                                },
                            )
                        }
                    },
                    onSegment = { count ->
                        _state.update { it.copy(segmentsDone = count) }
                    },
                )

                val cues = result.cues.mapIndexed { index, cue -> cue.toVideoCue(index) }
                _state.update {
                    it.copy(
                        isRunning = false,
                        stage = null,
                        progress = 1f,
                        cues = cues,
                        durationMs = result.durationMs,
                        failedTranslations = if (isRecognizeOnly) 0 else result.failedTranslations,
                        message = if (cues.isEmpty()) {
                            getApplication<Application>().getString(R.string.video_no_speech)
                        } else if (isRecognizeOnly) {
                            "字幕提取完成，共 ${cues.size} 句"
                        } else {
                            getApplication<Application>().getString(R.string.video_done, cues.size)
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "video translation failed", e)
                val rawMsg = e.message ?: e.javaClass.simpleName
                val userFriendlyMsg = if (rawMsg.contains("Failed to instantiate extractor", ignoreCase = true)) {
                    "无法解析视频音频流（格式可能不被支持或权限受限）"
                } else {
                    rawMsg
                }
                val prefix = if (snapshot.recognizeOnly) "字幕提取失败" else "翻译失败"
                _state.update {
                    it.copy(
                        isRunning = false,
                        stage = null,
                        error = "$prefix：$userFriendlyMsg",
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(isRunning = false, stage = null, message = null) }
    }

    fun exportSrt() {
        val cues = _state.value.cues
        if (cues.isEmpty()) return
        viewModelScope.launch {
            val name = ExportTranslator.fileName(
                LocalDateTime.now(),
                ext = "srt",
                label = getApplication<Application>().getString(R.string.video_srt_label),
            )
            val result = ExportTranslator.saveToDownloads(
                getApplication(),
                name,
                SrtFormatter.format(cues, _state.value.subtitleMode),
                "application/x-subrip",
            )
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { path ->
                            getApplication<Application>().getString(R.string.video_export_ok, path)
                        },
                        onFailure = { e ->
                            getApplication<Application>().getString(
                                R.string.video_export_fail,
                                e.message.orEmpty(),
                            )
                        },
                    ),
                )
            }
        }
    }

    /**
     * Burns the cues into a new video file next to the source.
     *
     * This re-encodes the whole video, so it is deliberately a separate, explicit
     * action from [exportSrt]: the SRT is instant and lossless, this one takes as long
     * as the video and produces a larger file — but the subtitles travel with it.
     */
    fun burnSubtitles() {
        val snapshot = _state.value
        val uri = snapshot.uri?.let(Uri::parse) ?: return
        if (snapshot.cues.isEmpty() || snapshot.isBurning) return

        viewModelScope.launch {
            _state.update {
                it.copy(isBurning = true, burnProgress = 0f, message = null, error = null)
            }
            val app = getApplication<Application>()
            val target = withContext(Dispatchers.IO) {
                runCatching {
                    val name = ExportTranslator.fileName(
                        LocalDateTime.now(),
                        ext = "mp4",
                        label = app.getString(R.string.video_burn_label),
                    )
                    // Transformer writes through a file path, not a content URI, so the
                    // work happens in the cache and only the finished file is published.
                    val work = File(app.cacheDir, name)
                    if (work.exists()) work.delete()
                    work
                }.getOrNull()
            } ?: return@launch

            val result = SubtitleBurnIn.burn(
                context = app,
                source = uri,
                cues = snapshot.cues,
                outputFile = target,
                mode = snapshot.subtitleMode,
                style = snapshot.subtitleStyle,
                onProgress = { fraction ->
                    if (fraction >= 0f) {
                        _state.update { it.copy(burnProgress = fraction) }
                    }
                },
            )

            val published = result.mapCatching { file ->
                ExportTranslator.publishToDownloads(
                    app,
                    file,
                    // Keep the timestamped name built above: a fixed name would make
                    // MediaStore append " (1)", " (2)" to every later export.
                    file.name,
                    "video/mp4",
                ).getOrThrow()
            }
            // Delete the cache copy either way — a failed publish would otherwise
            // leave a full-length re-encoded video behind on disk.
            target.delete()

            _state.update {
                it.copy(
                    isBurning = false,
                    burnProgress = 0f,
                    message = published.fold(
                        onSuccess = { path -> app.getString(R.string.video_burn_ok, path) },
                        onFailure = { e ->
                            app.getString(R.string.video_burn_fail, e.message.orEmpty())
                        },
                    ),
                )
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    /** 云端识别缺什么就直说，不要让用户开了才发现报错。 */
    private fun cloudAsrMissingReason(settings: UserSettings): String? {
        if (settings.genericAsrBaseUrl.isBlank()) {
            return getApplication<Application>().getString(R.string.video_cloud_asr_need_config)
        }
        if (apiKeyStore.getGenericAsrKey().isBlank()) {
            return getApplication<Application>().getString(R.string.video_cloud_asr_need_key)
        }
        return null
    }

    private companion object {
        const val TAG = "VideoTranslateViewModel"

        /** Decoding is fast; recognition + translation is where the time goes. */
        const val EXTRACT_WEIGHT = 0.25f
    }
}

class VideoTranslateViewModelFactory(
    private val app: Application,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val modelManager: ModelDownloader,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VideoTranslateViewModel(app, settingsRepository, apiKeyStore, modelManager) as T
    }
}
