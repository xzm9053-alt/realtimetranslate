package com.xzm.realtimetranslate.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Saves translated documents (PDF, DOCX) into MediaStore `Download/实时翻译`.
 */
object DocumentExporter {

    const val RELATIVE_DIR = "Download/实时翻译"

    /**
     * Generates a descriptive filename like `sample-双语-10月28日-15:30.pdf`
     * or `report-翻译-10月28日-15:30.docx`.
     */
    fun generateOutputFileName(
        originalName: String,
        bilingual: Boolean,
        fallbackExt: String = "pdf",
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val baseName = originalName.substringBeforeLast('.', originalName).ifBlank { "文档" }
        val ext = originalName.substringAfterLast('.', fallbackExt).ifBlank { fallbackExt }
        val modeTag = if (bilingual) "双语" else "翻译"
        val timeTag = now.format(DateTimeFormatter.ofPattern("M月d日-HH:mm"))
        return "$baseName-$modeTag-$timeTag.$ext"
    }

    /**
     * Writes binary data into the system Downloads folder under `Download/实时翻译`.
     * Returns a display path like `Download/实时翻译/sample-双语-10月28日-15:30.pdf`.
     */
    fun saveToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        writeBlock: (OutputStream) -> Unit,
    ): Result<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法在下载目录创建文件")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    writeBlock(out)
                } ?: error("无法写入文件输出流")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            "$RELATIVE_DIR/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val subDir = File(baseDir, "实时翻译")
            if (!subDir.exists()) subDir.mkdirs()
            val file = File(subDir, fileName)
            FileOutputStream(file).use { out ->
                writeBlock(out)
            }
            file.absolutePath
        }
    }

    /**
     * Helper to save a temporary file to Downloads.
     */
    fun saveFileToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        sourceFile: File,
    ): Result<String> = saveToDownloads(context, fileName, mimeType) { out ->
        FileInputStream(sourceFile).use { it.copyTo(out) }
    }
}
