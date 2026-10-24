package com.xzm.realtimetranslate.overlay

import android.content.Context
import android.graphics.Insets
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.view.WindowManager
import com.xzm.realtimetranslate.ocr.PixelRect
import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.util.realScreenMetrics

/**
 * Owns the single full-screen cover window.
 *
 * The window is deliberately `FLAG_NOT_TOUCHABLE`: it must never swallow a tap
 * meant for the app underneath, which is the whole point of a replacement layer.
 * All calls are safe from any thread — window work is posted to the main looper.
 */
class CoverOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var view: CoverView? = null
    private var coverStyle: CoverStyle = CoverStyle()

    fun show(style: CoverStyle) {
        onMain {
            coverStyle = style
            removeWindow()
            addWindow()
        }
    }

    /**
     * Replaces what is painted. Targets that land on the status/navigation bars are
     * dropped — a block painted under a system bar would sit on top of the clock or
     * the gesture pill and look broken.
     */
    fun update(targets: List<CoverTarget>) {
        onMain {
            val cover = view ?: return@onMain
            val (w, h, _) = context.realScreenMetrics()
            val filtered = targets.filterNot { onSystemBar(it.rect, w, h) }
            cover.setTargets(filtered, coverStyle)
        }
    }

    /** Rebuilds the window for the current screen geometry and drops stale blocks. */
    fun reclamp() {
        onMain {
            if (view == null) return@onMain
            removeWindow()
            addWindow()
        }
    }

    fun hide() {
        onMain { removeWindow() }
    }

    private fun addWindow() {
        val cover = CoverView(context)
        try {
            windowManager.addView(cover, buildParams())
            view = cover
        } catch (t: Throwable) {
            Log.w(TAG, "failed to add cover overlay", t)
        }
    }

    private fun removeWindow() {
        val cover = view ?: return
        view = null
        runCatching { windowManager.removeView(cover) }
    }

    private fun buildParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    )

    private fun onSystemBar(rect: PixelRect, width: Int, height: Int): Boolean {
        val insets = systemBarInsets()
        if (rect.top < insets.top || rect.bottom > height - insets.bottom) return true
        if (rect.left < insets.left || rect.right > width - insets.right) return true
        return false
    }

    /** System-bar thickness in px for the current rotation. */
    private fun systemBarInsets(): Insets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        }
        val res = context.resources
        val status = res.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { res.getDimensionPixelSize(it) } ?: 0
        val nav = res.getIdentifier("navigation_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { res.getDimensionPixelSize(it) } ?: 0
        return Insets.of(0, status, 0, nav)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val TAG = "CoverOverlay"
    }
}
