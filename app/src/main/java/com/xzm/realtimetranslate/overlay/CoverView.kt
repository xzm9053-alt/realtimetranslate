package com.xzm.realtimetranslate.overlay

import android.content.Context
import android.graphics.Canvas
import android.view.View

/**
 * Full-screen, touch-transparent layer that paints a solid block over every
 * recognized text box and writes the translation inside it, so the screen reads
 * as if the original text had been replaced.
 *
 * Nothing here reacts to input: the window is added with FLAG_NOT_TOUCHABLE by
 * [CoverOverlayController] and this view never handles touch. The actual painting
 * lives in [CoverPainter] because the image pipeline draws the same blocks into a
 * Bitmap and both must look identical.
 */
class CoverView(context: Context) : View(context) {

    private var targets: List<CoverTarget> = emptyList()
    private var coverStyle: CoverStyle = CoverStyle()

    private val painter = CoverPainter(resources.displayMetrics.density)

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
        painter.draw(canvas, targets, coverStyle)
    }

    companion object {
        const val BLOCK_STYLE_DARK = "DARK"
    }
}
