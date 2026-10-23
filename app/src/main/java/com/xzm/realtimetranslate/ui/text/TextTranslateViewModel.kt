package com.xzm.realtimetranslate.ui.text

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.HistoryEntry
import com.xzm.realtimetranslate.data.HistoryRepository
import com.xzm.realtimetranslate.data.SupportedLanguages
import com.xzm.realtimetranslate.data.UserSettingsRepository
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import com.xzm.realtimetranslate.util.TextChunker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

data class TextTranslateUiState(
    val sourceCode: String = "auto",
    val targetCode: String = "zh-Hans",
    val input: String = "",
    val output: String = "",
    val isTranslating: Boolean = false,
    /** 1-based index of the chunk being translated; 0 when idle. */
    val chunkIndex: Int = 0,
    val chunkCount: Int = 0,
    val error: String? = null,
    val missingKey: Boolean = false,
    val savedToHistory: Boolean = false,
) {
    val canTranslate: Boolean get() = input.isNotBlank() && !isTranslating
}

class TextTranslateViewModel(
    app: Application,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val historyRepository: HistoryRepository,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(TextTranslateUiState())
    val state: StateFlow<TextTranslateUiState> = _state.asStateFlow()

    private var job: Job? = null
    private val languageSeeded = AtomicBoolean(false)

    init {
        // Seed the pickers from the subtitle page's languages, once. Later settings
        // edits on other pages must not yank the language out from under the user.
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!languageSeeded.compareAndSet(false, true)) return@launch
            val target = settings.targetLanguageCode.takeIf { code ->
                SupportedLanguages.targetOptions.any { it.code == code }
            } ?: "zh-Hans"
            val source = settings.sourceLanguageCode.takeIf { code ->
                SupportedLanguages.sourceOptions.any { it.code == code }
            } ?: "auto"
            _state.update { it.copy(sourceCode = source, targetCode = target) }
        }
    }

    fun setSourceLanguage(code: String) {
        _state.update { it.copy(sourceCode = code) }
    }

    fun setTargetLanguage(code: String) {
        _state.update { it.copy(targetCode = code) }
    }

    fun setInput(text: String) {
        _state.update { it.copy(input = text, savedToHistory = false) }
    }

    fun translate() {
        val snapshot = _state.value
        val text = snapshot.input.trim()
        if (text.isEmpty() || snapshot.isTranslating) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    isTranslating = true,
                    output = "",
                    error = null,
                    missingKey = false,
                    savedToHistory = false,
                    chunkIndex = 0,
                    chunkCount = 0,
                )
            }
            try {
                val settings = settingsRepository.settings.first()
                if (!apiKeyStore.hasKeyFor(settings.translationEngine)) {
                    _state.update { it.copy(isTranslating = false, missingKey = true) }
                    return@launch
                }
                val engine = TranslationEngineFactory.create(settings, apiKeyStore)
                val chunks = TextChunker.split(text)
                _state.update { it.copy(chunkCount = chunks.size) }
                val finished = StringBuilder()
                chunks.forEachIndexed { index, chunk ->
                    currentCoroutineContext().ensureActive()
                    _state.update { it.copy(chunkIndex = index + 1) }
                    var latest = ""
                    withTimeout(TRANSLATE_TIMEOUT_MS) {
                        engine.translate(chunk, snapshot.sourceCode, snapshot.targetCode)
                            .collect { partial ->
                                latest = partial
                                _state.update {
                                    it.copy(output = finished.toString() + partial)
                                }
                            }
                    }
                    if (latest.isBlank()) {
                        error(getApplication<Application>().getString(R.string.text_empty_result))
                    }
                    if (finished.isNotEmpty()) finished.append('\n')
                    finished.append(latest)
                    _state.update { it.copy(output = finished.toString()) }
                }
                _state.update {
                    it.copy(isTranslating = false, chunkIndex = 0, chunkCount = 0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(isTranslating = false, error = e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(isTranslating = false, chunkIndex = 0, chunkCount = 0) }
    }

    fun clearInput() {
        cancel()
        _state.update {
            it.copy(input = "", output = "", error = null, missingKey = false, savedToHistory = false)
        }
    }

    fun saveToHistory() {
        val snapshot = _state.value
        val output = snapshot.output.trim()
        if (output.isEmpty()) return
        historyRepository.append(
            HistoryEntry(
                stoppedAtEpochMs = System.currentTimeMillis(),
                inputFull = snapshot.input.trim(),
                outputFull = output,
            ),
        )
        _state.update { it.copy(savedToHistory = true) }
    }

    fun dismissMessages() {
        _state.update { it.copy(error = null, missingKey = false) }
    }

    private companion object {
        const val TRANSLATE_TIMEOUT_MS = 60_000L
    }
}

class TextTranslateViewModelFactory(
    private val app: Application,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val historyRepository: HistoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TextTranslateViewModel(app, settingsRepository, apiKeyStore, historyRepository) as T
    }
}
