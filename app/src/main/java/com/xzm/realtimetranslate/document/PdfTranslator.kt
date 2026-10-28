package com.xzm.realtimetranslate.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.image.ImageTranslationPipeline
import com.xzm.realtimetranslate.ocr.ScreenTextOcr
import com.xzm.realtimetranslate.translate.TranslationEngine
import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.util.realScreenMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.roundToInt

data class PdfTranslationResult(
    val pageCount: Int,
    val exportedPages: Int,
    val sourceTextSummary: String = "",
    val translatedTextSummary: String = "",
)

class PdfTranslator(private val ocr: ScreenTextOcr) {

    private val pipeline = ImageTranslationPipeline(ocr)

    suspend fun translatePdf(
        context: Context,
        pdfPfd: ParcelFileDescriptor,
        output: OutputStream,
        settings: UserSettings,
        engine: TranslationEngine,
        config: DocumentTranslationConfig = DocumentTranslationConfig(),
        onProgress: (page: Int, totalPages: Int) -> Unit = { _, _ -> },
    ): PdfTranslationResult = withContext(Dispatchers.Default) {
        val renderer = PdfRenderer(pdfPfd)
        val pageCount = renderer.pageCount
        validatePageCount(pageCount, config.maxPages)

        val (screenWidth, _, density) = context.realScreenMetrics()
        val pdfDoc = PdfDocument()

        val sourceTexts = StringBuilder()
        val translatedTexts = StringBuilder()
        var currentExportPageNumber = 1

        try {
            for (index in 0 until pageCount) {
                val page = renderer.openPage(index)
                val widthPoints = page.width
                val heightPoints = page.height

                val scale = calculateScaleFactor(widthPoints, heightPoints)
                val bmpW = (widthPoints * scale).roundToInt()
                val bmpH = (heightPoints * scale).roundToInt()

                val originalBmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                originalBmp.eraseColor(Color.WHITE)
                page.render(originalBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val result = pipeline.run(
                    original = originalBmp,
                    settings = settings,
                    engine = engine,
                    screenDensity = density,
                    screenWidthPx = screenWidth,
                )

                if (result.sourceText.isNotBlank()) {
                    sourceTexts.appendLine("--- 第 ${index + 1} 页 ---")
                    sourceTexts.appendLine(result.sourceText)
                }
                if (result.translatedText.isNotBlank()) {
                    translatedTexts.appendLine("--- 第 ${index + 1} 页 ---")
                    translatedTexts.appendLine(result.translatedText)
                }

                // If bilingualAlternating: first write original page, then translated page
                if (config.bilingualAlternating) {
                    writeBitmapToPdfPage(
                        pdfDoc = pdfDoc,
                        bmp = originalBmp,
                        widthPoints = widthPoints,
                        heightPoints = heightPoints,
                        pageNumber = currentExportPageNumber++,
                    )
                    writeBitmapToPdfPage(
                        pdfDoc = pdfDoc,
                        bmp = result.translated,
                        widthPoints = widthPoints,
                        heightPoints = heightPoints,
                        pageNumber = currentExportPageNumber++,
                    )
                } else {
                    // Translated page only
                    writeBitmapToPdfPage(
                        pdfDoc = pdfDoc,
                        bmp = result.translated,
                        widthPoints = widthPoints,
                        heightPoints = heightPoints,
                        pageNumber = currentExportPageNumber++,
                    )
                }

                originalBmp.recycle()
                result.translated.recycle()

                withContext(Dispatchers.Main) {
                    onProgress(index + 1, pageCount)
                }
            }

            withContext(Dispatchers.IO) {
                pdfDoc.writeTo(output)
            }
        } finally {
            pdfDoc.close()
            renderer.close()
        }

        val exportedPages = calculateExportedPages(pageCount, config.bilingualAlternating)
        PdfTranslationResult(
            pageCount = pageCount,
            exportedPages = exportedPages,
            sourceTextSummary = sourceTexts.toString(),
            translatedTextSummary = translatedTexts.toString(),
        )
    }

    private fun writeBitmapToPdfPage(
        pdfDoc: PdfDocument,
        bmp: Bitmap,
        widthPoints: Int,
        heightPoints: Int,
        pageNumber: Int,
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(widthPoints, heightPoints, pageNumber).create()
        val docPage = pdfDoc.startPage(pageInfo)
        val destRect = Rect(0, 0, widthPoints, heightPoints)
        docPage.canvas.drawBitmap(bmp, null, destRect, null)
        pdfDoc.finishPage(docPage)
    }

    fun close() {
        pipeline.close()
    }

    companion object {
        private const val TAG = "PdfTranslator"
        const val DEFAULT_MAX_SIDE = 2000f

        fun validatePageCount(pageCount: Int, maxPages: Int = 50) {
            require(pageCount > 0) { "PDF 为空，无有效页面" }
            require(pageCount <= maxPages) {
                "PDF 共有 $pageCount 页，超出单次上限 $maxPages 页，请分次翻译"
            }
        }

        fun calculateScaleFactor(pageWidth: Int, pageHeight: Int, targetMaxSide: Float = DEFAULT_MAX_SIDE): Float {
            val maxDimension = maxOf(pageWidth, pageHeight).toFloat().coerceAtLeast(1f)
            return (targetMaxSide / maxDimension).coerceIn(1.0f, 4.0f)
        }

        fun calculateExportedPages(pageCount: Int, bilingualAlternating: Boolean): Int {
            return if (bilingualAlternating) pageCount * 2 else pageCount
        }
    }
}
