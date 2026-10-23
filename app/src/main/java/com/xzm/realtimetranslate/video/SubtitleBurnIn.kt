package com.xzm.realtimetranslate.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Burns the translated cues into a copy of the video.
 *
 * The output is re-encoded, so it plays anywhere with the subtitles already part of
 * the picture — no sidecar `.srt` to lose. The cost is that the whole video is decoded
 * and encoded again; resolution stays at the source's own, and duration drives how
 * long that takes.
 */
@OptIn(UnstableApi::class)
object SubtitleBurnIn {

    private const val TAG = "SubtitleBurnIn"

    /**
     * @param onProgress 0..1, or a negative value while the encoder cannot report yet.
     * @return the written file on success.
     */
    suspend fun burn(
        context: Context,
        source: Uri,
        cues: List<VideoCue>,
        outputFile: File,
        onProgress: (Float) -> Unit = {},
    ): Result<File> {
        if (cues.isEmpty()) {
            return Result.failure(IllegalArgumentException("没有字幕可压制"))
        }

        val settings = StaticOverlaySettings.Builder()
            // Bottom-centre. The overlay is drawn in its own space, so this only places
            // the rendered bitmap; the text is centred inside it by [CueBitmapOverlay].
            .setOverlayFrameAnchor(0f, -0.82f)
            .setBackgroundFrameAnchor(0f, -0.82f)
            .build()

        val effects = Effects(emptyList(), listOf(OverlayEffect(listOf(CueBitmapOverlay(cues)))))
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(effects)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Log.i(TAG, "burn-in completed → ${outputFile.name} (${outputFile.length()} B)")
                        if (continuation.isActive) continuation.resume(Result.success(outputFile))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        Log.e(TAG, "burn-in failed", exportException)
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(exportException))
                        }
                    }
                })
                .build()

            continuation.invokeOnCancellation { runCatching { transformer.cancel() } }

            val started = runCatching { transformer.start(item, outputFile.absolutePath) }
            if (started.isFailure) {
                val t = started.exceptionOrNull()!!
                Log.e(TAG, "could not start transformer", t)
                if (continuation.isActive) continuation.resume(Result.failure(t))
                return@suspendCancellableCoroutine
            }

            // Transformer only exposes progress through a polled holder, and reports
            // "not available yet" rather than a number while it sets up.
            val holder = ProgressHolder()
            Thread {
                while (continuation.isActive) {
                    val state = runCatching { transformer.getProgress(holder) }.getOrDefault(-1)
                    val fraction = if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        holder.progress / 100f
                    } else {
                        -1f
                    }
                    runCatching { onProgress(fraction) }
                    Thread.sleep(400L)
                }
            }.apply { isDaemon = true }.start()
        }
    }
}

/**
 * Renders whichever cue covers the current presentation time.
 *
 * This is a [BitmapOverlay] rather than a [androidx.media3.effect.TextOverlay] because
 * the plate behind the text has to be drawn by us: `BackgroundColorSpan` only paints
 * the glyphs' own boxes, so a two-line cue would get two separate bars with a gap
 * between them instead of one solid plate.
 *
 * Between cues the overlay returns a fully transparent pixel rather than `null`: the
 * base class requires a bitmap, and a 1×1 transparent one is what makes the subtitles
 * disappear instead of leaving the last line stuck on screen.
 */
@OptIn(UnstableApi::class)
private class CueBitmapOverlay(private val cues: List<VideoCue>) : BitmapOverlay() {

    private var cachedKey: String? = null
    private var cachedBitmap: Bitmap? = null
    private val blank: Lazy<Bitmap> = lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1_000L
        val cue = cues.firstOrNull { ms >= it.startMs && ms < it.endMs }
        val text = cue?.let { it.translation.ifBlank { it.source }.trim() }.orEmpty()
        if (text.isEmpty()) return blank.value

        // Re-render only when the words change: Transformer asks for a bitmap on every
        // frame, and laying out the same line 30 times a second is wasted work.
        if (text == cachedKey) return cachedBitmap ?: blank.value

        val bitmap = render(text)
        cachedKey = text
        cachedBitmap = bitmap
        return bitmap
    }

    private fun render(text: String): Bitmap {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = TEXT_SIZE_PX
            typeface = Typeface.DEFAULT_BOLD
        }
        val layout = StaticLayout.Builder
            .obtain(SpannableString(text), 0, text.length, textPaint, MAX_TEXT_WIDTH_PX)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .build()

        val padH = (TEXT_SIZE_PX * 0.55f).toInt()
        val padV = (TEXT_SIZE_PX * 0.32f).toInt()
        val bitmap = Bitmap.createBitmap(
            layout.width + padH * 2,
            layout.height + padV * 2,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.argb(170, 0, 0, 0))
        canvas.translate(padH.toFloat(), padV.toFloat())
        layout.draw(canvas)
        return bitmap
    }

    override fun release() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedKey = null
        if (blank.isInitialized()) blank.value.recycle()
        super.release()
    }

    private companion object {
        /** 100 px tall glyphs: legible at 1080p and still sane when downscaled. */
        const val TEXT_SIZE_PX = 100f
        const val MAX_TEXT_WIDTH_PX = 1400
    }
}
