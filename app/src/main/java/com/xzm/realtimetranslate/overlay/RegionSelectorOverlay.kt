package com.xzm.realtimetranslate.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.xzm.realtimetranslate.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen overlay for picking the OCR capture region. Draws a dim mask over
 * the whole screen with a highlighted rectangle the user can drag, resize (from
 * the four corner handles) or redraw from scratch by dragging outside it.
 *
 * The window spans the full display with its origin at top-left, so the chosen
 * [Rect] is already in screen pixels — identical to the coordinates of the
 * 1:1 [com.xzm.realtimetranslate.ocr.ScreenTextCapturer] virtual display.
 */
class RegionSelectorOverlay(
    private val context: Context,
    private val onConfirm: (Rect) -> Unit,
    private val onCancel: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var selectionView: RegionSelectionView? = null

    fun show() {
        if (rootView != null) return
        val (screenW, screenH, _) = screenMetrics()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        layoutParams = params

        val density = context.resources.displayMetrics.density
        val root = FrameLayout(context)

        val sv = RegionSelectionView(context, screenW, screenH, density).apply {
            region = defaultRegion(screenW, screenH)
        }
        selectionView = sv
        root.addView(sv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        // Bottom-center action buttons, layered above the selection view.
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
            ).apply {
                bottomMargin = (24 * density).roundToInt()
            }
        }

        val cancelBtn = actionButton(
            context,
            context.getString(R.string.ocr_cancel),
            Color.rgb(70, 74, 86),
            density,
        )
        val startBtn = actionButton(
            context,
            context.getString(R.string.ocr_start),
            ACCENT,
            density,
        )
        val gap = (16 * density).roundToInt()
        buttonRow.addView(cancelBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        buttonRow.addView(
            startBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = gap },
        )

        cancelBtn.setOnClickListener {
            hide()
            onCancel()
        }
        startBtn.setOnClickListener {
            val sv = selectionView ?: return@setOnClickListener
            sv.enforceMinSize()
            val r = Rect(sv.region)
            hide()
            onConfirm(r)
        }

        root.addView(buttonRow)
        rootView = root
        windowManager.addView(root, params)
    }

    fun hide() {
        val view = rootView ?: return
        runCatching { windowManager.removeView(view) }
        rootView = null
        layoutParams = null
        selectionView = null
    }

    private fun screenMetrics(): Triple<Int, Int, Float> {
        val density = context.resources.displayMetrics.density
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), density)
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val real = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(real)
            Triple(real.widthPixels, real.heightPixels, density)
        }
    }

    private fun defaultRegion(screenW: Int, screenH: Int): Rect {
        val w = (screenW * 0.72f).roundToInt()
        val h = (screenH * 0.32f).roundToInt()
        val l = (screenW - w) / 2
        val t = (screenH - h) / 2
        return Rect(l, t, l + w, t + h)
    }

    private fun actionButton(context: Context, text: String, color: Int, density: Float): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
            setPadding((20 * density).roundToInt(), (10 * density).roundToInt(),
                (20 * density).roundToInt(), (10 * density).roundToInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = (10 * density)
                setColor(color)
            }
            elevation = (4 * density)
        }

    /** The interactive mask / selection view. Coordinates are window (= screen) px. */
    private class RegionSelectionView(
        context: Context,
        private val screenW: Int,
        private val screenH: Int,
        density: Float,
    ) : View(context) {

        var region: Rect = Rect()

        private val dimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
        private val fillPaint = Paint().apply { color = Color.argb(45, 255, 255, 255) }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (2 * density)
            color = ACCENT
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            style = Paint.Style.FILL
        }
        private val handleOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (1.5f * density)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 15f, context.resources.displayMetrics,
            )
            textAlign = Paint.Align.CENTER
        }

        private val handleRadius = (12 * density).roundToInt()
        private val hitRadius = (30 * density).roundToInt()

        private var mode = MODE_NONE
        private var downX = 0
        private var downY = 0
        private var startRegion = Rect()
        private var dragOffsetX = 0
        private var dragOffsetY = 0
        private var corner = -1

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(dimPaint.color)
            canvas.drawRect(region, fillPaint)
            canvas.drawRect(region, borderPaint)
            for (i in 0 until 4) {
                val (cx, cy) = cornerOf(i)
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), handleRadius.toFloat(), handlePaint)
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), handleRadius.toFloat(), handleOutline)
            }
            val hint = context.getString(R.string.ocr_region_hint)
            canvas.drawText(hint, width / 2f, (56 * resources.displayMetrics.density), textPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val x = event.x.roundToInt()
                    val y = event.y.roundToInt()
                    corner = hitCorner(x, y)
                    mode = when {
                        corner >= 0 -> MODE_RESIZE
                        region.contains(x, y) -> {
                            dragOffsetX = x - region.left
                            dragOffsetY = y - region.top
                            MODE_DRAG
                        }
                        else -> MODE_DRAW
                    }
                    startRegion.set(region)
                    downX = x
                    downY = y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = event.x.roundToInt().coerceIn(0, screenW)
                    val y = event.y.roundToInt().coerceIn(0, screenH)
                    when (mode) {
                        MODE_DRAG -> {
                            val w = region.width()
                            val h = region.height()
                            val l = (x - dragOffsetX).coerceIn(0, max(0, screenW - w))
                            val t = (y - dragOffsetY).coerceIn(0, max(0, screenH - h))
                            region.set(l, t, l + w, t + h)
                        }
                        MODE_RESIZE -> resizeFromCorner(corner, x, y)
                        MODE_DRAW -> {
                            val l = min(startRegion.left, x).coerceIn(0, screenW)
                            val t = min(startRegion.top, y).coerceIn(0, screenH)
                            val r = max(startRegion.left, x).coerceIn(0, screenW)
                            val b = max(startRegion.top, y).coerceIn(0, screenH)
                            region.set(l, t, r, b)
                        }
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    enforceMinSize()
                    mode = MODE_NONE
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun resizeFromCorner(corner: Int, x: Int, y: Int) {
            val r = region
            val minW = MIN_REGION_PX
            val minH = MIN_REGION_PX
            when (corner) {
                0 -> { // top-left
                    r.left = min(x, r.right - minW).coerceAtLeast(0)
                    r.top = min(y, r.bottom - minH).coerceAtLeast(0)
                }
                1 -> { // top-right
                    r.right = max(x, r.left + minW).coerceAtMost(screenW)
                    r.top = min(y, r.bottom - minH).coerceAtLeast(0)
                }
                2 -> { // bottom-left
                    r.left = min(x, r.right - minW).coerceAtLeast(0)
                    r.bottom = max(y, r.top + minH).coerceAtMost(screenH)
                }
                else -> { // bottom-right
                    r.right = max(x, r.left + minW).coerceAtMost(screenW)
                    r.bottom = max(y, r.top + minH).coerceAtMost(screenH)
                }
            }
        }

        private fun hitCorner(x: Int, y: Int): Int {
            for (i in 0 until 4) {
                val (cx, cy) = cornerOf(i)
                if (abs(x - cx) <= hitRadius && abs(y - cy) <= hitRadius) return i
            }
            return -1
        }

        private fun cornerOf(i: Int): Pair<Int, Int> = when (i) {
            0 -> region.left to region.top
            1 -> region.right to region.top
            2 -> region.left to region.bottom
            else -> region.right to region.bottom
        }

        /** Called before confirming: guarantee a usable minimum-sized selection. */
        fun enforceMinSize() {
            val w = region.width()
            val h = region.height()
            if (w >= MIN_REGION_PX && h >= MIN_REGION_PX) return
            region.set(
                region.left.coerceIn(0, max(0, screenW - MIN_REGION_PX)),
                region.top.coerceIn(0, max(0, screenH - MIN_REGION_PX)),
                region.left.coerceIn(0, max(0, screenW - MIN_REGION_PX)) + max(w, MIN_REGION_PX),
                region.top.coerceIn(0, max(0, screenH - MIN_REGION_PX)) + max(h, MIN_REGION_PX),
            )
        }

        companion object {
            private const val MODE_NONE = 0
            private const val MODE_DRAG = 1
            private const val MODE_RESIZE = 2
            private const val MODE_DRAW = 3
            private const val MIN_REGION_PX = 48
        }
    }

    companion object {
        private const val ACCENT = 0xFF2E7CF6.toInt()
    }
}
