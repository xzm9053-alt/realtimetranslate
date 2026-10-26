package com.xzm.realtimetranslate.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

/**
 * Shared rendering for the edge-snapped floating balls (hub ball, audio fold-ball, and
 * the screen-OCR ball).
 *
 * Visual design:
 * - Translucent frosted dark glass sphere with ambient diffuse refraction and subtle specular rim.
 * - Center glowing concave 4-point sparkle star with prismatic chromatic aberration:
 *   warm amber/orange dispersion on upper-left, electric cyan-blue on lower-left, pure white core.
 * - [SIDE_LEFT] / [SIDE_RIGHT]: true hemisphere window flush against screen edge.
 *   Icon remains perfectly centered in the visible semicircle.
 * - [SIDE_FLOAT]: full circle.
 */
class BallView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    /** Circle diameter in pixels. Changing it re-renders. */
    var diameterPx: Int = 0
        set(value) {
            field = value
            shadersNeedUpdate = true
            invalidate()
        }

    /** One of [SIDE_LEFT], [SIDE_RIGHT], [SIDE_FLOAT]. Changing it re-renders. */
    var side: Int = SIDE_FLOAT
        set(value) {
            field = value
            shadersNeedUpdate = true
            invalidate()
        }

    /** Translation paused: dims the ball and shows a ▶ hint. Changing it re-renders. */
    var paused: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var shadersNeedUpdate = true
    private var lastWidth = 0
    private var lastHeight = 0

    // Paints
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val warmDispersionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DISPERSION_WARM_COLOR
        style = Paint.Style.FILL
    }
    private val coolDispersionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DISPERSION_COOL_COLOR
        style = Paint.Style.FILL
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val pausePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF0FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    // Paths
    private val starPath = Path()
    private val warmPath = Path()
    private val coolPath = Path()
    private val pausePath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shadersNeedUpdate = true
    }

    private fun updateShaders(cx: Float, cy: Float, radius: Float) {
        if (radius <= 0f) return
        // Frosted dark acrylic glass radial gradient:
        // Top-left ambient reflection into deep dark glass body
        glassPaint.shader = RadialGradient(
            cx - radius * 0.30f,
            cy - radius * 0.30f,
            radius * 1.35f,
            intArrayOf(
                0xD8323644.toInt(), // Top-left frosted highlight
                0xE01E202B.toInt(), // Mid body
                0xEB13141C.toInt(), // Deep shadow
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )

        // Fine specular rim light:
        rimPaint.shader = LinearGradient(
            cx,
            cy - radius,
            cx,
            cy + radius,
            intArrayOf(
                0x70FFFFFF.toInt(), // Top specular white edge
                0x25A0B4D8.toInt(), // Side soft sheen
                0x18FFFFFF.toInt(), // Bottom subtle bounce light
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        shadersNeedUpdate = false
    }

    override fun onDraw(canvas: Canvas) {
        val radius = diameterPx / 2f
        if (radius <= 0f) return

        val cx = when (side) {
            SIDE_LEFT -> 0f
            SIDE_RIGHT -> width.toFloat()
            else -> width / 2f
        }
        val cy = height / 2f

        if (shadersNeedUpdate || width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            updateShaders(cx, cy, radius)
        }

        val baseAlpha = if (paused) PAUSED_BALL_ALPHA else 255
        glassPaint.alpha = baseAlpha
        rimPaint.alpha = baseAlpha

        // 1. Draw frosted dark glass sphere
        canvas.drawCircle(cx, cy, radius, glassPaint)

        // 2. Draw delicate specular rim stroke
        val rimInset = rimPaint.strokeWidth / 2f
        canvas.drawCircle(cx, cy, radius - rimInset, rimPaint)

        // Determine icon center point within the visible area
        val gx = when (side) {
            SIDE_LEFT -> radius * 0.44f
            SIDE_RIGHT -> width - radius * 0.44f
            else -> width / 2f
        }

        if (paused) {
            // Draw pause / resume icon
            val s = radius * 0.38f
            pausePath.reset()
            pausePath.moveTo(gx - s * 0.55f, cy - s)
            pausePath.lineTo(gx + s * 0.85f, cy)
            pausePath.lineTo(gx - s * 0.55f, cy + s)
            pausePath.close()
            canvas.drawPath(pausePath, pausePaint)
            return
        }

        // 3. Draw 4-point sparkle star with prismatic chromatic aberration
        val starR = if (side == SIDE_FLOAT) radius * 0.38f else radius * 0.30f
        val offset = 1.35f * density

        // 3a. Warm chromatic dispersion flare (orange-amber on upper-left)
        buildStarPath(warmPath, gx - offset * 1.1f, cy - offset * 0.6f, starR * 1.08f)
        canvas.drawPath(warmPath, warmDispersionPaint)

        // 3b. Cool chromatic dispersion flare (cyan-blue on lower-left)
        buildStarPath(coolPath, gx - offset * 0.9f, cy + offset * 1.1f, starR * 1.05f)
        canvas.drawPath(coolPath, coolDispersionPaint)

        // 3c. Pure white diamond star core
        buildStarPath(starPath, gx, cy, starR)
        canvas.drawPath(starPath, starPaint)
    }

    /**
     * Builds a concave 4-point diamond star path with smooth waist indentations.
     */
    private fun buildStarPath(path: Path, sx: Float, sy: Float, outerR: Float) {
        path.reset()
        val waist = outerR * 0.22f
        val rx = outerR * 0.92f
        val ry = outerR

        path.moveTo(sx, sy - ry) // Top tip
        path.quadTo(sx + waist, sy - waist, sx + rx, sy) // Top -> Right
        path.quadTo(sx + waist, sy + waist, sx, sy + ry) // Right -> Bottom
        path.quadTo(sx - waist, sy + waist, sx - rx, sy) // Bottom -> Left
        path.quadTo(sx - waist, sy - waist, sx, sy - ry) // Left -> Top
        path.close()
    }

    companion object {
        const val SIDE_LEFT = 0
        const val SIDE_RIGHT = 1
        const val SIDE_FLOAT = 2

        private const val PAUSED_BALL_ALPHA = 140

        // Prismatic dispersion colors
        private const val DISPERSION_WARM_COLOR = 0xD8FF8C38.toInt() // Radiant amber-orange
        private const val DISPERSION_COOL_COLOR = 0xD000B0FF.toInt() // Electric cyan-blue
    }
}
