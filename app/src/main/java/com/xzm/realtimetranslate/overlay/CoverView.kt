package com.xzm.realtimetranslate.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/**
 * Full-screen, touch-transparent layer that paints a solid block over every
 * recognized text box and writes the translation inside it, so the screen reads
 * as if the original text had been replaced.
 *
 * Nothing here reacts to input: the window is added with FLAG_NOT_TOUCHABLE by
 * [CoverOverlayController] and this view never handles touch.
 */
class CoverView(context: Context) : View(context) {

    private var targets: List<CoverTarget> = emptyList()
    private var coverStyle: CoverStyle = CoverStyle()

    private val density = resources.displayMetrics.density
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
    private val lineSpacing = 1.15f

    fun setTargets(targets: List<CoverTarget>, style: CoverStyle) {
        this.targets = targets
        this.coverStyle = style
        invalidate()
    }

    fun clear() {
        targets = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        for (target in targets) drawTarget(canvas, target)
    }

    private fun drawTarget(canvas: Canvas, target: CoverTarget) {
        val block = RectF(
            target.rect.left - blockPad,
            target.rect.top - blockPad,
            target.rect.right + blockPad,
            target.rect.bottom + blockPad,
        )
        blockPaint.color = if (coverStyle.blockStyle == BLOCK_STYLE_DARK) {
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

        val maxFont = (target.rect.height * 0.55f * coverStyle.fontScale)
            .coerceIn(dp(8f), dp(28f))

        val original = target.original
        if (coverStyle.bilingual && !original.isNullOrBlank()) {
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
            lineHeight = { size -> size * lineSpacing },
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
        val lineHeight = fitted.fontSize * lineSpacing
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

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    companion object {
        const val BLOCK_STYLE_DARK = "DARK"
        private val DARK_BLOCK = 0xE6000000.toInt()
    }
}
