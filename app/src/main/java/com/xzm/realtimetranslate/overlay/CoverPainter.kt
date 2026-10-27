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
        val layout = layoutOf(target, style)
        val block = layout.block
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

        var top = layout.textTop
        if (layout.original != null) {
            drawLines(canvas, layout.original, layout.left, top, layout.originalHeight, originalPaint)
            top += layout.originalHeight + layout.gap
        }
        drawLines(canvas, layout.translation, layout.left, top, layout.translationHeight, translationPaint)
    }

    /**
     * The block a target occupies once its translation has been laid out.
     *
     * Public because the screen session has to erase exactly this rect from the next
     * capture. If erasing used the source rect instead, the taller painted block
     * would be captured again as fresh text and re-translated forever.
     */
    fun paintedBounds(target: CoverTarget, style: CoverStyle): RectF = layoutOf(target, style).block

    private class Layout(
        val block: RectF,
        val left: Float,
        val textTop: Float,
        val translation: FittedText,
        val translationHeight: Float,
        val original: FittedText?,
        val originalHeight: Float,
        val gap: Float,
    )

    private fun layoutOf(target: CoverTarget, style: CoverStyle): Layout {
        val left = target.rect.left + textPadX
        val right = target.rect.right - textPadX
        val width = right - left
        if (width <= 0f) {
            return Layout(RectF(), left, target.rect.top.toFloat(),
                FittedText(emptyList(), 0f, false), 0f, null, 0f, 0f)
        }

        val original = target.original
        val bilingual = style.bilingual && !original.isNullOrBlank()

        // Lay the translation out against the block's real width. Height is left
        // open because the block grows downwards to fit whatever the fitter
        // produces; the width is what keeps lines from running off the block.
        val maxFont = fontFor(target, style)
        val translation = fit(target.text, width, Float.MAX_VALUE / 4f, maxFont)
        val fittedOriginal = if (bilingual) {
            fit(original.orEmpty(), width, Float.MAX_VALUE / 4f, maxFont * 0.72f)
        } else {
            null
        }

        val originalHeight = fittedOriginal?.let { it.lines.size * it.fontSize * LINE_SPACING } ?: 0f
        val translationHeight = translation.lines.size * translation.fontSize * LINE_SPACING
        val gap = if (fittedOriginal != null) dp(2f) else 0f

        val needed = originalHeight + gap + translationHeight + textPadY * 2
        val blockBottom = maxOf(target.rect.bottom.toFloat(), target.rect.top + needed)
        val block = RectF(
            target.rect.left - blockPad,
            target.rect.top - blockPad,
            target.rect.right + blockPad,
            blockBottom + blockPad,
        )
        return Layout(
            block = block,
            left = left,
            textTop = target.rect.top + textPadY,
            translation = translation,
            translationHeight = translationHeight,
            original = fittedOriginal,
            originalHeight = originalHeight,
            gap = gap,
        )
    }

    /**
     * The starting size for the translation, before fitting shrinks it to the box.
     *
     * [CoverTarget.sourceLineHeight] is the source text's own line height, so starting
     * there makes the replacement read at the same scale as what it covers. The
     * fitter then grows or shrinks from this starting point to fill the block.
     */
    private fun fontFor(target: CoverTarget, style: CoverStyle): Float {
        val fromSource = target.sourceLineHeight
        val base = if (fromSource > 0f) {
            // A line box is taller than the glyphs in it; ~0.78 of the box is the
            // actual text size. Bump it a little so a short translation expands to
            // fill the block instead of floating in the middle of it.
            fromSource * 0.78f * FILL_BOOST
        } else {
            target.rect.height * 0.55f
        }
        return (base * style.fontScale).coerceIn(dp(9f), dp(44f))
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

    /** Draws [fitted] from [top]; returns the height it consumed. */
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
        // Anchor to the top of the box rather than centring: when the block grew to
        // fit the text the two are equal, and when it did not, top alignment keeps
        // the first line where the source text started.
        var baseline = top - metrics.ascent
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
        /**
         * Line pitch as a multiple of the font size. Chinese glyphs are full-height
         * squares, so they need more air between lines than Latin text does; 1.15
         * reads as a solid block.
         */
        private const val LINE_SPACING = 1.35f

        /**
         * How far the translation may grow past the source line height to fill its
         * block. Chinese glyphs are denser than Latin, so a translation that matches
         * the source size can look smaller than the text it replaced.
         */
        private const val FILL_BOOST = 1.15f

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
