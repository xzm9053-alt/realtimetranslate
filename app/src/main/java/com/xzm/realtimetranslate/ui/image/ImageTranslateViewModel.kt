package com.xzm.realtimetranslate.ui.image

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.HistoryEntry
import com.xzm.realtimetranslate.data.HistoryMode
import com.xzm.realtimetranslate.data.HistoryRepository
import com.xzm.realtimetranslate.data.UserSettingsRepository
import com.xzm.realtimetranslate.image.ImageTranslationPipeline
import com.xzm.realtimetranslate.image.ImageTranslationResult
import com.xzm.realtimetranslate.ocr.ScreenTextOcr
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.util.BitmapDecoding
import com.xzm.realtimetranslate.util.ExportTranslator
import com.xzm.realtimetranslate.util.ImageExporter
import com.xzm.realtimetranslate.util.realScreenMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which picker the page was opened with. */
enum class ImageSource { CAMERA, GALLERY }

data class ImageTranslateUiState(
    val source: ImageSource = ImageSource.GALLERY,
    val isWorking: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** True when the current engine has no key, so nothing can be translated. */
    val missingKey: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val sourceText: String = "",
    val translatedText: String = "",
    /** Nothing on the picture was worth translating. */
    val nothingToTranslate: Boolean = false,
) {
    val hasResult: Boolean get() = translatedText.isNotBlank() || nothingToTranslate
    val progressLabel: String get() = if (total > 0) "$done/$total" else ""
}

class ImageTranslateViewModel(
    app: Application,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val historyRepository: HistoryRepository,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ImageTranslateUiState())
    val state: StateFlow<ImageTranslateUiState> = _state.asStateFlow()

    /** Kept across pictures: building the ML Kit recognizers is the expensive part. */
    private val pipeline = ImageTranslationPipeline(ScreenTextOcr())

    private var original: Bitmap? = null
    private var translated: Bitmap? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _state.update { it.copy(missingKey = !apiKeyStore.hasKeyFor(settings.translationEngine)) }
        }
    }

    fun setSource(source: ImageSource) {
        _state.update { it.copy(source = source) }
    }

    fun refreshKeyState() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _state.update { it.copy(missingKey = !apiKeyStore.hasKeyFor(settings.translationEngine)) }
        }
    }

    /** Camera output goes through a FileProvider uri; gallery picks come from the photo picker. */
    fun translate(uri: Uri) {
        if (_state.value.isWorking) return
        _state.update {
            it.copy(
                isWorking = true,
                error = null,
                message = null,
                done = 0,
                total = 0,
                sourceText = "",
                translatedText = "",
                nothingToTranslate = false,
            )
        }
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val settings = settingsRepository.settings.first()
                if (!apiKeyStore.hasKeyFor(settings.translationEngine)) {
                    _state.update {
                        it.copy(
                            isWorking = false,
                            missingKey = true,
                            error = context.getString(R.string.text_need_key),
                        )
                    }
                    return@launch
                }

                val bitmap = withContext(Dispatchers.IO) {
                    BitmapDecoding.decode(context, uri)
                }
                if (bitmap == null) {
                    _state.update {
                        it.copy(isWorking = false, error = context.getString(R.string.image_decode_failed))
                    }
                    return@launch
                }
                original?.recycle()
                translated?.recycle()
                original = bitmap
                translated = null

                val engine = TranslationEngineFactory.create(
                    settings,
                    apiKeyStore,
                    liveSettings = { settingsRepository.settings.first() },
                )
                val (screenWidth, _, density) = context.realScreenMetrics()
                val result = pipeline.run(
                    original = bitmap,
                    settings = settings,
                    engine = engine,
                    screenDensity = density,
                    screenWidthPx = screenWidth,
                    onProgress = { done, total ->
                        _state.update { it.copy(done = done, total = total) }
                    },
                )
                translated = result.translated
                _state.update {
                    it.copy(
                        isWorking = false,
                        sourceText = result.sourceText,
                        translatedText = result.translatedText,
                        nothingToTranslate = result.isEmpty,
                        message = if (result.isEmpty) {
                            context.getString(R.string.image_nothing_to_translate)
                        } else {
                            null
                        },
                    )
                }
                recordHistory(result)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e(TAG, "translate failed", t)
                _state.update {
                    it.copy(
                        isWorking = false,
                        error = t.message ?: getApplication<Application>().getString(R.string.image_failed),
                    )
                }
            }
        }
    }

    /** The painted picture, for preview and sharing. Null until a run finishes. */
    fun translatedBitmap(): Bitmap? = translated

    fun originalBitmap(): Bitmap? = original

    fun saveToGallery() {
        val bitmap = translated ?: return
        viewModelScope.launch {
            val context = getApplication<Application>()
            val name = ExportTranslator.fileName(ext = "jpg", label = "翻译")
            val result = withContext(Dispatchers.IO) {
                ImageExporter.saveToGallery(context, bitmap, name)
            }
            _state.update {
                result.fold(
                    onSuccess = { path ->
                        it.copy(message = context.getString(R.string.image_saved, path))
                    },
                    onFailure = { t ->
                        it.copy(error = t.message ?: context.getString(R.string.image_save_failed))
                    },
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private suspend fun recordHistory(result: ImageTranslationResult) {
        if (result.sourceText.isBlank() && result.translatedText.isBlank()) return
        val settings = settingsRepository.settings.first()
        historyRepository.append(
            HistoryEntry(System.currentTimeMillis(), result.sourceText, result.translatedText),
            maxEntries = if (settings.historyMode == HistoryMode.SAVE_ALL) null
            else settings.historyLimit,
        )
    }

    override fun onCleared() {
        super.onCleared()
        pipeline.close()
        original?.recycle()
        translated?.recycle()
        original = null
        translated = null
    }

    companion object {
        private const val TAG = "ImageTranslateVM"
    }
}

class ImageTranslateViewModelFactory(
    private val app: Application,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val historyRepository: HistoryRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ImageTranslateViewModel(app, settingsRepository, apiKeyStore, historyRepository) as T
    }
}
