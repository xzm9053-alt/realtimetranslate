package com.xzm.realtimetranslate.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.xzm.realtimetranslate.data.OcrScript
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

/**
 * Wrapper around the ML Kit bundled [TextRecognizer]s. Building a recognizer
 * loads its (compressed, bundled) model — expensive — so one recognizer is kept
 * per script and only built once. Fully offline: the bundled `text-recognition-*`
 * artifacts ship the model in the APK and need no Google Play Services.
 *
 * Input is preprocessed before recognition (upscale + grayscale/contrast) so small
 * screen text reaches a readable size — resolution is the single biggest accuracy
 * lever for ML Kit. [OcrScript.AUTO] runs the Latin + Japanese recognizers in
 * parallel and picks the better result, covering common English / Japanese screens.
 */
class ScreenTextOcr {

    private val recognizers = mutableMapOf<OcrScript, TextRecognizer?>()

    /**
     * Recognizes [bitmap] and returns the text in reading order, or null on
     * failure/empty. The caller keeps ownership of [bitmap]; intermediate
     * preprocessed copies are recycled here.
     */
    suspend fun recognize(bitmap: Bitmap, script: OcrScript): String? {
        val prepared = preprocess(bitmap)
        return try {
            when (script) {
                OcrScript.AUTO -> recognizeAuto(prepared)
                else -> recognizeWith(prepared, script)
            }
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
    }

    /** Runs Latin + Japanese in parallel and picks the result that matches the content. */
    private suspend fun recognizeAuto(prepared: Bitmap): String? = coroutineScope {
        val latin = async { recognizeWith(prepared, OcrScript.LATIN) }
        val japanese = async { recognizeWith(prepared, OcrScript.JAPANESE) }
        pickBest(latin.await(), japanese.await())
    }

    private suspend fun recognizeWith(prepared: Bitmap, script: OcrScript): String? {
        val rec = ensureRecognizer(script) ?: return null
        return try {
            val text = rec.process(InputImage.fromBitmap(prepared, 0)).await()
            joinReadingOrder(text).takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "recognize failed for $script", t)
            null
        }
    }

    /** Lazy per-script recognizer; [OcrScript.AUTO] is resolved before this is reached. */
    private fun ensureRecognizer(script: OcrScript): TextRecognizer? {
        if (recognizers.containsKey(script)) return recognizers[script]
        val rec = try {
            when (script) {
                OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                OcrScript.CHINESE_MIX -> TextRecognition.getClient(
                    ChineseTextRecognizerOptions.Builder().build(),
                )
                OcrScript.JAPANESE -> TextRecognition.getClient(
                    JapaneseTextRecognizerOptions.Builder().build(),
                )
                OcrScript.KOREAN -> TextRecognition.getClient(
                    KoreanTextRecognizerOptions.Builder().build(),
                )
                OcrScript.AUTO -> null // resolved in recognizeAuto()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "failed to build $script recognizer", t)
            null
        }
        recognizers[script] = rec
        return rec
    }

    fun close() {
        for (rec in recognizers.values) {
            runCatching { rec?.close() }
        }
        recognizers.clear()
    }

    // ---- script selection (AUTO) ---------------------------------------------

    /**
     * Picks whichever recognizer output matches the content:
     * empty side loses; presence of kana → Japanese; otherwise CJK-heavy Japanese
     * output wins, Latin-heavy Latin output wins.
     */
    private fun pickBest(latin: String?, japanese: String?): String? {
        val lt = latin?.trim().orEmpty()
        val jt = japanese?.trim().orEmpty()
        if (lt.isEmpty() && jt.isEmpty()) return null
        if (lt.isEmpty()) return japanese
        if (jt.isEmpty()) return latin
        if (jt.any { it in '぀'..'ヿ' }) return japanese // hiragana/katakana
        val jpCjkRatio = jt.count(::isCjk).toFloat() / jt.length
        val latinRatio = lt.count { it.code in 0x20..0x7e }.toFloat() / lt.length
        return if (jpCjkRatio > 0.15f && jpCjkRatio >= latinRatio) japanese else latin
    }

    /** CJK ideographs, kana, Hangul, bopomofo. */
    private fun isCjk(c: Char): Boolean {
        val cp = c.code
        return cp in 0x3400..0x4dbf ||  // Ext A
            cp in 0x4e00..0x9fff ||      // CJK Unified
            cp in 0x3040..0x30ff ||      // hiragana / katakana
            cp in 0x3100..0x312f ||      // bopomofo
            cp in 0xac00..0xd7af         // Hangul
    }

    // ---- preprocessing --------------------------------------------------------

    /**
     * Upscales small crops to a readable text size (resolution is the top OCR
     * accuracy lever) and applies a mild grayscale + contrast boost. Threshold
     * binarization is intentionally off: ML Kit's deep models prefer natural
     * images, and over-binarizing anti-aliased UI text hurts more than it helps.
     */
    private fun preprocess(bitmap: Bitmap): Bitmap = enhance(upscale(bitmap))

    private fun upscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w >= MIN_TARGET_WIDTH && h >= MIN_TARGET_HEIGHT) return bitmap
        val scale = maxOf(
            MIN_TARGET_WIDTH.toFloat() / w,
            MIN_TARGET_HEIGHT.toFloat() / h,
        ).coerceIn(1f, MAX_SCALE)
        if (scale <= 1.01f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * scale).roundToInt(),
            (h * scale).roundToInt(),
            true,
        )
    }

    /** Grayscale + slight contrast stretch, in one pass via [ColorMatrix]. */
    private fun enhance(bitmap: Bitmap): Bitmap {
        if (bitmap.isRecycled) return bitmap
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val contrast = CONTRAST
        val translate = 128f * (1f - contrast)
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
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
        /** Minimum preprocessed width/height so screen text reaches readable size. */
        private const val MIN_TARGET_WIDTH = 1200
        private const val MIN_TARGET_HEIGHT = 120
        /** Cap the upscale to bound compute on already-large crops. */
        private const val MAX_SCALE = 2f
        /** Mild contrast boost (1 = identity). */
        private const val CONTRAST = 1.15f
    }
}
