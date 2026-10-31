package com.xzm.realtimetranslate.overlay

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.service.HubBus
import com.xzm.realtimetranslate.service.HubCommand
import com.xzm.realtimetranslate.service.HubMode
import com.xzm.realtimetranslate.service.HubUiState
import com.xzm.realtimetranslate.service.StaticModeTarget
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 通用常驻悬浮球展示与交互层：
 * 1. 半球悬浮球窗口（基于 BallView，真半球裁剪、贴边磁吸、跨边拖拽）；
 * 2. 状态感知交互菜单（全屏透明 Scrim 浮层，自适应 IDLE 模式选择与运行时控制）。
 */
class HubBallOverlay(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var ballWindowView: FrameLayout? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var ballView: BallView? = null

    private var menuWindowView: FrameLayout? = null

    private val touchSlopPx: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var side = BallView.SIDE_RIGHT
    private var centerX = 0
    private var centerY = 0

    private var currentState: HubUiState = HubUiState()

    private var expandAnimator: ValueAnimator? = null

    /**
     * Animate the ball growing from a half-circle (edge-snapped) into a full circle.
     * [fromSide] must be SIDE_LEFT or SIDE_RIGHT.
     * [onEnd] is called on the main thread when the animation completes or if skipped.
     */
    private fun startExpandAnimation(fromSide: Int, snapW: Int, snapH: Int, snapSize: Int, onEnd: () -> Unit) {
        expandAnimator?.cancel()
        val params = ballParams ?: run { onEnd(); return }
        val root = ballWindowView ?: run { onEnd(); return }
        val bv = ballView ?: run { onEnd(); return }

        bv.expandSide = fromSide
        bv.expandProgress = 0f

        val startW = snapSize / 2
        val endW = snapSize

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 130L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedFraction
                bv.expandProgress = p
                val curW = (startW + (endW - startW) * p).roundToInt()
                params.width = curW
                params.height = snapSize
                params.x = if (fromSide == BallView.SIDE_LEFT) 0 else snapW - curW
                params.y = (centerY - snapSize / 2).coerceIn(0, max(0, snapH - snapSize))
                runCatching { windowManager.updateViewLayout(root, params) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bv.expandSide = BallView.EXPAND_INACTIVE
                    bv.side = BallView.SIDE_FLOAT
                    params.width = snapSize
                    params.height = snapSize
                    params.x = (centerX - snapSize / 2).coerceIn(0, max(0, snapW - snapSize))
                    runCatching { windowManager.updateViewLayout(root, params) }
                    onEnd()
                }
                override fun onAnimationCancel(animation: Animator) {
                    bv.expandSide = BallView.EXPAND_INACTIVE
                }
            })
            expandAnimator = this
            start()
        }
    }

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            runCatching { reclamp() }
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = Unit
    }
    private var callbacksRegistered = false

    fun show() {
        if (ballWindowView != null) return
        val (screenW, screenH, _) = screenMetrics()
        val size = sizePx()

        side = BallView.SIDE_RIGHT
        centerX = screenW
        centerY = (screenH * 0.40f).roundToInt()

        val params = overlayParams(size, size)
        ballParams = params

        val ball = BallView(context).apply {
            diameterPx = size
            side = this@HubBallOverlay.side
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

    fun hide() {
        hideMenu()
        ballWindowView?.let { runCatching { windowManager.removeView(it) } }
        ballWindowView = null
        ballParams = null
        ballView = null
        if (callbacksRegistered) {
            runCatching { context.applicationContext.unregisterComponentCallbacks(configCallbacks) }
            callbacksRegistered = false
        }
    }

    fun updateState(state: HubUiState) {
        currentState = state
        ballView?.paused = state.isPaused
        // If menu is showing, update it
        if (menuWindowView != null) {
            hideMenu()
            showMenu()
        }
    }

    fun reclamp() {
        val (screenW, screenH, _) = screenMetrics()
        val size = sizePx()
        centerX = if (side == BallView.SIDE_LEFT) 0 else screenW
        centerY = centerY.coerceIn(size / 2, max(size / 2, screenH - size / 2))
        applyGeometry()
    }

    private fun showMenu() {
        if (menuWindowView != null) return
        val (screenW, screenH, density) = screenMetrics()
        val size = sizePx()
        val margin = (12 * density).roundToInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (16 * density).roundToInt()
            val padV = (12 * density).roundToInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(0xF0202228.toInt()) // 深色半透明卡片
                cornerRadius = 16 * density
                setStroke((1 * density).roundToInt(), 0x33FFFFFF)
            }
            elevation = 12 * density
        }

        fun addHeader(title: String) {
            val tv = TextView(context).apply {
                text = title
                setTextColor(0x99FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                val padBottom = (8 * density).roundToInt()
                setPadding(0, (4 * density).roundToInt(), 0, padBottom)
            }
            card.addView(tv)
        }

        fun addItem(icon: String, text: String, color: Int = Color.WHITE, onClick: () -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val rowPadV = (10 * density).roundToInt()
                val rowPadH = (8 * density).roundToInt()
                setPadding(rowPadH, rowPadV, rowPadH, rowPadV)
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                }
                isClickable = true
                setOnClickListener {
                    hideMenu()
                    onClick()
                }
            }
            val iconView = TextView(context).apply {
                this.text = icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, 0, (10 * density).roundToInt(), 0)
            }
            val textView = TextView(context).apply {
                this.text = text
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT
            }
            row.addView(iconView)
            row.addView(textView)
            card.addView(row)
        }

        // 根据当前模式渲染菜单项
        when (currentState.mode) {
            HubMode.IDLE -> {
                addHeader("实时翻译模式")
                addItem("🔍", context.getString(R.string.hub_menu_region)) {
                    HubBus.postCommand(HubCommand.SwitchMode(HubMode.SCREEN_REGION))
                }
                addItem("🎙️", context.getString(R.string.hub_menu_voice)) {
                    HubBus.postCommand(HubCommand.SwitchMode(HubMode.VOICE))
                }

                addHeader("其他翻译工具")
                addItem("📷", context.getString(R.string.hub_menu_camera)) {
                    HubBus.postCommand(HubCommand.LaunchStaticMode(StaticModeTarget.CAMERA))
                }
                addItem("🖼️", context.getString(R.string.hub_menu_image)) {
                    HubBus.postCommand(HubCommand.LaunchStaticMode(StaticModeTarget.IMAGE))
                }
                addItem("📄", context.getString(R.string.hub_menu_document)) {
                    HubBus.postCommand(HubCommand.LaunchStaticMode(StaticModeTarget.DOCUMENT))
                }

                addItem("❌", context.getString(R.string.hub_menu_close_hub), 0xFFFF5C5C.toInt()) {
                    HubBus.postCommand(HubCommand.CloseHub)
                }
            }
            HubMode.VOICE -> {
                addHeader("声音同传控制")
                val winText = if (currentState.isSubtitleWindowVisible) {
                    context.getString(R.string.hub_menu_hide_window)
                } else {
                    context.getString(R.string.hub_menu_show_window)
                }
                val winIcon = if (currentState.isSubtitleWindowVisible) "🙈" else "👁️"
                addItem(winIcon, winText) {
                    HubBus.postCommand(HubCommand.SetSubtitleWindowVisible(!currentState.isSubtitleWindowVisible))
                }

                val pauseText = if (currentState.isPaused) {
                    context.getString(R.string.hub_menu_resume)
                } else {
                    context.getString(R.string.hub_menu_pause)
                }
                val pauseIcon = if (currentState.isPaused) "▶️" else "⏸️"
                addItem(pauseIcon, pauseText) {
                    HubBus.postCommand(HubCommand.TogglePause)
                }

                addItem("⏹️", context.getString(R.string.hub_menu_stop_current), 0xFFFF5C5C.toInt()) {
                    HubBus.postCommand(HubCommand.StopCurrentMode)
                }
            }
            HubMode.SCREEN_REGION -> {
                addHeader("屏幕取词控制")
                val pauseText = if (currentState.isPaused) {
                    context.getString(R.string.hub_menu_resume)
                } else {
                    context.getString(R.string.hub_menu_pause)
                }
                val pauseIcon = if (currentState.isPaused) "▶️" else "⏸️"
                addItem(pauseIcon, pauseText) {
                    HubBus.postCommand(HubCommand.TogglePause)
                }

                addItem("🎯", context.getString(R.string.ocr_menu_reselect)) {
                    HubBus.postCommand(HubCommand.ReselectRegion)
                }
                addItem("🔲", "切换取词框显隐") {
                    HubBus.postCommand(HubCommand.ToggleOutline)
                }
                addItem("⏹️", context.getString(R.string.hub_menu_stop_current), 0xFFFF5C5C.toInt()) {
                    HubBus.postCommand(HubCommand.StopCurrentMode)
                }
            }
        }

        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        card.measure(unspecified, unspecified)
        val cw = card.measuredWidth
        val ch = card.measuredHeight

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
            setOnClickListener { hideMenu() }
            addView(card)
        }
        menuWindowView = root
        runCatching { windowManager.addView(root, overlayParams(screenW, screenH)) }
    }

    private fun hideMenu() {
        menuWindowView?.let { runCatching { windowManager.removeView(it) } }
        menuWindowView = null
    }

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

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun registerCallbacks() {
        if (callbacksRegistered) return
        runCatching { context.applicationContext.registerComponentCallbacks(configCallbacks) }
        callbacksRegistered = true
    }

    private fun sizePx(): Int {
        val density = context.resources.displayMetrics.density
        return (BALL_DIAMETER_DP * density).roundToInt()
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

    @SuppressLint("ClickableViewAccessibility")
    private inner class BallTouch : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var grabDx = 0f
        private var grabDy = 0f

        // Cached once per gesture: screenMetrics() is a binder round-trip, and the
        // drag path needs these on every move event.
        private var gestureW = 0
        private var gestureH = 0
        private var gestureSize = 0

        // Drag targets, flushed to the window manager at most once per frame.
        private var pendingX = 0
        private var pendingY = 0
        private var flushScheduled = false
        private val flushDrag = Runnable {
            flushScheduled = false
            val params = ballParams ?: return@Runnable
            val root = ballWindowView ?: return@Runnable
            params.width = gestureSize
            params.height = gestureSize
            params.x = (pendingX - gestureSize / 2).coerceIn(0, max(0, gestureW - gestureSize))
            params.y = (pendingY - gestureSize / 2).coerceIn(0, max(0, gestureH - gestureSize))
            runCatching { windowManager.updateViewLayout(root, params) }
        }

        // One updateViewLayout per frame, regardless of event rate.
        private var expandDone = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (ballWindowView == null) return false
            return try {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        dragging = false
                        expandDone = false
                        grabDx = event.rawX - centerX
                        grabDy = event.rawY - centerY
                        val (sw, sh, _) = screenMetrics()
                        gestureW = sw
                        gestureH = sh
                        gestureSize = sizePx()
                        pendingX = centerX
                        pendingY = centerY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging &&
                            (abs(event.rawX - downX) > touchSlopPx ||
                                abs(event.rawY - downY) > touchSlopPx)
                        ) {
                            dragging = true
                            val prevSide = side
                            side = BallView.SIDE_FLOAT
                            pendingX = (event.rawX - grabDx).roundToInt().coerceIn(0, gestureW)
                            pendingY = (event.rawY - grabDy).roundToInt().coerceIn(
                                gestureSize / 2,
                                max(gestureSize / 2, gestureH - gestureSize / 2),
                            )
                            if (prevSide != BallView.SIDE_FLOAT) {
                                // Animate half → full circle; suppress flushDrag until done
                                expandDone = false
                                startExpandAnimation(prevSide, gestureW, gestureH, gestureSize) {
                                    expandDone = true
                                }
                            } else {
                                expandDone = true
                                flushDrag.run()
                            }
                        }
                        if (dragging && expandDone) {
                            pendingX = (event.rawX - grabDx).roundToInt()
                                .coerceIn(0, gestureW)
                            pendingY = (event.rawY - grabDy).roundToInt()
                                .coerceIn(
                                    gestureSize / 2,
                                    max(gestureSize / 2, gestureH - gestureSize / 2),
                                )
                            // One updateViewLayout per frame, regardless of event rate.
                            if (!flushScheduled) {
                                flushScheduled = true
                                v.postOnAnimation(flushDrag)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = hypot(event.rawX - downX, event.rawY - downY)
                        if (moved <= touchSlopPx && !dragging) {
                            // 点击悬浮球：弹出菜单
                            triggerSnapHaptic()
                            showMenu()
                        } else if (dragging) {
                            // Drop any frame still queued so the snap uses the final
                            // finger position, not the last rendered one.
                            expandAnimator?.cancel()
                            v.removeCallbacks(flushDrag)
                            flushScheduled = false
                            centerX = pendingX
                            centerY = pendingY
                            side = if (centerX < gestureW / 2) BallView.SIDE_LEFT else BallView.SIDE_RIGHT
                            centerX = if (side == BallView.SIDE_LEFT) 0 else gestureW
                            triggerSnapHaptic()
                            applyGeometry()
                        }
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        expandAnimator?.cancel()
                        v.removeCallbacks(flushDrag)
                        flushScheduled = false
                        centerX = pendingX
                        centerY = pendingY
                        side = if (centerX < gestureW / 2) BallView.SIDE_LEFT else BallView.SIDE_RIGHT
                        centerX = if (side == BallView.SIDE_LEFT) 0 else gestureW
                        dragging = false
                        applyGeometry()
                        true
                    }
                    else -> false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        private const val BALL_DIAMETER_DP = 48
    }
}
