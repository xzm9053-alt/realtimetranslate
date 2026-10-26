package com.xzm.realtimetranslate.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Paints translation blocks over an existing picture.
 *
 * Kept apart from [CoverView] so the same pixels can be produced in two places: the
 * live screen overlay (a [Canvas] owned by a View) and the static image pipeline (a
 * [Canvas] backed by a Bitmap). A second copy of this logic would drift, and then a
 * saved picture would not match what the user previewed.
 *
 * [density] converts the dp-based paddings and minimum font sizes into the target
 * canvas's own pixels. For the screen that is the display density; for a photo it is
 * scaled by how much the photo is bigger than the screen — see [densityFor].
 */
class CoverPainter(private val density: Float) {

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val translationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val originalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xC8FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private val blockPad = dp(2f)
    private val textPadX = dp(4f)
    private val textPadY = dp(2f)
    private val cornerRadius = dp(4f)

    fun draw(canvas: Canvas, targets: List<CoverTarget>, style: CoverStyle) {
        for (target in targets) drawTarget(canvas, target, style)
    }

    private fun drawTarget(canvas: Canvas, target: CoverTarget, style: CoverStyle) {
        val block = RectF(
            target.rect.left - blockPad,
            target.rect.top - blockPad,
            target.rect.right + blockPad,
            target.rect.bottom + blockPad,
        )
        blockPaint.color = if (style.blockStyle == CoverView.BLOCK_STYLE_DARK) {
            DARK_BLOCK
        } else {
            target.background
        }
        canvas.drawRoundRect(block, cornerRadius, cornerRadius, blockPaint)
        // The block colour is sampled from whatever sits behind the text, so it can
        // be white just as easily as black. A fixed white pen would vanish on a
        // white page — pick the ink from the block's own luminance.
        translationPaint.color = inkOn(blockPaint.color)
        originalPaint.color = (inkOn(blockPaint.color) and 0x00FFFFFF) or 0xC8000000.toInt()

        val left = target.rect.left + textPadX
        val right = target.rect.right - textPadX
        val width = right - left
        var top = target.rect.top + textPadY
        var available = target.rect.bottom - textPadY - top
        if (width <= 0f || available <= 0f) return

        val maxFont = (target.rect.height * 0.55f * style.fontScale)
            .coerceIn(dp(8f), dp(28f))

        val original = target.original
        if (style.bilingual && !original.isNullOrBlank()) {
            val originalHeight = available * 0.4f
            val fitted = fit(original, width, originalHeight, maxFont * 0.75f)
            val used = drawLines(canvas, fitted, left, top, originalHeight, originalPaint)
            top += used + dp(1f)
            available -= used + dp(1f)
        }

        val fitted = fit(target.text, width, available, maxFont)
        drawLines(canvas, fitted, left, top, available, translationPaint)
    }

    /**
     * Lays out [text] for a [width]×[height] box at up to [maxFont] px, shrinking
     * and then ellipsizing so it never spills outside the block.
     */
    private fun fit(text: String, width: Float, height: Float, maxFont: Float): FittedText =
        CoverLayout.fit(
            text = text,
            maxWidth = width,
            maxHeight = height,
            maxFontSize = maxFont.coerceAtLeast(dp(8f)),
            minFontSize = dp(8f),
            measure = { s, size ->
                translationPaint.textSize = size
                translationPaint.measureText(s)
            },
            lineHeight = { size -> size * LINE_SPACING },
        )

    /** Draws [fitted] vertically centred in [height]; returns the height it consumed. */
    private fun drawLines(
        canvas: Canvas,
        fitted: FittedText,
        left: Float,
        top: Float,
        height: Float,
        paint: Paint,
    ): Float {
        if (fitted.lines.isEmpty()) return 0f
        paint.textSize = fitted.fontSize
        val metrics = paint.fontMetrics
        val lineHeight = fitted.fontSize * LINE_SPACING
        val used = fitted.lines.size * lineHeight
        var baseline = top + (height - used) / 2f - metrics.ascent
        for (line in fitted.lines) {
            canvas.drawText(line, left, baseline, paint)
            baseline += lineHeight
        }
        return used
    }

    /** Near-black on light blocks, white on dark ones. */
    private fun inkOn(background: Int): Int {
        val r = (background shr 16) and 0xFF
        val g = (background shr 8) and 0xFF
        val b = background and 0xFF
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        return if (luminance > 140f) 0xFF1A1A1A.toInt() else Color.WHITE
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        private const val LINE_SPACING = 1.15f
        private val DARK_BLOCK = 0xE6000000.toInt()

        /**
         * Density for painting into a bitmap of [bitmapWidth] on a screen of
         * [screenWidthPx]. A 2000px photo on a 1080px screen gets ~1.85x the screen's
         * dp scale, so paddings and the minimum font size stay legible at photo scale
         * instead of shrinking to a hairline.
         */
        fun densityFor(
            screenDensity: Float,
            bitmapWidth: Int,
            screenWidthPx: Int,
        ): Float {
            if (screenWidthPx <= 0 || bitmapWidth <= 0) return screenDensity
            val ratio = bitmapWidth.toFloat() / screenWidthPx
            return (screenDensity * ratio).coerceIn(screenDensity, screenDensity * 4f)
        }
    }
}
