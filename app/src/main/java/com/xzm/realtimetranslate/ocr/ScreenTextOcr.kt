package com.xzm.realtimetranslate.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.xzm.realtimetranslate.data.OcrScript
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around the ML Kit bundled [TextRecognizer]s. Building a
 * recognizer loads its (compressed, bundled) model — expensive — so one
 * recognizer is kept per script and only rebuilt when the script changes.
 * Fully offline: the bundled `text-recognition-*` artifacts ship the model in
 * the APK and need no Google Play Services.
 */
class ScreenTextOcr {

    private var recognizer: TextRecognizer? = null
    private var currentScript: OcrScript? = null

    /** Recognizes [bitmap] and returns the text in reading order, or null on failure/empty. */
    suspend fun recognize(bitmap: Bitmap, script: OcrScript): String? {
        val rec = ensureRecognizer(script) ?: return null
        return try {
            val text = rec.process(InputImage.fromBitmap(bitmap, 0)).await()
            joinReadingOrder(text).takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "recognize failed", t)
            null
        }
    }

    /** Rebuild the recognizer whenever the script setting changes. */
    private fun ensureRecognizer(script: OcrScript): TextRecognizer? {
        if (recognizer != null && currentScript == script) return recognizer
        recognizer?.close()
        recognizer = try {
            when (script) {
                OcrScript.CHINESE_MIX -> TextRecognition.getClient(
                    ChineseTextRecognizerOptions.Builder().build(),
                )
                OcrScript.JAPANESE -> TextRecognition.getClient(
                    JapaneseTextRecognizerOptions.Builder().build(),
                )
                OcrScript.KOREAN -> TextRecognition.getClient(
                    KoreanTextRecognizerOptions.Builder().build(),
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "failed to build $script recognizer", t)
            null
        }
        currentScript = script
        return recognizer
    }

    fun close() {
        recognizer?.close()
        recognizer = null
        currentScript = null
    }

    /** Sorts blocks top→left so a multi-line caption region reads in order. */
    private fun joinReadingOrder(text: Text): String {
        val blocks = text.textBlocks.sortedWith(
            compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }),
        )
        val sb = StringBuilder()
        for (block in blocks) {
            for (line in block.lines) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line.text.trim())
            }
        }
        return sb.toString().trim()
    }

    companion object {
        private const val TAG = "ScreenTextOcr"
    }
}
