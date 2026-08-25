package com.xzm.realtimetranslate.overlay

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.util.realScreenMetrics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Screen-OCR presentation: an edge-snapped hemisphere ball plus a translation
 * bubble that pops out of the ball. Independent of the audio subtitle overlay.
 *
 * Three windows:
 * - **ball window**: draggable hemisphere (SIDE_LEFT / SIDE_RIGHT / SIDE_FLOAT
 *   state machine). Tapping it fires [onTap] — the service re-opens the region
 *   selector to pick a new capture area.
 * - **popup window**: the latest translation anchored beside the ball, with a ✕
 *   close button. Stays until closed or a new sentence arrives (which re-opens
 *   it); streaming updates to the same sentence only re-paint the open bubble.
 * - **region outline window**: a subtle, touch-transparent outline of the OCR
 *   capture area, kept on screen so the user can see which part is being
 *   translated. Managed via [showRegionOutline] / [hideRegionOutline].
 */
class OcrBallOverlay(
    private val context: Context,
    private val onTap: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var ballWindowView: FrameLayout? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var ballView: BallView? = null

    private var popupWindowView: FrameLayout? = null
    private var popupParams: WindowManager.LayoutParams? = null
    private var popupText: TextView? = null

    private var outlineWindowView: FrameLayout? = null
    private var outlineView: RegionOutlineView? = null
    private var lastRegion: Rect? = null

    private var settings: UserSettings = UserSettings()

    // Ball centre in screen px; `side` decides the window geometry around it.
    private var side = BallView.SIDE_RIGHT
    private var centerX = 0
    private var centerY = 0

    // Popup state.
    private var popupVisible = false
    private var popupUserClosed = false
    private var shownInput = ""
    private var shownOutput = ""
    private var lastInput = ""

    private val touchSlopPx: Int = ViewConfiguration.get(context).scaledTouchSlop

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            runCatching { reclamp() }
        }

        override fun onLowMemory() = Unit
    }
    private var callbacksRegistered = false

    fun show(initial: UserSettings) {
        if (ballWindowView != null) return
        settings = initial
        val (screenW, screenH, _) = screenMetrics()
        val size = sizePx()

        // Default: parked on the right edge, upper-middle of the screen.
        side = BallView.SIDE_RIGHT
        centerX = screenW
        centerY = (screenH * 0.35f).roundToInt()

        val params = overlayParams(size, size)
        ballParams = params

        val ball = BallView(context).apply {
            diameterPx = size
            side = this@OcrBallOverlay.side
            setOnTouchListener(BallTouch())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        ballView = ball
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(ball)
        }
        ballWindowView = root
        windowManager.addView(root, params)
        registerCallbacks()
        applyGeometry()
    }

    fun updateSettings(value: UserSettings) {
        settings = value
        // Capture-area outline follows the appearance settings live.
        val region = lastRegion
        if (region != null) {
            if (value.ocrRegionOutlineEnabled) {
                showRegionOutline(region)
            } else {
                hideRegionOutline()
            }
        }
        val tv = popupText ?: return
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, clampedFontSp(value.fontSizeSp))
        tv.setTextColor(value.translationTextColor.toInt())
        runCatching { movePopup() }
    }

    /** Feeds translation results. Only the [output] is displayed (the OCR region
     *  already shows the source text on screen). */
    fun updateTranscripts(input: String?, output: String?) {
        if (ballWindowView == null) return
        if (input != null) lastInput = input
        if (output == null) return // nothing to show yet
        val text = output
        if (text == shownOutput && lastInput == shownInput) return
        val isNewSentence = lastInput != shownInput
        shownInput = lastInput
        shownOutput = text
        ensurePopup()
        popupText?.text = text
        if (isNewSentence) {
            showPopup() // new sentence always re-opens, even after a manual close
        } else if (popupVisible) {
            movePopup() // streaming update of the already-visible bubble
        }
    }

    /** Repositions after rotation / display change; keeps the ball on-screen. */
    fun reclamp() {
        if (ballWindowView == null) return
        val (screenW, screenH, _) = screenMetrics()
        val size = sizePx()
        if (side != BallView.SIDE_FLOAT) {
            centerX = if (side == BallView.SIDE_LEFT) 0 else screenW
        }
        centerY = centerY.coerceIn(size / 2, max(size / 2, screenH - size / 2))
        applyGeometry()
        movePopup()
    }

    fun hide() {
        unregisterCallbacks()
        popupVisible = false
        popupUserClosed = false
        popupWindowView?.let { runCatching { windowManager.removeView(it) } }
        popupWindowView = null
        popupParams = null
        popupText = null
        hideRegionOutline()
        ballWindowView?.let { runCatching { windowManager.removeView(it) } }
        ballWindowView = null
        ballParams = null
        ballView = null
        side = BallView.SIDE_RIGHT
        shownInput = ""
        shownOutput = ""
        lastInput = ""
    }

    // ---- ball window ----

    private fun applyGeometry() {
        val params = ballParams ?: return
        val root = ballWindowView ?: return
        val (screenW, screenH, _) = screenMetrics()
        val size = sizePx()
        when (side) {
            BallView.SIDE_LEFT -> {
                params.width = size / 2
                params.height = size
                params.x = 0
            }
            BallView.SIDE_RIGHT -> {
                params.width = size / 2
                params.height = size
                params.x = screenW - size / 2
            }
            else -> {
                params.width = size
                params.height = size
                params.x = (centerX - size / 2).coerceIn(0, max(0, screenW - size))
            }
        }
        params.y = (centerY - size / 2).coerceIn(0, max(0, screenH - size))
        ballView?.side = side
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class BallTouch : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var grabDx = 0f
        private var grabDy = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (ballWindowView == null) return false
            return try {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        dragging = false
                        grabDx = event.rawX - centerX
                        grabDy = event.rawY - centerY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging &&
                            (abs(event.rawX - downX) > touchSlopPx ||
                                abs(event.rawY - downY) > touchSlopPx)
                        ) {
                            dragging = true
                            // Pull it off the edge — the full circle reveals itself.
                            side = BallView.SIDE_FLOAT
                            applyGeometry()
                        }
                        if (dragging) {
                            centerX = (event.rawX - grabDx).roundToInt()
                            centerY = (event.rawY - grabDy).roundToInt()
                            applyGeometry()
                            movePopup()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            runCatching { onTap() }
                        } else {
                            val (screenW, _, _) = screenMetrics()
                            val size = sizePx()
                            centerX = (event.rawX - grabDx).roundToInt()
                            centerY = (event.rawY - grabDy).roundToInt()
                            side = when {
                                centerX <= size * 3 / 4 -> BallView.SIDE_LEFT
                                centerX >= screenW - size * 3 / 4 -> BallView.SIDE_RIGHT
                                else -> BallView.SIDE_FLOAT
                            }
                            if (side == BallView.SIDE_LEFT) centerX = 0
                            if (side == BallView.SIDE_RIGHT) centerX = screenW
                            applyGeometry()
                            movePopup()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                        true
                    }
                    else -> false
                }
            } catch (t: Throwable) {
                Log.e(TAG, "ball touch failed", t)
                true
            }
        }
    }

    // ---- popup window ----

    private fun ensurePopup() {
        if (popupWindowView != null) return
        val density = context.resources.displayMetrics.density
        val (_, screenH, _) = screenMetrics()
        val maxPopupHeightPx = (screenH * POPUP_MAX_HEIGHT_FRACTION).roundToInt()

        val tv = TextView(context).apply {
            typeface = Typeface.DEFAULT
            setLineSpacing(0f, 1.15f)
            // No hard line cap: the bubble grows up to maxHeight and scrolls
            // beyond it, so long translations stay fully readable.
            movementMethod = ScrollingMovementMethod()
            maxHeight = maxPopupHeightPx
            isVerticalScrollBarEnabled = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, clampedFontSp(settings.fontSizeSp))
            setTextColor(settings.translationTextColor.toInt())
            text = ""
            setPadding(0, 0, (24 * density).roundToInt(), 0)
        }
        popupText = tv

        val close = TextView(context).apply {
            text = "✕"
            setTextColor(Color.argb(170, 255, 255, 255))
            textSize = 12f
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.ocr_popup_close)
            setOnClickListener { closePopup() }
            layoutParams = FrameLayout.LayoutParams(
                (26 * density).roundToInt(),
                (26 * density).roundToInt(),
                Gravity.TOP or Gravity.END,
            )
        }

        val padH = (12 * density).roundToInt()
        val padV = (8 * density).roundToInt()
        val card = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(Color.argb(236, 20, 20, 28))
            }
            setPadding(padH, padV, padH, padV)
            addView(
                tv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(close)
        }
        popupWindowView = card
        popupParams = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun showPopup() {
        ensurePopup()
        val view = popupWindowView ?: return
        val params = popupParams ?: return
        if (view.parent == null) {
            windowManager.addView(view, params)
        }
        movePopup()
        view.animate().cancel()
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(160).start()
        popupVisible = true
        popupUserClosed = false
    }

    private fun closePopup() {
        popupUserClosed = true
        popupVisible = false
        val view = popupWindowView ?: return
        runCatching { windowManager.removeView(view) }
        popupWindowView = null
        popupParams = null
        popupText = null
    }

    /** Anchors the bubble beside the ball, choosing the side with more room.
     *  Never lets the bubble cover the ball so it stays draggable: if the bubble
     *  is too wide for either side (only when the ball floats mid-screen and the
     *  translation is long), it parks below or above the ball instead. */
    private fun movePopup() {
        val view = popupWindowView ?: return
        val params = popupParams ?: return
        if (view.parent == null) return
        val (screenW, screenH, density) = screenMetrics()
        val maxW = (screenW * 0.7f).roundToInt()
        val maxH = (screenH * POPUP_MAX_HEIGHT_FRACTION).roundToInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST),
        )
        val pw = view.measuredWidth
        val ph = view.measuredHeight
        val size = sizePx()
        val margin = (12 * density).roundToInt()
        val ballLeft = centerX - size / 2
        val ballRight = centerX + size / 2
        val ballTop = centerY - size / 2
        val ballBottom = centerY + size / 2
        val rightRoom = screenW - ballRight - margin
        val leftRoom = ballLeft - margin
        val centeredY = (centerY - ph / 2).coerceIn(margin, max(margin, screenH - ph - margin))
        val (x, y) = when {
            pw <= rightRoom -> ballRight + margin to centeredY
            pw <= leftRoom -> ballLeft - margin - pw to centeredY
            else -> {
                // Too wide to sit beside the ball: drop below it (or above) on the
                // side with more room, so the ball is never covered.
                val xv = (if (rightRoom >= leftRoom) screenW - pw else 0)
                    .coerceIn(0, max(0, screenW - pw))
                val yv = when {
                    ph <= screenH - ballBottom - margin -> ballBottom + margin
                    ph <= ballTop - margin -> ballTop - margin - ph
                    else -> margin
                }
                xv to yv
            }
        }
        params.x = x
        params.y = y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    // ---- region outline window ----

    /** Shows (or moves) the subtle outline of the OCR capture area. Touch-transparent.
     *  No-ops (and hides) when the user disabled it in settings. Always re-applies
     *  the appearance so color/opacity changes take effect live. */
    fun showRegionOutline(rect: Rect) {
        lastRegion = Rect(rect)
        if (!settings.ocrRegionOutlineEnabled) {
            hideRegionOutline()
            return
        }
        if (outlineView == null) createOutlineWindow()
        outlineView?.apply {
            setRegion(rect)
            applyAppearance(
                settings.ocrRegionOutlineColor.toInt(),
                settings.ocrRegionOutlineAlpha,
            )
        }
    }

    fun hideRegionOutline() {
        outlineWindowView?.let { runCatching { windowManager.removeView(it) } }
        outlineWindowView = null
        outlineView = null
    }

    private fun createOutlineWindow() {
        val view = RegionOutlineView(context).apply {
            applyAppearance(
                settings.ocrRegionOutlineColor.toInt(),
                settings.ocrRegionOutlineAlpha,
            )
        }
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        outlineView = view
        outlineWindowView = root
        windowManager.addView(root, params)
    }

    /** Draws only a faint rounded box at the capture region; no dim, no handles,
     *  no hint text — just enough to know where the OCR area is. */
    private class RegionOutlineView(context: Context) : View(context) {
        /** Region in physical screen coords; converted to this view's coords in onDraw. */
        private val region = Rect()
        private val rectF = RectF()

        private val density = context.resources.displayMetrics.density
        private val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.argb(18, 255, 255, 255)
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (1.5f * density)
            color = Color.argb(130, 46, 124, 246)
        }
        private val cornerRadius = 8 * density

        fun setRegion(r: Rect) {
            region.set(r)
            invalidate()
        }

        /** Applies the user's outline color + opacity ("明显程度"). [colorArgb] is
         *  opaque — its alpha byte is overridden by [alpha]. */
        fun applyAppearance(colorArgb: Int, alpha: Float) {
            val a = (alpha.coerceIn(0.05f, 1f) * 255).toInt()
            borderPaint.color = (a shl 24) or (colorArgb and 0x00FFFFFF)
            fillPaint.color = Color.argb((a * 0.15f).roundToInt(), 255, 255, 255)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Physical coords → this view's coords: if the outline window is inset
            // below the status bar, subtract its on-screen origin so the box stays
            // aligned with where the user drew it.
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            rectF.set(
                (region.left - loc[0]).toFloat(),
                (region.top - loc[1]).toFloat(),
                (region.right - loc[0]).toFloat(),
                (region.bottom - loc[1]).toFloat(),
            )
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)
        }
    }

    // ---- helpers ----

    private fun clampedFontSp(sp: Float): Float = sp.coerceIn(MIN_FONT_SP, MAX_FONT_SP)

    private fun sizePx(): Int = (BALL_SIZE_DP * context.resources.displayMetrics.density).roundToInt()

    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            w,
            h,
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

    private fun screenMetrics(): Triple<Int, Int, Float> = context.realScreenMetrics()

    private fun registerCallbacks() {
        if (callbacksRegistered) return
        runCatching { context.applicationContext.registerComponentCallbacks(configCallbacks) }
        callbacksRegistered = true
    }

    private fun unregisterCallbacks() {
        if (!callbacksRegistered) return
        runCatching { context.applicationContext.unregisterComponentCallbacks(configCallbacks) }
        callbacksRegistered = false
    }

    companion object {
        private const val TAG = "OcrBallOverlay"
        private const val BALL_SIZE_DP = 56
        private const val MIN_FONT_SP = 12f
        private const val MAX_FONT_SP = 28f
        /** Bubble max height as a fraction of screen height; overflow scrolls. */
        private const val POPUP_MAX_HEIGHT_FRACTION = 0.55f
    }
}
