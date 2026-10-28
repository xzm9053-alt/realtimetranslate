package com.xzm.realtimetranslate.document

import android.net.Uri

/**
 * Supported document formats for translation.
 */
enum class DocumentType(val extension: String, val mimeType: String, val displayName: String) {
    PDF("pdf", "application/pdf", "PDF 文档"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word 文档"),
    UNKNOWN("", "", "未知类型");

    companion object {
        fun fromFileName(name: String): DocumentType {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "pdf" -> PDF
                "docx" -> DOCX
                else -> UNKNOWN
            }
        }

        fun fromMimeType(mime: String?): DocumentType {
            if (mime == null) return UNKNOWN
            return when {
                mime.equals("application/pdf", ignoreCase = true) -> PDF
                mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ignoreCase = true) ||
                    mime.equals("application/msword", ignoreCase = true) -> DOCX
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Metadata for a selected document to be translated.
 */
data class DocumentInfo(
    val uri: Uri? = null,
    val name: String,
    val sizeBytes: Long,
    val type: DocumentType,
) {
    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
            else -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
        }
}

/**
 * Configuration for document translation.
 */
data class DocumentTranslationConfig(
    /**
     * For PDF: whether each original page is followed immediately by the translated page.
     * Defaults to true per user confirmation ("双语交替").
     */
    val bilingualAlternating: Boolean = true,
    /**
     * Safety limit to avoid exhausting memory or translation quota.
     */
    val maxPages: Int = 50,
)

/**
 * Progress updates during translation.
 */
data class DocumentTranslationProgress(
    val done: Int,
    val total: Int,
    val description: String,
)
