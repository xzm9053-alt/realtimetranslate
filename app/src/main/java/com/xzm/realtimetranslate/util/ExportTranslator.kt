package com.xzm.realtimetranslate.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ExportTranslator {
    /**
     * Export session as plain text.
     *
     * Format (two sections): full source block, then full translation block.
     * Continuous Live transcripts are not reliably sentence-aligned, so pairing
     * line-by-line would invent structure. Two sections stay faithful to the stream.
     */
    fun buildText(
        sourceText: String,
        translationText: String,
        stoppedAt: LocalDateTime = LocalDateTime.now(),
    ): String {
        val timeLabel = stoppedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return buildString {
            appendLine("翻译结果")
            appendLine("停止时间：$timeLabel")
            appendLine()
            appendLine("【原文】")
            appendLine(sourceText.ifBlank { "（无）" }.trim())
            appendLine()
            appendLine("【译文】")
            appendLine(translationText.ifBlank { "（无）" }.trim())
            appendLine()
        }
    }

    /** e.g. 7月14日-22:35-翻译结果.txt */
    fun fileName(
        stoppedAt: LocalDateTime = LocalDateTime.now(),
        ext: String = "txt",
        label: String = "翻译结果",
    ): String {
        val month = stoppedAt.monthValue
        val day = stoppedAt.dayOfMonth
        val hm = stoppedAt.format(DateTimeFormatter.ofPattern("HH:mm"))
        return "${month}月${day}日-$hm-$label.$ext"
    }

    /**
     * Write to public Downloads. Returns display path or content URI string.
     */
    fun saveToDownloads(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/plain",
    ): Result<String> {
        return runCatching {
            val bytes = content.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建下载文件")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("无法写入文件")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Download/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                file.absolutePath
            }
        }
    }

    /**
     * Copies an already-written file (e.g. a Transformer export in the cache) into
     * public Downloads.
     *
     * Separate from [saveToDownloads] because a re-encoded video cannot be held in a
     * String: it is streamed from disk. The source file is left in place; the caller
     * decides when to delete it.
     */
    fun publishToDownloads(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
    ): Result<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建下载文件")
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: error("无法写入文件")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Download/$displayName"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, displayName)
            source.inputStream().use { input ->
                FileOutputStream(file).use { input.copyTo(it) }
            }
            file.absolutePath
        }
    }
}
