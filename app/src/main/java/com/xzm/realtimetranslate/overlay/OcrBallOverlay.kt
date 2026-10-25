package com.xzm.realtimetranslate.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.method.ScrollingMovementMethod
import com.xzm.realtimetranslate.util.AppLog as Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.util.RegionBounds
import com.xzm.realtimetranslate.util.RegionGestureMath
import com.xzm.realtimetranslate.util.realScreenMetrics
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Screen-OCR presentation: an edge-snapped hemisphere ball plus a translation
 * bubble that pops out of the ball. Independent of the audio subtitle overlay.
 *
 * Windows:
 * - **ball window**: draggable hemisphere (SIDE_LEFT / SIDE_RIGHT / SIDE_FLOAT
 *   state machine). Tap toggles pause/resume ([onTogglePause]); long-press opens
 *   the action menu (pause, reselect the capture area, toggle the outline, stop).
 * - **popup window**: the latest translation anchored beside the ball, with a ✕
 *   close button. Stays until closed or a new sentence arrives (which re-opens
 *   it); streaming updates to the same sentence only re-paint the open bubble.
 *   Drag it anywhere on screen — once moved it stops following the ball and
 *   stays where the user put it.
 * - **menu window**: the long-press action menu; a transparent full-screen scrim
 *   so tapping anywhere outside dismisses it.
 * - **region outline window**: a subtle, touch-transparent outline of the OCR
 *   capture area, kept on screen so the user can see which part is being
 *   translated. Managed via [showRegionOutline] / [hideRegionOutline].
 */
class OcrBallOverlay(
    private val context: Context,
    private val onTogglePause: () -> Unit,
    private val onReselectRegion: () -> Unit,
    private val onStop: () -> Unit,
    /** 拖动识别框右下角圆点结束后回调，参数是落定后的新选区。 */
    private val onRegionMoved: (Rect) -> Unit,
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

    /** 识别框右下角的拖动圆点：独立的小窗口，只有圆点本身吃触摸。 */
    private var regionHandleWindow: FrameLayout? = null
    private var regionHandleParams: WindowManager.LayoutParams? = null
    private var regionHandleDot: View? = null
    private var draggingRegion = false

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

    // Once the user drags the bubble it stops following the ball and keeps these
    // top-left coords (clamped to the screen on every layout pass).
    private var popupPinned = false
    private var popupX = 0
    private var popupY = 0
    private var handleOwnsGesture = false

    private var menuWindowView: FrameLayout? = null

    private val touchSlopPx: Int = ViewConfiguration.get(context).scaledTouchSlop

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            runCatching { reclamp() }
        }

        override fun onLowMemory() = Unit
    }
    private var callbacksRegistered = false

    var hubModeEnabled: Boolean = false
        set(value) {
            field = value
            if (value) {
                ballWindowView?.visibility = View.GONE
            } else {
                ballWindowView?.visibility = View.VISIBLE
            }
        }

    fun triggerToggleOutline() {
        toggleRegionOutline()
    }

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
        if (hubModeEnabled || com.xzm.realtimetranslate.service.HubBus.state.value.isHubEnabled) {
            hubModeEnabled = true
            root.visibility = View.GONE
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

    /** Pause state is owned by the service (it drives the capture loop); the ball
     *  only mirrors it, so a paused session is recognisable at a glance. */
    fun setPaused(value: Boolean) {
        ballView?.paused = value
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
        hideMenu()
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
        hideMenu()
        popupVisible = false
        popupUserClosed = false
        popupPinned = false
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

    private fun triggerSnapHaptic() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(20L)
                }
            }
        }
    }

    // ---- long-press menu ----

    private fun showMenu() {
        hideMenu()
        if (ballWindowView == null) return
        val density = context.resources.displayMetrics.density
        val (screenW, screenH, _) = screenMetrics()
        val size = sizePx()
        val margin = (12 * density).roundToInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true // taps on the card itself must not dismiss the menu
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(Color.argb(243, 22, 22, 30))
            }
            setPadding(
                (6 * density).roundToInt(),
                (6 * density).roundToInt(),
                (6 * density).roundToInt(),
                (6 * density).roundToInt(),
            )
        }
        val itemW = (168 * density).roundToInt()
        val itemH = (42 * density).roundToInt()
        fun addItem(label: String, action: () -> Unit) {
            card.addView(
                TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * density).roundToInt(), 0, (14 * density).roundToInt(), 0)
                    layoutParams = LinearLayout.LayoutParams(itemW, itemH)
                    setOnClickListener {
                        hideMenu()
                        runCatching { action() }
                    }
                },
            )
        }

        val paused = ballView?.paused == true
        addItem(
            context.getString(if (paused) R.string.ocr_menu_resume else R.string.ocr_menu_pause),
        ) { onTogglePause() }
        addItem(context.getString(R.string.ocr_menu_reselect)) { onReselectRegion() }
        addItem(
            context.getString(
                if (outlineWindowView != null) R.string.ocr_menu_hide_outline
                else R.string.ocr_menu_show_outline,
            ),
        ) { toggleRegionOutline() }
        addItem(context.getString(R.string.ocr_menu_stop)) { onStop() }

        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        card.measure(unspecified, unspecified)
        val cw = card.measuredWidth
        val ch = card.measuredHeight
        // Open on whichever side of the ball has room, vertically centred on it.
        val ballLeft = centerX - size / 2
        val ballRight = centerX + size / 2
        val x = if (ballLeft - margin - cw >= 0) {
            ballLeft - margin - cw
        } else {
            (ballRight + margin).coerceIn(0, max(0, screenW - cw))
        }
        val y = (centerY - ch / 2).coerceIn(margin, max(margin, screenH - ch - margin))
        card.layoutParams = FrameLayout.LayoutParams(cw, ch).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = x
            topMargin = y
        }

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { hideMenu() } // transparent scrim: tap outside to dismiss
            addView(card)
        }
        menuWindowView = root
        runCatching { windowManager.addView(root, overlayParams(screenW, screenH)) }
    }

    private fun hideMenu() {
        menuWindowView?.let { runCatching { windowManager.removeView(it) } }
        menuWindowView = null
    }

    private fun toggleRegionOutline() {
        val region = lastRegion ?: return
        if (outlineWindowView != null) {
            hideRegionOutline()
            Toast.makeText(context, "已隐藏取词框（长按悬浮球可恢复）", Toast.LENGTH_SHORT).show()
        } else {
            showRegionOutline(region)
            Toast.makeText(context, "已显示取词框", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class BallTouch : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var grabDx = 0f
        private var grabDy = 0f
        private var longPressTriggered = false

        private val longPressRunnable = Runnable {
            if (!dragging) {
                longPressTriggered = true
                showMenu()
                triggerSnapHaptic()
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (ballWindowView == null) return false
            return try {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        dragging = false
                        longPressTriggered = false
                        grabDx = event.rawX - centerX
                        grabDy = event.rawY - centerY
                        v.postDelayed(longPressRunnable, 500L)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging &&
                            (abs(event.rawX - downX) > touchSlopPx ||
                                abs(event.rawY - downY) > touchSlopPx)
                        ) {
                            dragging = true
                            v.removeCallbacks(longPressRunnable)
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
                        v.removeCallbacks(longPressRunnable)
                        if (longPressTriggered) {
                            true
                        } else if (!dragging) {
                            runCatching { onTogglePause() }
                            true
                        } else {
                            val (screenW, _, _) = screenMetrics()
                            val size = sizePx()
                            centerX = (event.rawX - grabDx).roundToInt()
                            centerY = (event.rawY - grabDy).roundToInt()
                            val prevSide = side
                            side = when {
                                centerX <= size * 3 / 4 -> BallView.SIDE_LEFT
                                centerX >= screenW - size * 3 / 4 -> BallView.SIDE_RIGHT
                                else -> BallView.SIDE_FLOAT
                            }
                            if (side == BallView.SIDE_LEFT) centerX = 0
                            if (side == BallView.SIDE_RIGHT) centerX = screenW
                            applyGeometry()
                            if (side != BallView.SIDE_FLOAT && side != prevSide) {
                                triggerSnapHaptic()
                            }
                            movePopup()
                            true
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.removeCallbacks(longPressRunnable)
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
            setPadding(0, 0, (56 * density).roundToInt(), 0)
        }
        popupText = tv

        val copy = TextView(context).apply {
            text = "📋"
            textSize = 12f
            gravity = Gravity.CENTER
            contentDescription = "复制译文"
            setOnClickListener {
                val textToCopy = shownOutput.ifBlank { shownInput }
                if (textToCopy.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OCR Translation", textToCopy))
                    Toast.makeText(context, "已复制译文", Toast.LENGTH_SHORT).show()
                }
            }
            layoutParams = FrameLayout.LayoutParams(
                (26 * density).roundToInt(),
                (26 * density).roundToInt(),
                Gravity.TOP or Gravity.END,
            ).apply {
                marginEnd = (28 * density).roundToInt()
            }
        }

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

        // Explicit move grip: a small dot in the bottom-right corner. Dragging it
        // moves the bubble in real time; the body stays draggable too, so the dot
        // is an affordance rather than the only way in.
        val handleSize = (18 * density).roundToInt()
        val dragHandle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(120, 255, 255, 255))
            }
            contentDescription = context.getString(R.string.ocr_popup_drag)
            layoutParams = FrameLayout.LayoutParams(
                handleSize,
                handleSize,
                Gravity.BOTTOM or Gravity.END,
            ).apply {
                bottomMargin = (5 * density).roundToInt()
                marginEnd = (5 * density).roundToInt()
            }
            setOnTouchListener(MoveHandleTouch())
        }

        val padH = (12 * density).roundToInt()
        val padV = (8 * density).roundToInt()
        val card = DragFrame(context).apply {
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
            addView(copy)
            addView(close)
            addView(dragHandle)
        }
        popupWindowView = card
        popupParams = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    /**
     * The translation bubble card. Dragging it moves its window; taps still reach
     * the copy/close buttons, and a vertical drag over text that still has room to
     * scroll scrolls the text instead of moving the bubble.
     */
    @SuppressLint("ViewConstructor")
    private inner class DragFrame(context: Context) : FrameLayout(context) {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startX = popupParams?.x ?: 0
                    startY = popupParams?.y ?: 0
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (handleOwnsGesture) return false // the corner dot is moving it
                    if (dragging) return true
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (abs(dx) <= touchSlopPx && abs(dy) <= touchSlopPx) return false
                    val textScrolls = abs(dy) > abs(dx) &&
                        popupText?.canScrollVertically(if (dy < 0) -1 else 1) == true
                    if (textScrolls) return false
                    dragging = true
                    popupPinned = true
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return false
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val params = popupParams ?: return true
                    popupX = startX + (ev.rawX - downRawX).roundToInt()
                    popupY = startY + (ev.rawY - downRawY).roundToInt()
                    params.x = popupX
                    params.y = popupY
                    runCatching { windowManager.updateViewLayout(this, params) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> movePopup() // clamp on-screen
            }
            return true
        }
    }

    /** Real-time drag from the corner dot. Kept separate from [DragFrame] so the
     *  dot never has to compete with the text view for the gesture. */
    @SuppressLint("ClickableViewAccessibility")
    private inner class MoveHandleTouch : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = popupParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    handleOwnsGesture = true
                }
                MotionEvent.ACTION_MOVE -> {
                    moved = true
                    popupPinned = true
                    popupX = startX + (event.rawX - downRawX).roundToInt()
                    popupY = startY + (event.rawY - downRawY).roundToInt()
                    params.x = popupX
                    params.y = popupY
                    val card = popupWindowView ?: return true
                    runCatching { windowManager.updateViewLayout(card, params) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleOwnsGesture = false
                    if (moved) movePopup() // clamp inside the screen
                }
            }
            return true
        }
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
        if (popupPinned) {
            // User placed the bubble themselves: keep that spot, only squeezing it
            // back inside the screen (rotation, or a longer translation growing
            // past the right/bottom edge).
            popupX = popupX.coerceIn(0, max(0, screenW - pw))
            popupY = popupY.coerceIn(0, max(0, screenH - ph))
            params.x = popupX
            params.y = popupY
            runCatching { windowManager.updateViewLayout(view, params) }
            return
        }
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
        showRegionHandle(rect)
    }

    fun hideRegionOutline() {
        outlineWindowView?.let { runCatching { windowManager.removeView(it) } }
        outlineWindowView = null
        outlineView = null
        hideRegionHandle()
    }

    // ---- region move handle (bottom-right dot) ----

    private fun showRegionHandle(rect: Rect) {
        val dot = regionHandleDot ?: createRegionHandle().also { regionHandleDot = it }
        applyHandleAppearance(settings.ocrRegionOutlineColor.toInt())
        dot.invalidate()
        placeRegionHandle(rect)
    }

    private fun hideRegionHandle() {
        regionHandleWindow?.let { runCatching { windowManager.removeView(it) } }
        regionHandleWindow = null
        regionHandleParams = null
        regionHandleDot = null
        draggingRegion = false
    }

    private fun applyHandleAppearance(colorArgb: Int) {
        val density = context.resources.displayMetrics.density
        regionHandleDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((0xE6 shl 24) or (colorArgb and 0x00FFFFFF))
            setStroke((2 * density).roundToInt(), Color.argb(230, 255, 255, 255))
        }
    }

    private fun createRegionHandle(): View {
        val density = context.resources.displayMetrics.density
        val windowSize = (REGION_HANDLE_TOUCH_DP * density).roundToInt()
        val dotSize = (REGION_HANDLE_DOT_DP * density).roundToInt()
        val dot = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                dotSize,
                dotSize,
                Gravity.CENTER,
            )
            setOnTouchListener(RegionHandleTouch())
        }
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(dot)
        }
        regionHandleWindow = root
        regionHandleParams = overlayParams(windowSize, windowSize)
        runCatching { windowManager.addView(root, regionHandleParams) }
        return dot
    }

    /** 把圆点窗口的中心对到选区右下角；窗口比圆点大，留出足够的触摸范围。 */
    private fun placeRegionHandle(rect: Rect) {
        val root = regionHandleWindow ?: return
        val params = regionHandleParams ?: return
        val (screenW, screenH, density) = screenMetrics()
        val size = (REGION_HANDLE_TOUCH_DP * density).roundToInt()
        val half = size / 2
        params.x = (rect.right - half).coerceIn(0, max(0, screenW - size))
        params.y = (rect.bottom - half).coerceIn(0, max(0, screenH - size))
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private enum class RegionDragMode { PENDING, RESIZE, MOVE }

    /**
     * 右下角圆点：直接拖动 = 从右下角改宽高；按住 500ms 后再拖 = 整框平移。
     * 圈外（外框窗口）是触摸穿透的，所以移动模式只能靠圆点上的长按进入。
     */
    @SuppressLint("ClickableViewAccessibility")
    private inner class RegionHandleTouch : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private val startRect = Rect()
        private var mode = RegionDragMode.PENDING
        /** 本次手势是否真的改过选区；长按后没动就抬手不该触发重新识别。 */
        private var changed = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        private val longPress = Runnable {
            if (draggingRegion && mode == RegionDragMode.PENDING) {
                mode = RegionDragMode.MOVE
                regionHandleDot?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                Toast.makeText(context, R.string.ocr_region_move_mode, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val region = lastRegion ?: return false
                    startRect.set(region)
                    downRawX = event.rawX
                    downRawY = event.rawY
                    mode = RegionDragMode.PENDING
                    changed = false
                    draggingRegion = true
                    v.postDelayed(longPress, REGION_LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!draggingRegion) return false
                    val dx = (event.rawX - downRawX).roundToInt()
                    val dy = (event.rawY - downRawY).roundToInt()
                    if (mode == RegionDragMode.PENDING) {
                        // 先动手指 = 缩放；长按期间的小抖动不算，等定时器触发移动模式。
                        if (hypot(dx.toFloat(), dy.toFloat()) <= touchSlop) return true
                        v.removeCallbacks(longPress)
                        mode = RegionDragMode.RESIZE
                        regionHandleDot?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    val (screenW, screenH, _) = screenMetrics()
                    val startBounds = RegionBounds(
                        startRect.left, startRect.top, startRect.right, startRect.bottom,
                    )
                    val nextBounds = when (mode) {
                        RegionDragMode.RESIZE -> RegionGestureMath.resizeFromBottomRight(
                            startBounds, dx, dy, screenW, screenH,
                        )
                        RegionDragMode.MOVE -> RegionGestureMath.moveClamped(
                            startBounds, dx, dy, screenW, screenH,
                        )
                        RegionDragMode.PENDING -> return true
                    }
                    val next = Rect(
                        nextBounds.left, nextBounds.top, nextBounds.right, nextBounds.bottom,
                    )
                    if (next != lastRegion) changed = true
                    lastRegion = next
                    outlineView?.setRegion(next) // 实时跟手
                    placeRegionHandle(next)
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (!draggingRegion) return false
                    draggingRegion = false
                    if (changed) lastRegion?.let { onRegionMoved(Rect(it)) }
                    mode = RegionDragMode.PENDING
                    changed = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPress)
                    if (!draggingRegion) return false
                    draggingRegion = false
                    if (changed) {
                        lastRegion = Rect(startRect)
                        outlineView?.setRegion(startRect)
                        placeRegionHandle(startRect)
                    }
                    mode = RegionDragMode.PENDING
                    changed = false
                }
                else -> return false
            }
            return true
        }
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
        /** Visible dot diameter of the region move handle. */
        private const val REGION_HANDLE_DOT_DP = 22
        /** Touch target around that dot (window size). */
        private const val REGION_HANDLE_TOUCH_DP = 44
        /** 圆点上长按多久进入移动模式。 */
        private const val REGION_LONG_PRESS_MS = 500L
    }
}
