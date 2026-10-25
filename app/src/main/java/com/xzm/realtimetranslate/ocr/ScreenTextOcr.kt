package com.xzm.realtimetranslate.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.xzm.realtimetranslate.util.AppLog as Log
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
    suspend fun recognize(bitmap: Bitmap, script: OcrScript): String? =
        recognizePage(bitmap, script)
            ?.blocks
            ?.joinToString("\n") { it.text }
            ?.let { OcrTextProcessor.cleanAndJoinLines(it) }
            ?.takeIf { it.isNotEmpty() }

    /**
     * Same recognition as [recognize], but keeps the per-line bounding boxes so
     * callers can draw over the original text. Coordinates are already scaled
     * back to [bitmap]'s own pixel space.
     */
    suspend fun recognizePage(bitmap: Bitmap, script: OcrScript): OcrPage? {
        val prepared = prepare(bitmap)
        return try {
            when (script) {
                OcrScript.AUTO -> recognizePageAuto(prepared)
                else -> recognizePageWith(prepared, script)
            }
        } finally {
            if (prepared.bitmap !== bitmap) prepared.bitmap.recycle()
        }
    }

    /** Runs Latin + Japanese in parallel and keeps the page that matches the content. */
    private suspend fun recognizePageAuto(prepared: Prepared): OcrPage? = coroutineScope {
        val latin = async { recognizePageWith(prepared, OcrScript.LATIN) }
        val japanese = async { recognizePageWith(prepared, OcrScript.JAPANESE) }
        pickBestPage(latin.await(), japanese.await())
    }

    private suspend fun recognizePageWith(prepared: Prepared, script: OcrScript): OcrPage? {
        val rec = ensureRecognizer(script) ?: return null
        return try {
            val text = rec.process(InputImage.fromBitmap(prepared.bitmap, 0)).await()
            val page = toPage(text, prepared)
            Log.i(
                TAG,
                "TRANSPROF ocrraw script=$script rawLen=${text.text.length} " +
                    "rawBlocks=${text.textBlocks.size} kept=${page.blocks.size} " +
                    "bmp=${prepared.bitmap.width}x${prepared.bitmap.height} " +
                    "src=${prepared.sourceWidth}x${prepared.sourceHeight} " +
                    "sx=${prepared.scaleX} sy=${prepared.scaleY}",
            )
            page.takeIf { it.blocks.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "recognize failed for $script", t)
            null
        }
    }

    /** Converts ML Kit output to page-space boxes, undoing the preprocessing upscale. */
    private fun toPage(text: Text, prepared: Prepared): OcrPage {
        val blocks = text.textBlocks
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .mapNotNull { block ->
                val lines = block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    val content = line.text.trim()
                    if (content.isEmpty()) return@mapNotNull null
                    OcrLine(
                        text = content,
                        rect = PixelRect(box.left, box.top, box.right, box.bottom)
                            .scaleToSource(prepared.scaleX, prepared.scaleY),
                    )
                }
                if (lines.isEmpty()) null else OcrBlock(lines)
            }
        return OcrPage(prepared.sourceWidth, prepared.sourceHeight, blocks)
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

    private fun pickBestPage(latin: OcrPage?, japanese: OcrPage?): OcrPage? {
        val lt = latin?.let(::pageText)?.trim().orEmpty()
        val jt = japanese?.let(::pageText)?.trim().orEmpty()
        if (lt.isEmpty() && jt.isEmpty()) return null
        if (lt.isEmpty()) return japanese
        if (jt.isEmpty()) return latin
        return if (preferJapanese(jt, lt)) japanese else latin
    }

    private fun pageText(page: OcrPage): String = page.blocks.joinToString("\n") { it.text }

    private fun preferJapanese(jt: String, lt: String): Boolean {
        if (jt.any { it in '぀'..'ヿ' }) return true // hiragana/katakana
        val jpCjkRatio = jt.count(::isCjk).toFloat() / jt.length
        val latinRatio = lt.count { it.code in 0x20..0x7e }.toFloat() / lt.length
        return jpCjkRatio > 0.15f && jpCjkRatio >= latinRatio
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

    /** A preprocessed copy plus the factor needed to map boxes back to source pixels. */
    private class Prepared(
        val bitmap: Bitmap,
        val scaleX: Float,
        val scaleY: Float,
        val sourceWidth: Int,
        val sourceHeight: Int,
    )

    /**
     * Upscales small crops to a readable text size (resolution is the top OCR
     * accuracy lever) and applies a mild grayscale + contrast boost. Threshold
     * binarization is intentionally off: ML Kit's deep models prefer natural
     * images, and over-binarizing anti-aliased UI text hurts more than it helps.
     */
    private fun prepare(bitmap: Bitmap): Prepared {
        val upscaled = upscale(bitmap)
        val enhanced = enhance(upscaled)
        if (upscaled !== bitmap && upscaled !== enhanced) upscaled.recycle()
        return Prepared(
            bitmap = enhanced,
            scaleX = enhanced.width.toFloat() / bitmap.width,
            scaleY = enhanced.height.toFloat() / bitmap.height,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
        )
    }

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
