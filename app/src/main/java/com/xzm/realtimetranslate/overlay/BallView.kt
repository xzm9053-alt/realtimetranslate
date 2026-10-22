package com.xzm.realtimetranslate.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Shared rendering for the edge-snapped floating balls (audio fold-ball and the
 * screen-OCR ball). Draws a solid circle of fixed [diameterPx]; the enclosing
 * window decides the visible shape:
 *
 * - [SIDE_LEFT]  / [SIDE_RIGHT]: the window is only `diameterPx / 2` wide and flush
 *   against the screen edge, so the circle's centre sits ON the edge and the window
 *   clips away the hidden half → a true hemisphere. No negative coordinates, so MIUI
 *   can't push the whole ball on-screen.
 * - [SIDE_FLOAT]: the window is `diameterPx` square and the full circle is shown.
 *
 * No text glyphs — a hemisphere silhouette reads as a handle already, and
 * tiny-ball text rendering is a font bug magnet. The one exception is the paused
 * state, drawn as a plain triangle path (▶ = tap to resume) because a paused
 * session must be visible at a glance.
 */
class BallView(context: Context) : View(context) {

    /** Circle diameter in pixels. Changing it re-renders. */
    var diameterPx: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    /** One of [SIDE_LEFT], [SIDE_RIGHT], [SIDE_FLOAT]. Changing it re-renders. */
    var side: Int = SIDE_FLOAT
        set(value) {
            field = value
            invalidate()
        }

    /** Translation paused: dims the ball and shows a ▶ hint. Changing it re-renders. */
    var paused: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BALL_COLOR
    }

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PAUSED_GLYPH_COLOR
    }

    private val glyphPath = Path()

    override fun onDraw(canvas: Canvas) {
        val radius = diameterPx / 2f
        val cx = when (side) {
            SIDE_LEFT -> 0f
            SIDE_RIGHT -> width.toFloat()
            else -> width / 2f
        }
        val cy = height / 2f
        paint.alpha = if (paused) PAUSED_BALL_ALPHA else 255
        canvas.drawCircle(cx, cy, radius, paint)
        if (!paused) return
        // Hemisphere windows show only the half nearest the edge; keep the glyph
        // inside that visible half instead of centring it on the clipped circle.
        val gx = when (side) {
            SIDE_LEFT -> radius / 2f
            SIDE_RIGHT -> width - radius / 2f
            else -> width / 2f
        }
        val s = radius * 0.42f
        glyphPath.reset()
        glyphPath.moveTo(gx - s * 0.6f, cy - s)
        glyphPath.lineTo(gx + s, cy)
        glyphPath.lineTo(gx - s * 0.6f, cy + s)
        glyphPath.close()
        canvas.drawPath(glyphPath, glyphPaint)
    }

    companion object {
        const val SIDE_LEFT = 0
        const val SIDE_RIGHT = 1
        const val SIDE_FLOAT = 2
        /** Same look as the previous fold-ball: translucent near-black. */
        private const val BALL_COLOR = 0xE614141C.toInt()
        private const val PAUSED_BALL_ALPHA = 150
        private const val PAUSED_GLYPH_COLOR = 0xE6FFFFFF.toInt()
    }
}
