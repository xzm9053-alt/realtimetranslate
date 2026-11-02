package com.xzm.realtimetranslate.ui.glossary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.data.GlossaryRepository
import com.xzm.realtimetranslate.data.GlossaryTerm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlossaryViewModel(
    app: Application,
    private val repository: GlossaryRepository,
) : AndroidViewModel(app) {

    val entries: StateFlow<List<GlossaryTerm>> = repository.entries

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun add(source: String, target: String, note: String = "") {
        viewModelScope.launch {
            repository.add(source, target, note)
            _message.value = "已添加术语「${source.trim()}」"
        }
    }

    fun update(term: GlossaryTerm) {
        viewModelScope.launch {
            repository.update(term)
            _message.value = "已修改术语「${term.source}」"
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _message.value = "已删除术语"
        }
    }

    fun toggle(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggle(id, enabled)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _message.value = "已清空所有术语"
        }
    }

    fun importText(content: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val result = repository.importText(content)
            result.onSuccess { count ->
                _message.value = "成功导入 $count 条术语"
                onComplete(count)
            }.onFailure { e ->
                _message.value = "导入失败：${e.message}"
            }
        }
    }

    fun exportTsv(): String = repository.exportTsv()

    fun clearMessage() {
        _message.value = null
    }
}

class GlossaryViewModelFactory(
    private val app: Application,
    private val repository: GlossaryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GlossaryViewModel(app, repository) as T
    }
}
