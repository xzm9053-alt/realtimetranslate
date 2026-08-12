package com.xzm.realtimetranslate.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.HistoryEntry
import com.xzm.realtimetranslate.data.HistoryRepository
import com.xzm.realtimetranslate.util.ExportTranslator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryViewModel(
    app: Application,
    private val repository: HistoryRepository,
) : AndroidViewModel(app) {

    val entries: StateFlow<List<HistoryEntry>> = repository.entries

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun saveEntryAsText(entry: HistoryEntry) {
        viewModelScope.launch {
            val at = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.stoppedAtEpochMs),
                ZoneId.systemDefault(),
            )
            val name = ExportTranslator.fileName(at, "txt")
            val content = ExportTranslator.buildText(entry.inputFull, entry.outputFull, at)
            val result = ExportTranslator.saveToDownloads(
                getApplication(),
                name,
                content,
                "text/plain",
            )
            _message.value = result.fold(
                onSuccess = { path ->
                    getApplication<Application>().getString(R.string.subtitle_export_ok, path)
                },
                onFailure = { e ->
                    getApplication<Application>().getString(
                        R.string.subtitle_export_fail,
                        e.message.orEmpty(),
                    )
                },
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _message.value = getApplication<Application>().getString(R.string.history_cleared)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

class HistoryViewModelFactory(
    private val app: Application,
    private val repository: HistoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HistoryViewModel(app, repository) as T
    }
}
