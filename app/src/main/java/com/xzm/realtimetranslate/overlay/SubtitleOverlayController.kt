package com.xzm.realtimetranslate.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.xzm.realtimetranslate.data.SubtitleDisplayMode
import com.xzm.realtimetranslate.data.UserSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Floating subtitle window using classic Views (reliable with WindowManager).
 * - thin top grabber to move
 * - bottom-right handle to resize box only (font size unchanged)
 * - clamps size/position on orientation change so the handle never goes off-screen
 * - display mode: BOTH (source + divider + translation, split) / SOURCE-only /
 *   TRANSLATION-only, each pane with independent auto-scroll
 */
class SubtitleOverlayController(
    private val context: Context,
    private val onGeometryChanged: (x: Int, y: Int, widthDp: Int, heightDp: Int) -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var inputView: TextView? = null
    private var outputView: TextView? = null
    private var inputScroll: ScrollView? = null
    private var outputScroll: ScrollView? = null
    private var dividerView: View? = null
    private var inputSection: LinearLayout? = null
    private var container: LinearLayout? = null

    // ---- 折叠成小球 ----
    private var ballView: BallView? = null
    private var resizeHandle: View? = null
    private var rootBackground: Drawable? = null
    private var rootPadL = 0
    private var rootPadT = 0
    private var rootPadR = 0
    private var rootPadB = 0
    private var isCollapsed = false
    private var isAnimating = false
    /** 折叠态球吸附边：LEFT / RIGHT（半球贴边）或 FLOAT（完整圆浮动）。 */
    private var ballSide = BallView.SIDE_RIGHT
    private var savedX = 0
    private var savedY = 0
    private var savedW = 0
    private var savedH = 0
    /** 半球「点击 vs 滑动」判定阈值（像素）。 */
    private val touchSlopPx: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var settings: UserSettings = UserSettings()
    private var inputText: String = ""
    private var outputText: String = ""

    /** Last known layout line counts — scroll only when a new line is completed. */
    private var lastInputLineCount = 0
    private var lastOutputLineCount = 0

    private var lastScreenW = 0
    private var lastScreenH = 0

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            clampAndApply(persist = true, reason = "config")
        }

        override fun onLowMemory() = Unit
    }

    private var callbacksRegistered = false

    fun show(initial: UserSettings) {
        if (rootView != null) {
            updateSettings(initial)
            clampAndApply(persist = true, reason = "show-update")
            return
        }
        settings = initial

        val (screenW, screenH, density) = screenMetrics()
        lastScreenW = screenW
        lastScreenH = screenH

        val widthPx = clampWidth((initial.overlayWidthDp * density).roundToInt(), screenW)
        val heightPx = clampHeight((initial.overlayHeightDp * density).roundToInt(), screenH)
        val x = if (initial.overlayX < 0) {
            ((screenW - widthPx) / 2).coerceAtLeast(0)
        } else {
            safeCoerce(initial.overlayX, 0, max(0, screenW - widthPx))
        }
        val y = if (initial.overlayY < 0) {
            (screenH * 0.72f).roundToInt().let { safeCoerce(it, 0, max(0, screenH - heightPx)) }
        } else {
            safeCoerce(initial.overlayY, 0, max(0, screenH - heightPx))
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
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
            this.x = x
            this.y = y
        }
        layoutParams = params

        val view = buildOverlayView(density)
        rootView = view
        windowManager.addView(view, params)
        registerCallbacks()
        applySettingsToViews()
        applyTranscriptsToViews()
        persistGeometry()
    }

    fun updateSettings(value: UserSettings) {
        val displayModeChanged = value.displayMode != settings.displayMode
        val fontChanged = value.fontSizeSp != settings.fontSizeSp
        settings = value
        applySettingsToViews()
        applyLayoutMode()
        if (displayModeChanged || fontChanged) {
            // Layout geometry of lines changes — re-baseline counters
            lastInputLineCount = 0
            lastOutputLineCount = 0
        }
        clampAndApply(persist = false, reason = "settings")
        // After mode switch, re-apply text + scroll policy
        applyTranscriptsToViews()
    }

    fun updateTranscripts(input: String?, output: String?) {
        if (input != null) inputText = input
        if (output != null) outputText = output
        applyTranscriptsToViews()
    }

    fun hide() {
        unregisterCallbacks()
        val view = rootView ?: return
        runCatching { windowManager.removeView(view) }
        rootView = null
        layoutParams = null
        inputView = null
        outputView = null
        inputScroll = null
        outputScroll = null
        dividerView = null
        inputSection = null
        container = null
        ballView = null
        resizeHandle = null
        rootBackground = null
        // 重置折叠状态，防止残留状态污染下次会话。
        isCollapsed = false
        isAnimating = false
        ballSide = BallView.SIDE_RIGHT
    }

    private fun clampAndApply(persist: Boolean, reason: String) {
        val params = layoutParams ?: return
        val view = rootView ?: return
        val (screenW, screenH, _) = screenMetrics()

        val screenChanged = screenW != lastScreenW || screenH != lastScreenH
        lastScreenW = screenW
        lastScreenH = screenH

        val oldW = params.width
        val oldH = params.height
        val oldX = params.x
        val oldY = params.y

        if (isCollapsed) {
            // 折叠态：窗口 size/2 宽紧贴屏幕边缘，BallView 圆心对准屏幕边缘、由窗口裁出真半球；
            // FLOAT 时保持 size×size 完整圆。屏幕变化时保持吸附；不持久化小球几何。
            val size = ballSizePx()
            when (ballSide) {
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
                    params.x = safeCoerce(params.x, 0, max(0, screenW - size))
                }
            }
            params.y = safeCoerce(params.y, 0, max(0, screenH - size))
            ballView?.side = ballSide
        } else {
            params.width = clampWidth(params.width, screenW)
            params.height = clampHeight(params.height, screenH)
            params.x = safeCoerce(params.x, 0, max(0, screenW - params.width))
            params.y = safeCoerce(params.y, 0, max(0, screenH - params.height))
        }

        val changed = params.width != oldW || params.height != oldH ||
            params.x != oldX || params.y != oldY || screenChanged

        if (changed) {
            Log.i(
                TAG,
                "clamp($reason): ${oldW}x${oldH}@${oldX},${oldY} -> " +
                    "${params.width}x${params.height}@${params.x},${params.y} screen=${screenW}x${screenH}",
            )
            runCatching { windowManager.updateViewLayout(view, params) }
                .onFailure { Log.e(TAG, "updateViewLayout failed", it) }
            if ((persist || screenChanged) && !isCollapsed) {
                persistGeometry()
            }
        }
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

    private fun clampWidth(width: Int, screenW: Int): Int {
        val minW = min(MIN_WIDTH_PX, screenW)
        val maxW = max(minW, screenW - EDGE_MARGIN_PX)
        return safeCoerce(width, minW, maxW)
    }

    private fun clampHeight(height: Int, screenH: Int): Int {
        val minH = min(MIN_HEIGHT_PX, screenH)
        val maxH = max(minH, min(screenH / 2, screenH - EDGE_MARGIN_PX))
        return safeCoerce(height, minH, maxH)
    }

    private fun safeCoerce(value: Int, start: Int, end: Int): Int {
        if (end < start) return start
        return value.coerceIn(start, end)
    }

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

    @SuppressLint("ClickableViewAccessibility")
    private fun buildOverlayView(density: Float): View {
        val root = FrameLayout(context)

        val bg = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(Color.argb((settings.backgroundAlpha * 255).toInt().coerceIn(25, 242), 0, 0, 0))
        }
        root.background = bg
        val padH = (10 * density).roundToInt()
        val padV = (6 * density).roundToInt()
        root.setPadding(padH, padV, padH, padH)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        container = column

        // Thin grabber
        val grabberRow = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (22 * density).roundToInt(),
            )
        }
        val grabberBar = View(context).apply {
            val w = (36 * density).roundToInt()
            val h = (4 * density).roundToInt()
            layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
            background = GradientDrawable().apply {
                cornerRadius = 2 * density
                setColor(Color.argb(115, 255, 255, 255))
            }
        }
        grabberRow.addView(grabberBar)
        grabberRow.setOnTouchListener(MoveTouchListener())
        column.addView(grabberRow)

        // ---- Source pane (BOTH / SOURCE only) ----
        val sourceSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            visibility = if (settings.displayMode != SubtitleDisplayMode.TRANSLATION) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        inputSection = sourceSection

        val inScroll = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            // Don't steal drag from grabber/resize; text area is display-only scroll
            isClickable = false
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }
        inputScroll = inScroll

        val input = TextView(context).apply {
            typeface = Typeface.DEFAULT
            setLineSpacing(0f, 1.15f)
            // No maxLines — grow and scroll
            text = ""
        }
        inputView = input
        inScroll.addView(
            input,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        sourceSection.addView(inScroll)
        column.addView(sourceSection)

        // Divider between source and translation
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                max(1, (1 * density).roundToInt()),
            ).apply {
                topMargin = (4 * density).roundToInt()
                bottomMargin = (4 * density).roundToInt()
            }
            setBackgroundColor(Color.argb(70, 255, 255, 255))
            visibility = if (settings.displayMode == SubtitleDisplayMode.BOTH) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        dividerView = divider
        column.addView(divider)

        // ---- Translation pane (BOTH / TRANSLATION) ----
        val outScroll = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isClickable = false
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        outputScroll = outScroll

        val output = TextView(context).apply {
            typeface = Typeface.DEFAULT
            setLineSpacing(0f, 1.15f)
            text = "…"
        }
        outputView = output
        outScroll.addView(
            output,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        column.addView(outScroll)

        root.addView(column)

        // Resize handle (啾啾)
        val handleSize = (22 * density).roundToInt()
        val handle = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(handleSize, handleSize, Gravity.BOTTOM or Gravity.END)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(90, 255, 255, 255))
            }
        }
        handle.setOnTouchListener(ResizeTouchListener())
        resizeHandle = handle
        root.addView(handle)

        // 折叠小球（默认隐藏）：共享半球渲染视图（纯色圆、无文字/箭头，避免小尺寸球内字体错乱），
        // 贴边时窗口裁出真半球；点按展开。
        val ball = BallView(context).apply {
            diameterPx = ballSizePx()
            side = BallView.SIDE_FLOAT
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            visibility = View.GONE
            setOnTouchListener(BallTouchListener())
        }
        ballView = ball
        root.addView(ball)

        applyLayoutMode()
        return root
    }

    /**
     * Three display modes: BOTH (source + divider + translation, split half/half),
     * SOURCE-only (source fills the whole height), TRANSLATION-only (translation fills
     * the whole height). Hidden panes are GONE so they take no space; a single visible
     * pane with weight 1f fills the overlay.
     */
    private fun applyLayoutMode() {
        val mode = settings.displayMode
        inputSection?.visibility = if (mode != SubtitleDisplayMode.TRANSLATION) {
            View.VISIBLE
        } else {
            View.GONE
        }
        dividerView?.visibility = if (mode == SubtitleDisplayMode.BOTH) View.VISIBLE else View.GONE
        // SOURCE 模式隐藏译文窗格（GONE 不占空间，原文 weight 1f 撑满全高）。
        outputScroll?.visibility = if (mode == SubtitleDisplayMode.SOURCE) View.GONE else View.VISIBLE

        (inputSection?.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.weight = 1f
            lp.height = 0
            inputSection?.layoutParams = lp
        }
        (outputScroll?.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.weight = 1f
            lp.height = 0
            outputScroll?.layoutParams = lp
        }
    }

    private fun applySettingsToViews() {
        val alpha = (settings.backgroundAlpha * 255).toInt().coerceIn(25, 242)
        // 折叠时 root 背景被摘除存于 rootBackground，展开恢复时颜色也应保持最新设置。
        val bg = (rootView?.background as? GradientDrawable)
            ?: (rootBackground as? GradientDrawable)
        bg?.setColor(Color.argb(alpha, 0, 0, 0))
        inputView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSizeSp * 0.9f)
        outputView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSizeSp)
        // User-picked text colors (ARGB Long → Int, setTextColor(Int) reads ARGB directly).
        inputView?.setTextColor(settings.sourceTextColor.toInt())
        outputView?.setTextColor(settings.translationTextColor.toInt())
    }

    private fun applyTranscriptsToViews() {
        val inTv = inputView
        val outTv = outputView

        if (inTv != null) {
            val next = inputText
            if (inTv.text?.toString() != next) {
                inTv.text = next
                // Only scroll after layout, and only when line count increases
                scheduleLineScroll(inTv, inputScroll, isInput = true)
            }
        }
        if (outTv != null) {
            val next = outputText.ifBlank { "…" }
            if (outTv.text?.toString() != next) {
                outTv.text = next
                scheduleLineScroll(outTv, outputScroll, isInput = false)
            }
        }
    }

    /**
     * Auto-scroll policy: do NOT chase every character.
     * Only when the TextView's laid-out line count increases (a line filled and wrapped),
     * scroll so the newest line is visible — typically one line height at a time.
     */
    private fun scheduleLineScroll(
        textView: TextView,
        scrollView: ScrollView?,
        isInput: Boolean,
    ) {
        if (scrollView == null) return
        textView.post {
            val layout = textView.layout
            val lineCount = when {
                layout != null && layout.lineCount > 0 -> layout.lineCount
                else -> textView.lineCount
            }.coerceAtLeast(0)

            val previous = if (isInput) lastInputLineCount else lastOutputLineCount

            // Text shrank (history trimmed from the front / mode switch) — stay pinned
            // to the newest line rather than jumping to the top.
            if (lineCount < previous) {
                if (isInput) lastInputLineCount = lineCount else lastOutputLineCount = lineCount
                scrollToShowLastLine(textView, scrollView)
                return@post
            }

            // Same line still filling in — keep eyes steady, no scroll
            if (lineCount <= previous) {
                return@post
            }

            // New line(s) completed — scroll so the last line is fully visible
            if (isInput) lastInputLineCount = lineCount else lastOutputLineCount = lineCount
            scrollToShowLastLine(textView, scrollView)
        }
    }

    private fun scrollToShowLastLine(textView: TextView, scrollView: ScrollView) {
        scrollView.post {
            val layout = textView.layout ?: return@post
            if (layout.lineCount <= 0) return@post
            val last = layout.lineCount - 1
            val lineBottom = layout.getLineBottom(last)
            val target = (lineBottom + scrollView.paddingBottom - scrollView.height)
                .coerceAtLeast(0)
            // Smooth-ish step: if we only grew by 1 line, this moves ~one line
            if (target != scrollView.scrollY) {
                scrollView.smoothScrollTo(0, target)
            }
        }
    }

    private fun persistGeometry() {
        val params = layoutParams ?: return
        val d = context.resources.displayMetrics.density.coerceAtLeast(0.5f)
        runCatching {
            onGeometryChanged(
                params.x,
                params.y,
                (params.width / d).roundToInt().coerceAtLeast(1),
                (params.height / d).roundToInt().coerceAtLeast(1),
            )
        }.onFailure { Log.e(TAG, "persistGeometry failed", it) }
    }

    private fun ballSizePx(): Int =
        (BALL_SIZE_DP * context.resources.displayMetrics.density).roundToInt()

    /**
     * 拖动松手时判定贴边：窗口左边缘贴边→折叠到左；右边缘贴边→折叠到右。
     * 返回是否已触发折叠（触发则上层不再 persist，避免小球几何覆盖窗口位置）。
     */
    private fun maybeFold(): Boolean {
        val params = layoutParams ?: return false
        if (isCollapsed || isAnimating) return false
        val (screenW, _, _) = screenMetrics()
        return when {
            params.x <= EDGE_COLLAPSE_PX -> {
                fold(BallView.SIDE_LEFT); true
            }
            screenW - (params.x + params.width) <= EDGE_COLLAPSE_PX -> {
                fold(BallView.SIDE_RIGHT); true
            }
            else -> false
        }
    }

    /** 收起窗口成吸附边缘的小球（丝滑动画）。翻译继续，仅收界面。 */
    private fun fold(side: Int) {
        val params = layoutParams ?: return
        val root = rootView ?: return
        val ball = ballView ?: return
        val column = container ?: return
        if (isCollapsed || isAnimating) return

        savedX = params.x
        savedY = params.y
        savedW = params.width
        savedH = params.height
        ballSide = side
        isAnimating = true

        // 摘下窗口背景与 padding，折叠期间由小球（半球）接管。
        rootBackground = root.background
        rootPadL = root.paddingLeft
        rootPadT = root.paddingTop
        rootPadR = root.paddingRight
        rootPadB = root.paddingBottom
        root.setBackground(null)
        root.setPadding(0, 0, 0, 0)
        resizeHandle?.visibility = View.GONE

        val size = ballSizePx()
        val (screenW, screenH, _) = screenMetrics()
        // 半球：窗口贴边只留 size/2 宽，BallView 圆心对准屏幕边缘、由窗口裁出真半球。
        val targetW = size / 2
        val targetH = size
        val targetX = if (side == BallView.SIDE_LEFT) 0 else screenW - size / 2
        val targetY = savedY.coerceIn(0, max(0, screenH - size))
        ball.side = side
        ball.visibility = View.VISIBLE
        ball.alpha = 0f

        val startX = params.x
        val startY = params.y
        val startW = params.width
        val startH = params.height
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLLAPSE_ANIM_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                params.x = (startX + (targetX - startX) * f).roundToInt()
                params.y = (startY + (targetY - startY) * f).roundToInt()
                params.width = (startW + (targetW - startW) * f).roundToInt()
                params.height = (startH + (targetH - startH) * f).roundToInt()
                runCatching { windowManager.updateViewLayout(root, params) }
                    .onFailure { Log.e(TAG, "fold update failed", it) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    // 视图可能已在动画期间被 hide() 移除，此时丢弃收尾、不写状态。
                    if (rootView == null) return
                    params.x = targetX
                    params.y = targetY
                    params.width = targetW
                    params.height = targetH
                    ball.side = side
                    runCatching { windowManager.updateViewLayout(root, params) }
                        .onFailure { Log.e(TAG, "fold finalize failed", it) }
                    column.visibility = View.GONE
                    column.alpha = 1f
                    ball.alpha = 1f
                    isAnimating = false
                    isCollapsed = true
                }
            })
        }
        column.animate().alpha(0f).setDuration(COLLAPSE_ANIM_MS).start()
        ball.animate().alpha(1f).setDuration(COLLAPSE_ANIM_MS).start()
        anim.start()
    }

    /** 点小球展开：丝滑恢复到折叠前的位置与大小。 */
    private fun expand() {
        val params = layoutParams ?: return
        val root = rootView ?: return
        val ball = ballView ?: return
        val column = container ?: return
        if (!isCollapsed || isAnimating) return

        isAnimating = true

        val (screenW, screenH, _) = screenMetrics()
        val targetW = clampWidth(savedW, screenW)
        val targetH = clampHeight(savedH, screenH)
        val targetX = safeCoerce(savedX, 0, max(0, screenW - targetW))
        val targetY = safeCoerce(savedY, 0, max(0, screenH - targetH))

        // 恢复窗口背景与 padding。
        val bg = rootBackground
        if (bg != null) root.setBackground(bg)
        rootBackground = null
        root.setPadding(rootPadL, rootPadT, rootPadR, rootPadB)
        resizeHandle?.visibility = View.VISIBLE

        column.visibility = View.VISIBLE
        column.alpha = 0f

        val startX = params.x
        val startY = params.y
        val startW = params.width
        val startH = params.height
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLLAPSE_ANIM_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                params.x = (startX + (targetX - startX) * f).roundToInt()
                params.y = (startY + (targetY - startY) * f).roundToInt()
                params.width = (startW + (targetW - startW) * f).roundToInt()
                params.height = (startH + (targetH - startH) * f).roundToInt()
                runCatching { windowManager.updateViewLayout(root, params) }
                    .onFailure { Log.e(TAG, "expand update failed", it) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    // 视图可能已在动画期间被 hide() 移除，此时丢弃收尾、不写状态。
                    if (rootView == null) return
                    params.x = targetX
                    params.y = targetY
                    params.width = targetW
                    params.height = targetH
                    runCatching { windowManager.updateViewLayout(root, params) }
                        .onFailure { Log.e(TAG, "expand finalize failed", it) }
                    ball.visibility = View.GONE
                    ball.alpha = 1f
                    column.alpha = 1f
                    // 折叠期间文本持续更新、行计数可能过期——展开后重算滚动基线并滚到最新。
                    lastInputLineCount = 0
                    lastOutputLineCount = 0
                    inputView?.let { tv -> inputScroll?.let { sv -> scrollToShowLastLine(tv, sv) } }
                    outputView?.let { tv -> outputScroll?.let { sv -> scrollToShowLastLine(tv, sv) } }
                    isAnimating = false
                    isCollapsed = false
                }
            })
        }
        ball.animate().alpha(0f).setDuration(COLLAPSE_ANIM_MS).start()
        column.animate().alpha(1f).setDuration(COLLAPSE_ANIM_MS).start()
        anim.start()
    }

    private inner class MoveTouchListener : View.OnTouchListener {
        private var lastX = 0f
        private var lastY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            val root = rootView ?: return false
            return try {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        clampAndApply(persist = false, reason = "move-down")
                        lastX = event.rawX
                        lastY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        lastX = event.rawX
                        lastY = event.rawY
                        val (screenW, screenH, _) = screenMetrics()
                        params.width = clampWidth(params.width, screenW)
                        params.height = clampHeight(params.height, screenH)
                        params.x = safeCoerce(
                            params.x + dx.roundToInt(),
                            0,
                            max(0, screenW - params.width),
                        )
                        params.y = safeCoerce(
                            params.y + dy.roundToInt(),
                            0,
                            max(0, screenH - params.height),
                        )
                        windowManager.updateViewLayout(root, params)
                        persistGeometry()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 贴边则折叠成小球；否则正常落位。折叠时窗口几何已在 MOVE 中持久化，
                        // 不在此 persist，避免小球几何覆盖窗口位置。
                        if (!maybeFold()) {
                            clampAndApply(persist = true, reason = "move-up")
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        clampAndApply(persist = true, reason = "move-cancel")
                        true
                    }
                    else -> false
                }
            } catch (t: Throwable) {
                Log.e(TAG, "move touch failed", t)
                clampAndApply(persist = true, reason = "move-error")
                true
            }
        }
    }

    private inner class ResizeTouchListener : View.OnTouchListener {
        private var lastX = 0f
        private var lastY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            val root = rootView ?: return false
            return try {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        clampAndApply(persist = false, reason = "resize-down")
                        lastX = event.rawX
                        lastY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        lastX = event.rawX
                        lastY = event.rawY
                        val (screenW, screenH, _) = screenMetrics()
                        val maxW = max(MIN_WIDTH_PX, screenW - params.x - EDGE_MARGIN_PX)
                        val maxH = max(
                            MIN_HEIGHT_PX,
                            min(screenH / 2, screenH - params.y - EDGE_MARGIN_PX),
                        )
                        params.width = safeCoerce(params.width + dx.roundToInt(), MIN_WIDTH_PX, maxW)
                        params.height = safeCoerce(params.height + dy.roundToInt(), MIN_HEIGHT_PX, maxH)
                        params.x = safeCoerce(params.x, 0, max(0, screenW - params.width))
                        params.y = safeCoerce(params.y, 0, max(0, screenH - params.height))
                        windowManager.updateViewLayout(root, params)
                        persistGeometry()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        clampAndApply(persist = true, reason = "resize-up")
                        true
                    }
                    else -> false
                }
            } catch (t: Throwable) {
                Log.e(TAG, "resize touch failed", t)
                clampAndApply(persist = true, reason = "resize-error")
                true
            }
        }
    }

    /** 折叠态半球：可沿屏幕两侧上下推、拖离边缘成完整圆、跨边换向；轻点 → 展开。 */
    private inner class BallTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var grabDx = 0f
        private var grabDy = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            val root = rootView ?: return false
            if (ballView == null) return false
            return try {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        dragging = false
                        val (screenW, _, _) = screenMetrics()
                        val (cx, cy) = ballCenter(params, screenW)
                        grabDx = event.rawX - cx
                        grabDy = event.rawY - cy
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging &&
                            (abs(event.rawX - downX) > touchSlopPx ||
                                abs(event.rawY - downY) > touchSlopPx)
                        ) {
                            dragging = true
                            // 拖离贴边：窗口变 size×size，完整圆跟手。
                            setBallFloat(params)
                        }
                        if (dragging) {
                            val (screenW, screenH, _) = screenMetrics()
                            val size = ballSizePx()
                            val cx = (event.rawX - grabDx).roundToInt()
                                .coerceIn(size / 2, max(size / 2, screenW - size / 2))
                            val cy = (event.rawY - grabDy).roundToInt()
                                .coerceIn(size / 2, max(size / 2, screenH - size / 2))
                            params.width = size
                            params.height = size
                            params.x = cx - size / 2
                            params.y = cy - size / 2
                            runCatching { windowManager.updateViewLayout(root, params) }
                                .onFailure { Log.e(TAG, "ball move failed", it) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            // 轻点（未滑动）→ 展开回大框。
                            expand()
                        } else {
                            parkBall(params)
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

    /** 当前球心屏幕坐标（左贴边=0，右贴边=screenW，浮动=窗口中心）。 */
    private fun ballCenter(params: WindowManager.LayoutParams, screenW: Int): Pair<Int, Int> {
        val cx = when (ballSide) {
            BallView.SIDE_LEFT -> 0
            BallView.SIDE_RIGHT -> screenW
            else -> params.x + params.width / 2
        }
        return cx to (params.y + params.height / 2)
    }

    /** 拖离贴边：窗口切到 size×size 完整圆，圆心对准当前球心（含 grip 偏移）。 */
    private fun setBallFloat(params: WindowManager.LayoutParams) {
        val root = rootView ?: return
        val (screenW, screenH, _) = screenMetrics()
        val size = ballSizePx()
        val (cx, cy) = ballCenter(params, screenW)
        ballSide = BallView.SIDE_FLOAT
        params.width = size
        params.height = size
        params.x = (cx - size / 2).coerceIn(0, max(0, screenW - size))
        params.y = (cy - size / 2).coerceIn(0, max(0, screenH - size))
        ballView?.side = BallView.SIDE_FLOAT
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "ball float failed", it) }
    }

    /** 松手吸附：靠近左/右边缘 → 半球贴边；否则保持完整圆浮动。 */
    private fun parkBall(params: WindowManager.LayoutParams) {
        val root = rootView ?: return
        val (screenW, screenH, _) = screenMetrics()
        val size = ballSizePx()
        val cx = params.x + size / 2
        ballSide = when {
            cx <= size * 3 / 4 -> BallView.SIDE_LEFT
            cx >= screenW - size * 3 / 4 -> BallView.SIDE_RIGHT
            else -> BallView.SIDE_FLOAT
        }
        when (ballSide) {
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
                params.x = safeCoerce(params.x, 0, max(0, screenW - size))
            }
        }
        params.y = safeCoerce(params.y, 0, max(0, screenH - size))
        ballView?.side = ballSide
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "ball park failed", it) }
    }

    companion object {
        private const val TAG = "SubtitleOverlay"
        private const val MIN_WIDTH_PX = 200
        private const val MIN_HEIGHT_PX = 80
        private const val EDGE_MARGIN_PX = 8
        // ---- 折叠成小球 ----
        private const val BALL_SIZE_DP = 56
        private const val EDGE_COLLAPSE_PX = 60
        private const val COLLAPSE_ANIM_MS = 250L
    }
}
