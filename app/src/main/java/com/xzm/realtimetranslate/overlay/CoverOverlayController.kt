package com.xzm.realtimetranslate.overlay

import android.content.Context
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

    /** Screen-space rects currently painted, in the same coordinates as [CoverTarget]. */
    @Volatile
    private var paintedRects: List<PixelRect> = emptyList()

    /** Screen-space targets currently painted, parallel to [paintedRects]. */
    @Volatile
    private var paintedTargets: List<CoverTarget> = emptyList()

    /**
     * The targets behind [paintedRects], in the same order.
     *
     * The capture loop redraws their original text back into the frame before OCR:
     * the cover hides the source text, so without this the recognizer can never see
     * that region again and the only way to keep it translated would be to
     * accumulate blocks forever.
     */
    fun paintedTargets(): List<CoverTarget> = paintedTargets

    /**
     * The blocks currently painted. Callers erase these from the next capture so
     * OCR does not read our own translation back as fresh source text — that loop
     * re-translates the same screen forever.
     */
    fun paintedRects(): List<PixelRect> = paintedRects

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
            // System bars inset this window (frame starts at the status bar), so the
            // view's (0,0) is not the screen's (0,0). Targets arrive in screen pixels;
            // shift them into view space or every block lands one status-bar too low.
            val loc = IntArray(2)
            cover.getLocationOnScreen(loc)
            val placed = if (loc[0] == 0 && loc[1] == 0) {
                filtered
            } else {
                filtered.map { it.copy(rect = it.rect.offset(-loc[0], -loc[1])) }
            }
            Log.i(
                TAG,
                "coverupdate in=${targets.size} out=${placed.size} loc=${loc[0]},${loc[1]} " +
                    "view=${cover.width}x${cover.height} first=${placed.firstOrNull()?.rect}",
            )
            // Screen coordinates, NOT the view-space `placed` rects: the capture
            // bitmap spans the whole display, so erasing with view coords would be
            // off by the status-bar inset. Use the painter's real bounds — a block
            // grows downwards to fit its translation, and erasing only the source
            // rect would leave the painted text in the next capture, where OCR reads
            // it back as fresh source text and translates the screen forever.
            val painter = CoverPainter(context.resources.displayMetrics.density)
            paintedRects = filtered.map { toPixelRect(painter.paintedBounds(it, coverStyle)) }
            paintedTargets = filtered
            cover.setTargets(placed, coverStyle)
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
        paintedRects = emptyList()
        paintedTargets = emptyList()
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
        paintedRects = emptyList()
        paintedTargets = emptyList()
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
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            // FLAG_SECURE would keep this window out of the MediaProjection frames,
            // which is exactly what the cover needs. It is off because MIUI returns
            // black frames for the whole display as soon as any secure window is on
            // screen — the capture goes blind. The cover loop handles its own blocks
            // by filtering them out of the OCR result instead.
            0,
        PixelFormat.TRANSLUCENT,
    )

    private fun toPixelRect(r: android.graphics.RectF): PixelRect = PixelRect(
        left = r.left.toInt(),
        top = r.top.toInt(),
        right = r.right.toInt(),
        bottom = r.bottom.toInt(),
    )

    private data class BarInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun onSystemBar(rect: PixelRect, width: Int, height: Int): Boolean {
        val insets = systemBarInsets()
        if (rect.top < insets.top || rect.bottom > height - insets.bottom) return true
        if (rect.left < insets.left || rect.right > width - insets.right) return true
        return false
    }

    /** System-bar thickness in px for the current rotation. Safe on API 26+. */
    private fun systemBarInsets(): BarInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return BarInsets(insets.left, insets.top, insets.right, insets.bottom)
        }
        val res = context.resources
        val status = res.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { res.getDimensionPixelSize(it) } ?: 0
        val nav = res.getIdentifier("navigation_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { res.getDimensionPixelSize(it) } ?: 0
        return BarInsets(0, status, 0, nav)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val TAG = "CoverOverlay"
    }
}
