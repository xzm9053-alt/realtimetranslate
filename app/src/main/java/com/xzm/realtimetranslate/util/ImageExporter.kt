package com.xzm.realtimetranslate.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Writes a translated picture into the system gallery.
 *
 * Mirrors [ExportTranslator.publishToDownloads] but targets `MediaStore.Images` under
 * `Pictures/实时翻译`. Uses the same IS_PENDING handshake so a half-written
 * picture never appears in the gallery.
 */
object ImageExporter {

    private const val RELATIVE_DIR = "Pictures/实时翻译"
    private const val MIME_JPEG = "image/jpeg"

    /**
     * Compresses [bitmap] to JPEG and inserts it into the gallery. Returns a display
     * path like `Pictures/实时翻译/7月14日-22:35-翻译.jpg`.
     */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): Result<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_JPEG)
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建相册文件")
            try {
                resolver.openOutputStream(uri)?.use { writeJpeg(bitmap, it) }
                    ?: error("无法写入文件")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                // A pending row with no bytes behind it shows up as a broken tile in
                // some gallery apps; drop it instead of leaving the mess behind.
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            "$RELATIVE_DIR/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "实时翻译",
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { writeJpeg(bitmap, it) }
            file.absolutePath
        }
    }

    private fun writeJpeg(bitmap: Bitmap, out: OutputStream) {
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    }

    private const val JPEG_QUALITY = 95
}
