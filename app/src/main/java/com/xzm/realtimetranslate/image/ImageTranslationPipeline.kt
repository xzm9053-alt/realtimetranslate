package com.xzm.realtimetranslate.image

import android.graphics.Bitmap
import android.graphics.Canvas
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.ocr.BackgroundColorSampler
import com.xzm.realtimetranslate.ocr.ScreenTextOcr
import com.xzm.realtimetranslate.ocr.TranslationPlanner
import com.xzm.realtimetranslate.overlay.CoverPainter
import com.xzm.realtimetranslate.overlay.CoverStyle
import com.xzm.realtimetranslate.overlay.CoverTarget
import com.xzm.realtimetranslate.translate.TranslationCache
import com.xzm.realtimetranslate.translate.TranslationEngine
import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/** Everything the image screen needs after one picture has been translated. */
data class ImageTranslationResult(
    /** The source picture with translation blocks painted on top. */
    val translated: Bitmap,
    val targets: List<CoverTarget>,
    val sourceText: String,
    val translatedText: String,
    /** Blocks left alone because they were already in the target language or noise. */
    val skipped: Int,
) {
    val isEmpty: Boolean get() = targets.isEmpty()
}

/**
 * Runs one still picture through the same steps the live full-screen cover uses:
 * recognize → plan → translate → paint. Sharing [ScreenTextOcr], [TranslationPlanner]
 * and [CoverPainter] with that path is the point — a saved picture must look exactly
 * like what the overlay would have drawn.
 *
 * Unlike the live path there is no frame loop, no epoch and no deduplication across
 * frames: one picture is translated once.
 */
class ImageTranslationPipeline(private val ocr: ScreenTextOcr) {

    private val backgroundSampler = BackgroundColorSampler()
    private val cache = TranslationCache()

    suspend fun run(
        original: Bitmap,
        settings: UserSettings,
        engine: TranslationEngine,
        screenDensity: Float,
        screenWidthPx: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImageTranslationResult = withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()
        val page = ocr.recognizePage(original, settings.ocrScript)
        if (page == null) {
            Log.i(TAG, "TRANSPROF img ocr NULL bmp=${original.width}x${original.height}")
            return@withContext ImageTranslationResult(
                translated = original.copy(Bitmap.Config.ARGB_8888, true),
                targets = emptyList(),
                sourceText = "",
                translatedText = "",
                skipped = 0,
            )
        }

        // Paragraph planning, same as the live full-screen cover: ML Kit emits one
        // block per wrapped line, and translating those one by one hands the engine
        // half-sentences that come back disconnected.
        val planned = TranslationPlanner.planParagraphs(page, settings.targetLanguageCode)
        val skipped = page.blocks.size - planned.size
        Log.i(
            TAG,
            "TRANSPROF img ocr blocks=${page.blocks.size} plan=${planned.size} " +
                "skipped=$skipped target=${settings.targetLanguageCode}",
        )
        if (planned.isEmpty()) {
            return@withContext ImageTranslationResult(
                translated = original.copy(Bitmap.Config.ARGB_8888, true),
                targets = emptyList(),
                sourceText = page.blocks.joinToString("\n") { it.text },
                translatedText = "",
                skipped = skipped,
            )
        }

        val total = planned.size
        val progress = AtomicInteger(0)
        val semaphore = Semaphore(TRANSLATE_CONCURRENCY)
        val style = CoverStyle(
            bilingual = settings.coverBilingual,
            fontScale = settings.coverFontScale,
            blockStyle = settings.coverBlockStyle,
        )

        val targets = coroutineScope {
            planned.map { target ->
                async {
                    semaphore.withPermit {
                        val translated = translateTarget(engine, settings, target)
                        val finished = progress.incrementAndGet()
                        withContext(Dispatchers.Main) { onProgress(finished, total) }
                        // A failed or empty translation leaves the original text
                        // uncovered rather than stamping "翻译失败" onto the picture.
                        if (translated.isNullOrBlank()) {
                            null
                        } else {
                            CoverTarget(
                                rect = target.rect,
                                text = translated,
                                background = backgroundSampler.sample(original, target.rect),
                                original = target.text,
                                sourceLineHeight = target.sourceLineHeight,
                            )
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val painted = original.copy(Bitmap.Config.ARGB_8888, true)
        if (targets.isNotEmpty()) {
            val density = CoverPainter.densityFor(
                screenDensity = screenDensity,
                bitmapWidth = original.width,
                screenWidthPx = screenWidthPx,
            )
            CoverPainter(density).draw(Canvas(painted), targets, style)
        }

        Log.i(
            TAG,
            "TRANSPROF img done blocks=${page.blocks.size} painted=${targets.size} " +
                "skipped=$skipped ms=${System.currentTimeMillis() - started}",
        )

        ImageTranslationResult(
            translated = painted,
            targets = targets,
            sourceText = planned.joinToString("\n") { it.text },
            translatedText = targets.joinToString("\n") { it.text },
            skipped = skipped,
        )
    }

    /** Cached per language pair + text, so re-running on the same picture is free. */
    private suspend fun translateTarget(
        engine: TranslationEngine,
        settings: UserSettings,
        target: com.xzm.realtimetranslate.ocr.TextTarget,
    ): String? {
        val source = settings.sourceLanguageCode
        val dest = settings.targetLanguageCode
        val key = "$source>$dest|${target.key}"
        cache.get(key)?.let { return it }
        return try {
            val out = engine.translate(text = target.text, sourceLang = source, targetLang = dest).last()
            if (out.isBlank()) null else out.also { cache.put(key, it) }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "img translate failed for '${target.text.take(16)}'", t)
            null
        }
    }

    fun close() {
        ocr.close()
        cache.clear()
    }

    companion object {
        private const val TAG = "ImagePipeline"

        /** Four at a time: enough to hide per-request latency without tripping rate limits. */
        private const val TRANSLATE_CONCURRENCY = 4
    }
}
