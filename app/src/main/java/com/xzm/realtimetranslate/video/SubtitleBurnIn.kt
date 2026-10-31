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
import com.xzm.realtimetranslate.data.VideoSubtitleMode
import com.xzm.realtimetranslate.data.VideoSubtitleStyle
import com.xzm.realtimetranslate.util.SrtFormatter
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
        mode: VideoSubtitleMode = VideoSubtitleMode.TRANSLATION,
        style: VideoSubtitleStyle = VideoSubtitleStyle(),
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

        val effects = Effects(emptyList(), listOf(OverlayEffect(listOf(CueBitmapOverlay(cues, mode, style)))))
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
private class CueBitmapOverlay(
    private val cues: List<VideoCue>,
    private val mode: VideoSubtitleMode,
    private val style: VideoSubtitleStyle,
) : BitmapOverlay() {

    private var cachedKey: String? = null
    private var cachedBitmap: Bitmap? = null
    private val blank: Lazy<Bitmap> = lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1_000L
        val cue = cues.firstOrNull { ms >= it.startMs && ms < it.endMs }
            ?: return blank.value
        val text = SrtFormatter.render(cue, mode)
        if (text.isEmpty()) return blank.value

        // Re-render only when the words change: Transformer asks for a bitmap on every
        // frame, and laying out the same line 30 times a second is wasted work.
        if (text == cachedKey) return cachedBitmap ?: blank.value

        val bitmap = render(cue)
        cachedKey = text
        cachedBitmap = bitmap
        return bitmap
    }

    /**
     * In BOTH mode the source line is drawn smaller and dimmer than the translation,
     * so the eye lands on the translation first — the same hierarchy the in-app
     * preview uses.
     */
    private fun render(cue: VideoCue): Bitmap {
        val source = cue.source.trim()
        val translation = cue.translation.trim()

        val textSize = TEXT_SIZE_PX * style.fontScale
        val sourceSize = SOURCE_SIZE_PX * style.fontScale

        val lines = ArrayList<StaticLayout>(2)
        val primary = when (mode) {
            VideoSubtitleMode.SOURCE -> source.ifBlank { translation }
            else -> translation.ifBlank { source }
        }
        // The dim line is a second copy of the source only when the primary line is the
        // translation. With a blank translation the primary already *is* the source, and
        // drawing both would show the same sentence twice.
        if (mode == VideoSubtitleMode.BOTH && translation.isNotEmpty() && source != translation) {
            lines += layout(source, sourceSize, style.sourceTextColor.toInt())
        }
        if (primary.isNotEmpty()) lines += layout(primary, textSize, style.textColor.toInt())
        if (lines.isEmpty()) return blank.value

        val padH = (textSize * 0.55f).toInt()
        val padV = (textSize * 0.32f).toInt()
        val gap = (textSize * 0.18f).toInt()
        val contentW = lines.maxOf { it.width }
        val contentH = lines.sumOf { it.height } + gap * (lines.size - 1)

        val bitmap = Bitmap.createBitmap(
            contentW + padH * 2,
            contentH + padV * 2,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        // 背景层带 alpha：0 = 完全不画底板（字直接压在画面上）。
        // 直接按 0..1 换算成 0..255，与预览层 Color.Black.copy(alpha) 逐值对齐；
        // 默认 0.667 正好还原改动前写死的 0xAA。
        val plateAlpha = (style.backgroundAlpha.coerceIn(0f, 1f) * 255f).toInt()
        if (plateAlpha > 0) canvas.drawColor(plateAlpha shl 24)
        var y = padV.toFloat()
        lines.forEach { line ->
            canvas.save()
            // Centre each line inside the plate: the two lines rarely have the same
            // width, and left-aligning them would look like a ragged block.
            canvas.translate(padH + (contentW - line.width) / 2f, y)
            line.draw(canvas)
            canvas.restore()
            y += line.height + gap
        }
        return bitmap
    }

    private fun layout(text: String, sizePx: Float, color: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizePx
            typeface = Typeface.DEFAULT_BOLD
        }
        return StaticLayout.Builder
            .obtain(SpannableString(text), 0, text.length, paint, MAX_TEXT_WIDTH_PX)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .build()
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
        const val SOURCE_SIZE_PX = 72f
        const val MAX_TEXT_WIDTH_PX = 1400
    }
}
