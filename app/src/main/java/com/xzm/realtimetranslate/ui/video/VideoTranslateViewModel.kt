package com.xzm.realtimetranslate.ui.video

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.SupportedLanguages
import com.xzm.realtimetranslate.data.UserSettingsRepository
import com.xzm.realtimetranslate.data.VideoSubtitleMode
import com.xzm.realtimetranslate.service.SessionBus
import com.xzm.realtimetranslate.translate.AsrEngine
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import com.xzm.realtimetranslate.util.ExportTranslator
import com.xzm.realtimetranslate.util.ModelDownloader
import com.xzm.realtimetranslate.util.SrtFormatter
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
) {
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
            val settings = settingsRepository.settings.first()
            val target = settings.targetLanguageCode.takeIf { code ->
                SupportedLanguages.targetOptions.any { it.code == code }
            } ?: "zh-Hans"
            _state.update {
                it.copy(targetCode = target, subtitleMode = settings.videoSubtitleMode)
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
                if (!apiKeyStore.hasKeyFor(settings.translationEngine)) {
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
                if (!ready) {
                    _state.update {
                        it.copy(
                            isRunning = false,
                            stage = null,
                            error = getApplication<Application>().getString(R.string.video_need_model),
                        )
                    }
                    return@launch
                }

                val paths = modelManager.paths
                val modelDir = paths.senseVoiceModel.parentFile
                    ?: throw IllegalStateException("模型目录不可用")
                val engine = TranslationEngineFactory.create(
                    settings,
                    apiKeyStore,
                    liveSettings = { settingsRepository.settings.first() },
                )
                val pipeline = VideoTranslationPipeline(
                    context = getApplication(),
                    modelDir = modelDir,
                    vadModelPath = paths.sileroVad.absolutePath,
                )

                val result = pipeline.run(
                    uri = uri,
                    language = AsrEngine.senseVoiceLanguageFor(snapshot.sourceCode),
                    translate = { text ->
                        engine.translate(text, snapshot.sourceCode, snapshot.targetCode).lastOrNull()
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
                        failedTranslations = result.failedTranslations,
                        message = if (cues.isEmpty()) {
                            getApplication<Application>().getString(R.string.video_no_speech)
                        } else {
                            getApplication<Application>().getString(R.string.video_done, cues.size)
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "video translation failed", e)
                _state.update {
                    it.copy(
                        isRunning = false,
                        stage = null,
                        error = getApplication<Application>().getString(
                            R.string.text_translate_failed,
                            e.message ?: e.javaClass.simpleName,
                        ),
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
