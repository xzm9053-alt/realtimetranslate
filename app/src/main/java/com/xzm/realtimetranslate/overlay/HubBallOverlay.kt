package com.xzm.realtimetranslate.overlay

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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

    /** 取词选区：常驻球也不能落进去，否则会被录进帧里造成每帧都变。 */
    private var avoidRect: Rect? = null

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
        centerX = when (side) {
            BallView.SIDE_LEFT -> 0
            BallView.SIDE_RIGHT -> screenW
            else -> centerX.coerceIn(size / 2, max(size / 2, screenW - size / 2))
        }
        centerY = centerY.coerceIn(size / 2, max(size / 2, screenH - size / 2))
        applyGeometry()
    }

    /** 告诉常驻球取词选区在哪，它会自动挪到区外。null 恢复自由。 */
    fun setAvoidRect(rect: Rect?) {
        avoidRect = rect?.let { Rect(it) }
        val avoid = avoidRect ?: return
        if (ballWindowView == null) return
        val (screenW, screenH, _) = screenMetrics()
        val size = sizePx()
        val topRoom = avoid.top
        val bottomRoom = screenH - avoid.bottom
        val ballLeft = centerX - size / 2
        val ballRight = centerX + size / 2
        val ballTop = centerY - size / 2
        val ballBottom = centerY + size / 2
        val overlaps = Rect.intersects(avoid, Rect(ballLeft, ballTop, ballRight, ballBottom))
        if (overlaps || side != BallView.SIDE_FLOAT) {
            centerY = when {
                topRoom >= size -> avoid.top / 2
                bottomRoom >= size -> (avoid.bottom + screenH) / 2
                else -> if (topRoom >= bottomRoom) size / 2 else screenH - size / 2
            }.coerceIn(size / 2, max(size / 2, screenH - size / 2))
        }
        if (side != BallView.SIDE_FLOAT) {
            centerX = if (side == BallView.SIDE_LEFT) 0 else screenW
        }
        applyGeometry()
    }

    private fun showMenu() {
        if (menuWindowView != null) return
        val (screenW, screenH, density) = screenMetrics()
        val size = sizePx()
        val margin = (12 * density).roundToInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (12 * density).roundToInt()
            val padV = (12 * density).roundToInt()
            setPadding(padH, padV, padH, padV)
            minimumWidth = (212 * density).roundToInt()
            background = GradientDrawable().apply {
                setColor(0xE61C1D24.toInt()) // 深色磨砂玻璃晶体底色
                cornerRadius = 20 * density
                setStroke((1 * density).roundToInt(), 0x2EFFFFFF)
            }
            elevation = 16 * density
        }

        fun addHeader(title: String) {
            val tv = TextView(context).apply {
                text = title
                setTextColor(0x80FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding((6 * density).roundToInt(), (4 * density).roundToInt(), (6 * density).roundToInt(), (4 * density).roundToInt())
            }
            card.addView(tv)
        }

        fun addDivider() {
            val div = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    max(1, (0.5f * density).roundToInt())
                ).apply {
                    val m = (4 * density).roundToInt()
                    setMargins((4 * density).roundToInt(), m, (4 * density).roundToInt(), m)
                }
                setBackgroundColor(0x1AFFFFFF)
            }
            card.addView(div)
        }

        fun addItem(
            icon: String,
            title: String,
            subtitle: String? = null,
            badgeBgColor: Int = 0x263482FF,
            badgeTextColor: Int = 0xFF3482FF.toInt(),
            titleColor: Int = Color.WHITE,
            onClick: () -> Unit
        ) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val rowPadV = (7 * density).roundToInt()
                val rowPadH = (8 * density).roundToInt()
                setPadding(rowPadH, rowPadV, rowPadH, rowPadV)
                val shape = GradientDrawable().apply {
                    cornerRadius = 12 * density
                }
                background = RippleDrawable(ColorStateList.valueOf(0x26FFFFFF), null, shape)
                isClickable = true
                setOnClickListener {
                    hideMenu()
                    onClick()
                }
            }

            // Badge Icon Container
            val badge = FrameLayout(context).apply {
                val bSize = (32 * density).roundToInt()
                layoutParams = LinearLayout.LayoutParams(bSize, bSize).apply {
                    marginEnd = (10 * density).roundToInt()
                }
                background = GradientDrawable().apply {
                    setColor(badgeBgColor)
                    cornerRadius = 9 * density
                }
            }
            val badgeText = TextView(context).apply {
                this.text = icon
                setTextColor(badgeTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            badge.addView(badgeText)
            row.addView(badge)

            // Text column
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleView = TextView(context).apply {
                this.text = title
                setTextColor(titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
            }
            textCol.addView(titleView)

            if (!subtitle.isNullOrBlank()) {
                val subView = TextView(context).apply {
                    this.text = subtitle
                    setTextColor(0x80FFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                }
                textCol.addView(subView)
            }
            row.addView(textCol)

            card.addView(row)
        }

        // 根据当前模式渲染菜单项
        when (currentState.mode) {
            HubMode.IDLE -> {
                addHeader("实时翻译模式")
                addItem("⛶", context.getString(R.string.hub_menu_region), "选区与全屏即时翻译", badgeBgColor = 0x263482FF, badgeTextColor = 0xFF3482FF.toInt()) {
                    HubBus.postCommand(HubCommand.SwitchMode(HubMode.SCREEN_REGION))
                }
                addItem("🎙", context.getString(R.string.hub_menu_voice), "内录与麦克风同传", badgeBgColor = 0x268B5CF6, badgeTextColor = 0xFFA78BFA.toInt()) {
                    HubBus.postCommand(HubCommand.SwitchMode(HubMode.VOICE))
                }

                addDivider()
                addHeader("独立翻译工具")
                addItem("📷", context.getString(R.string.hub_menu_camera), "即时拍照翻译", badgeBgColor = 0x26F59E0B, badgeTextColor = 0xFFFBBF24.toInt()) {
                    HubBus.postCommand(HubCommand.LaunchStaticMode(StaticModeTarget.CAMERA))
                }
                addItem("🖼", context.getString(R.string.hub_menu_image), "相册划词涂抹翻译", badgeBgColor = 0x2610B981, badgeTextColor = 0xFF34D399.toInt()) {
                    HubBus.postCommand(HubCommand.LaunchStaticMode(StaticModeTarget.IMAGE))
                }
                addItem("📄", context.getString(R.string.hub_menu_document), "Docx / PDF 全篇翻译", badgeBgColor = 0x2606B6D4, badgeTextColor = 0xFF22D3EE.toInt()) {
                    HubBus.postCommand(HubCommand.LaunchStaticMode(StaticModeTarget.DOCUMENT))
                }

                addDivider()
                addItem("✕", context.getString(R.string.hub_menu_close_hub), "关闭常驻悬浮球", badgeBgColor = 0x26EF4444, badgeTextColor = 0xFFF87171.toInt(), titleColor = 0xFFF87171.toInt()) {
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
                val winIcon = if (currentState.isSubtitleWindowVisible) "🙈" else "👁"
                addItem(winIcon, winText, "切换字幕悬浮窗显隐", badgeBgColor = 0x2664748B, badgeTextColor = 0xFF94A3B8.toInt()) {
                    HubBus.postCommand(HubCommand.SetSubtitleWindowVisible(!currentState.isSubtitleWindowVisible))
                }

                val pauseText = if (currentState.isPaused) {
                    context.getString(R.string.hub_menu_resume)
                } else {
                    context.getString(R.string.hub_menu_pause)
                }
                val pauseIcon = if (currentState.isPaused) "▶" else "⏸"
                addItem(pauseIcon, pauseText, "暂停或继续实时同传", badgeBgColor = 0x26F59E0B, badgeTextColor = 0xFFFBBF24.toInt()) {
                    HubBus.postCommand(HubCommand.TogglePause)
                }

                addDivider()
                addItem("⏹", context.getString(R.string.hub_menu_stop_current), "结束同传并保存历史", badgeBgColor = 0x26EF4444, badgeTextColor = 0xFFF87171.toInt(), titleColor = 0xFFF87171.toInt()) {
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
                val pauseIcon = if (currentState.isPaused) "▶" else "⏸"
                addItem(pauseIcon, pauseText, "暂停或恢复取词识别", badgeBgColor = 0x26F59E0B, badgeTextColor = 0xFFFBBF24.toInt()) {
                    HubBus.postCommand(HubCommand.TogglePause)
                }

                addItem("⌖", context.getString(R.string.ocr_menu_reselect), "重新框选屏幕区域", badgeBgColor = 0x263482FF, badgeTextColor = 0xFF3482FF.toInt()) {
                    HubBus.postCommand(HubCommand.ReselectRegion)
                }
                addItem("▢", "切换取词框显隐", "隐藏或显示绿色选框", badgeBgColor = 0x2664748B, badgeTextColor = 0xFF94A3B8.toInt()) {
                    HubBus.postCommand(HubCommand.ToggleOutline)
                }

                addDivider()
                addItem("⏹", context.getString(R.string.hub_menu_stop_current), "结束屏幕取词会话", badgeBgColor = 0x26EF4444, badgeTextColor = 0xFFF87171.toInt(), titleColor = 0xFFF87171.toInt()) {
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

        // Fluid enter animation
        card.scaleX = 0.88f
        card.scaleY = 0.88f
        card.alpha = 0f
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()

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
                            val density = context.resources.displayMetrics.density
                            val snapThresholdPx = (SNAP_THRESHOLD_DP * density).roundToInt()
                            val nearLeft = pendingX <= snapThresholdPx
                            val nearRight = pendingX >= gestureW - snapThresholdPx

                            side = when {
                                nearLeft -> BallView.SIDE_LEFT
                                nearRight -> BallView.SIDE_RIGHT
                                else -> BallView.SIDE_FLOAT
                            }

                            if (side == BallView.SIDE_LEFT) {
                                centerX = 0
                                triggerSnapHaptic()
                            } else if (side == BallView.SIDE_RIGHT) {
                                centerX = gestureW
                                triggerSnapHaptic()
                            } else {
                                centerX = pendingX.coerceIn(gestureSize / 2, gestureW - gestureSize / 2)
                            }
                            centerY = pendingY.coerceIn(gestureSize / 2, max(gestureSize / 2, gestureH - gestureSize / 2))
                            applyGeometry()
                        }
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        expandAnimator?.cancel()
                        v.removeCallbacks(flushDrag)
                        flushScheduled = false
                        val density = context.resources.displayMetrics.density
                        val snapThresholdPx = (SNAP_THRESHOLD_DP * density).roundToInt()
                        val nearLeft = pendingX <= snapThresholdPx
                        val nearRight = pendingX >= gestureW - snapThresholdPx

                        side = when {
                            nearLeft -> BallView.SIDE_LEFT
                            nearRight -> BallView.SIDE_RIGHT
                            else -> BallView.SIDE_FLOAT
                        }

                        if (side == BallView.SIDE_LEFT) {
                            centerX = 0
                        } else if (side == BallView.SIDE_RIGHT) {
                            centerX = gestureW
                        } else {
                            centerX = pendingX.coerceIn(gestureSize / 2, gestureW - gestureSize / 2)
                        }
                        centerY = pendingY.coerceIn(gestureSize / 2, max(gestureSize / 2, gestureH - gestureSize / 2))
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
        private const val SNAP_THRESHOLD_DP = 44
    }
}
