package com.xzm.realtimetranslate.ui.document

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.HistoryEntry
import com.xzm.realtimetranslate.data.HistoryRepository
import com.xzm.realtimetranslate.data.UserSettingsRepository
import com.xzm.realtimetranslate.document.DocxTranslator
import com.xzm.realtimetranslate.document.DocumentInfo
import com.xzm.realtimetranslate.document.DocumentTranslationConfig
import com.xzm.realtimetranslate.document.DocumentTextExtractors
import com.xzm.realtimetranslate.document.DocumentType
import com.xzm.realtimetranslate.document.PdfTranslator
import com.xzm.realtimetranslate.ocr.ScreenTextOcr
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.util.DocumentExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class DocumentTranslateUiState(
    val documentInfo: DocumentInfo? = null,
    val isWorking: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val progressDescription: String = "",
    val bilingualAlternating: Boolean = true,
    val missingKey: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val resultUri: Uri? = null,
    val resultDisplayPath: String? = null,
) {
    val hasDocument: Boolean get() = documentInfo != null
    val hasResult: Boolean get() = resultDisplayPath != null
    val progressLabel: String get() = if (total > 0) "$done/$total" else ""
}

class DocumentTranslateViewModel(
    app: Application,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val historyRepository: HistoryRepository,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(DocumentTranslateUiState())
    val state: StateFlow<DocumentTranslateUiState> = _state.asStateFlow()

    private val pdfTranslator = PdfTranslator(ScreenTextOcr())

    init {
        refreshKeyState()
    }

    fun refreshKeyState() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _state.update { it.copy(missingKey = !apiKeyStore.hasKeyFor(settings.translationEngine)) }
        }
    }

    fun setBilingualAlternating(enabled: Boolean) {
        _state.update { it.copy(bilingualAlternating = enabled) }
    }

    fun selectDocument(uri: Uri, name: String, sizeBytes: Long, mimeType: String?) {
        val detectedType = when {
            DocumentType.fromFileName(name) != DocumentType.UNKNOWN -> DocumentType.fromFileName(name)
            else -> DocumentType.fromMimeType(mimeType)
        }
        val info = DocumentInfo(
            uri = uri,
            name = name,
            sizeBytes = sizeBytes,
            type = detectedType,
        )
        _state.update {
            it.copy(
                documentInfo = info,
                error = null,
                message = null,
                resultDisplayPath = null,
                done = 0,
                total = 0,
                progressDescription = "",
            )
        }
    }

    fun clearSelection() {
        _state.update {
            it.copy(
                documentInfo = null,
                error = null,
                message = null,
                resultDisplayPath = null,
                done = 0,
                total = 0,
                progressDescription = "",
            )
        }
    }

    fun translate() {
        val info = _state.value.documentInfo ?: return
        if (_state.value.isWorking) return

        viewModelScope.launch {
            val context = getApplication<Application>()
            val settings = settingsRepository.settings.first()

            if (!apiKeyStore.hasKeyFor(settings.translationEngine)) {
                _state.update {
                    it.copy(
                        missingKey = true,
                        error = context.getString(R.string.text_need_key),
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    isWorking = true,
                    error = null,
                    message = null,
                    done = 0,
                    total = 0,
                    progressDescription = "准备中…",
                )
            }

            try {
                val engine = TranslationEngineFactory.create(
                    settings,
                    apiKeyStore,
                    liveSettings = { settingsRepository.settings.first() },
                )

                val resultExport = withContext(Dispatchers.IO) {
                    when (info.type) {
                        DocumentType.PDF -> {
                            val uri = info.uri ?: error("无法读取文档路径")
                            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                                ?: error("无法读取 PDF 文件")
                            val tempOut = File.createTempFile("pdf_trans_", ".pdf", context.cacheDir)
                            try {
                                val pdfResult = pfd.use {
                                    FileOutputStream(tempOut).use { fos ->
                                        pdfTranslator.translatePdf(
                                            context = context,
                                            pdfPfd = pfd,
                                            output = fos,
                                            settings = settings,
                                            engine = engine,
                                            config = DocumentTranslationConfig(
                                                bilingualAlternating = _state.value.bilingualAlternating,
                                            ),
                                            onProgress = { done, total ->
                                                _state.update {
                                                    it.copy(
                                                        done = done,
                                                        total = total,
                                                        progressDescription = "正在翻译第 $done / $total 页…",
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                                val outName = DocumentExporter.generateOutputFileName(
                                    originalName = info.name,
                                    bilingual = _state.value.bilingualAlternating,
                                    fallbackExt = "pdf",
                                )
                                val saved = DocumentExporter.saveFileToDownloads(
                                    context = context,
                                    fileName = outName,
                                    mimeType = "application/pdf",
                                    sourceFile = tempOut,
                                ).getOrThrow()

                                recordHistory(
                                    sourceSummary = pdfResult.sourceTextSummary.ifBlank { info.name },
                                    translatedSummary = pdfResult.translatedTextSummary.ifBlank { "PDF 翻译完成（共 ${pdfResult.pageCount} 页）" },
                                )
                                saved
                            } finally {
                                tempOut.delete()
                            }
                        }

                        DocumentType.DOCX -> {
                            val uri = info.uri ?: error("无法读取文档路径")
                            val inputStream = context.contentResolver.openInputStream(uri)
                                ?: error("无法读取 Word 文档")
                            val tempOut = File.createTempFile("docx_trans_", ".docx", context.cacheDir)
                            try {
                                val docxTranslator = DocxTranslator(engine)
                                val docxResult = inputStream.use { ins ->
                                    FileOutputStream(tempOut).use { fos ->
                                        docxTranslator.translateDocx(
                                            input = ins,
                                            output = fos,
                                            sourceLang = settings.sourceLanguageCode,
                                            targetLang = settings.targetLanguageCode,
                                            onProgress = { done, total ->
                                                _state.update {
                                                    it.copy(
                                                        done = done,
                                                        total = total,
                                                        progressDescription = "正在翻译第 $done / $total 段…",
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                                val outName = DocumentExporter.generateOutputFileName(
                                    originalName = info.name,
                                    bilingual = false,
                                    fallbackExt = "docx",
                                )
                                val saved = DocumentExporter.saveFileToDownloads(
                                    context = context,
                                    fileName = outName,
                                    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    sourceFile = tempOut,
                                ).getOrThrow()

                                recordHistory(
                                    sourceSummary = info.name,
                                    translatedSummary = "Word 翻译完成（共替换 ${docxResult.translatedParagraphs} 段）",
                                )
                                saved
                            } finally {
                                tempOut.delete()
                            }
                        }

                        // TXT / Markdown / Excel / PPT：没有能原位回写文本的结构，
                        // 统一抽成纯文本段落翻译，导出一份双语对照的 .txt。
                        DocumentType.TXT, DocumentType.MARKDOWN,
                        DocumentType.XLSX, DocumentType.PPTX,
                        -> {
                            val uri = info.uri ?: error("无法读取文档路径")
                            val extractor = DocumentTextExtractors.forType(info.type)
                                ?: error("不支持的文档格式")
                            val paragraphs = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use {
                                    extractor.extract(it)
                                } ?: error("无法读取文档")
                            }
                            if (paragraphs.isEmpty()) error("文档里没有可翻译的文字")

                            val translated = ArrayList<String>(paragraphs.size)
                            paragraphs.forEachIndexed { index, paragraph ->
                                if (!_state.value.isWorking) return@forEachIndexed
                                val target = runCatching {
                                    engine.translate(
                                        paragraph,
                                        settings.sourceLanguageCode,
                                        settings.targetLanguageCode,
                                    ).last()
                                }.getOrNull() ?: paragraph
                                translated += target
                                _state.update {
                                    it.copy(
                                        done = index + 1,
                                        total = paragraphs.size,
                                        progressDescription =
                                            "正在翻译第 ${index + 1} / ${paragraphs.size} 段…",
                                    )
                                }
                            }

                            val tempOut = File.createTempFile("text_trans_", ".txt", context.cacheDir)
                            try {
                                tempOut.writeText(
                                    buildBilingualText(paragraphs, translated),
                                    Charsets.UTF_8,
                                )
                                val outName = DocumentExporter.generateOutputFileName(
                                    originalName = info.name,
                                    bilingual = true,
                                    fallbackExt = "txt",
                                )
                                val saved = DocumentExporter.saveFileToDownloads(
                                    context = context,
                                    fileName = outName,
                                    mimeType = "text/plain",
                                    sourceFile = tempOut,
                                ).getOrThrow()

                                recordHistory(
                                    sourceSummary = paragraphs.take(3).joinToString("\n"),
                                    translatedSummary =
                                        "文档翻译完成（共 ${paragraphs.size} 段）",
                                )
                                saved
                            } finally {
                                tempOut.delete()
                            }
                        }

                        DocumentType.UNKNOWN ->
                            error("不支持的文档格式，仅支持 PDF / Word / TXT / Markdown / Excel / PPT")
                    }
                }

                _state.update {
                    it.copy(
                        isWorking = false,
                        resultDisplayPath = resultExport.displayPath,
                        resultUri = resultExport.contentUri,
                        message = "已保存到 ${resultExport.displayPath}",
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e(TAG, "translate document failed", t)
                _state.update {
                    it.copy(
                        isWorking = false,
                        error = t.message ?: "翻译失败，请检查文件后重试",
                    )
                }
            }
        }
    }

    /**
     * 双语对照版式：每段先原文再译文，段间空一行。
     * 没有原格式可依的格式（TXT/Excel/PPT）用这个保留两边。
     */
    private fun buildBilingualText(source: List<String>, translated: List<String>): String =
        buildString {
            source.indices.forEach { i ->
                append(source[i])
                append('\n')
                val t = translated.getOrNull(i).orEmpty()
                if (t.isNotBlank() && t != source[i]) {
                    append(t)
                    append('\n')
                }
                if (i != source.lastIndex) append('\n')
            }
        }

    private fun recordHistory(sourceSummary: String, translatedSummary: String) {
        viewModelScope.launch {
            historyRepository.append(
                HistoryEntry(
                    stoppedAtEpochMs = System.currentTimeMillis(),
                    inputFull = sourceSummary,
                    outputFull = translatedSummary,
                ),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfTranslator.close()
    }

    companion object {
        private const val TAG = "DocumentTranslateVM"
    }
}

class DocumentTranslateViewModelFactory(
    private val app: Application,
    private val settingsRepository: UserSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val historyRepository: HistoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DocumentTranslateViewModel(
            app = app,
            settingsRepository = settingsRepository,
            apiKeyStore = apiKeyStore,
            historyRepository = historyRepository,
        ) as T
    }
}
