package com.xzm.realtimetranslate.util

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager

/**
 * Physical display size in px (including the system bars) plus density.
 *
 * The OCR selection box lives in this full-screen coordinate space, so the
 * MediaProjection virtual display must be created at exactly these dimensions
 * for a 1:1 pixel mapping. Unlike [android.view.WindowManager.getCurrentWindowMetrics],
 * [android.view.Display.getRealMetrics] is the real panel on every API level
 * (the app window metrics on Android 11–14 commonly exclude the nav bar).
 */
fun Context.realScreenMetrics(): Triple<Int, Int, Float> {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    @Suppress("DEPRECATION")
    val display = wm.defaultDisplay
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    display.getRealMetrics(metrics)
    return Triple(metrics.widthPixels, metrics.heightPixels, metrics.density)
}

/** Current display rotation: [Surface.ROTATION_0] / 90 / 180 / 270. */
@Suppress("DEPRECATION")
fun Context.realDisplayRotation(): Int = try {
    (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
} catch (_: Throwable) {
    Surface.ROTATION_0
}

/**
 * Maps [region] from the coordinate space it was drawn in
 * (`oldW`×`oldH` @ `oldRot`) into the one it must now be used in
 * (`newW`×`newH` @ `newRot`), then squeezes it into the new bounds with every
 * edge at least [minPx] long.
 *
 * A rigid rotation (the rotation delta) is mapped exactly; a pure size change
 * (foldable unfold, freeform resize) keeps the relative position. Anything left
 * over is clamped, so the result is always usable — never throws, never null.
 *
 * Caller must guarantee `newW >= minPx && newH >= minPx`.
 */
fun remapRegion(
    region: Rect,
    oldW: Int,
    oldH: Int,
    oldRot: Int,
    newW: Int,
    newH: Int,
    newRot: Int,
    minPx: Int,
): Rect {
    if (oldW <= 0 || oldH <= 0) return clampRegion(region, newW, newH, minPx)
    // Rotation applied to the drawn content between the two frames. Using the
    // delta (not "did width/height swap") keeps the direction right, and also
    // covers ROTATION_180 (where the axes do NOT swap) and a resize with no
    // rotation at all.
    val delta = (((newRot - oldRot) % 360) + 360) % 360
    val mapped = when (delta) {
        90 -> Rect(oldH - region.bottom, region.left, oldH - region.top, region.right)
        180 -> Rect(oldW - region.right, oldH - region.bottom, oldW - region.left, oldH - region.top)
        270 -> Rect(region.top, oldW - region.right, region.bottom, oldW - region.left)
        else -> if (oldW != newW || oldH != newH) {
            // Same orientation, different size (foldable, resizable display):
            // scale proportionally so the selection keeps its relative place.
            // A bare clamp would collapse it into a corner when shrinking.
            Rect(
                (region.left.toLong() * newW / oldW).toInt(),
                (region.top.toLong() * newH / oldH).toInt(),
                (region.right.toLong() * newW / oldW).toInt(),
                (region.bottom.toLong() * newH / oldH).toInt(),
            )
        } else {
            Rect(region)
        }
    }
    return clampRegion(mapped, newW, newH, minPx)
}

/**
 * Fits [region] inside `[0,newW]×[0,newH]` and grows every edge to at least
 * [minPx]. The result always satisfies the capture-side validity check:
 * on-screen and at least [minPx] wide/tall.
 *
 * Caller must guarantee `newW >= minPx && newH >= minPx` — `coerceIn` throws
 * otherwise.
 */
fun clampRegion(region: Rect, newW: Int, newH: Int, minPx: Int): Rect {
    val w = region.width().coerceIn(minPx, newW)
    val h = region.height().coerceIn(minPx, newH)
    val l = region.left.coerceIn(0, newW - w)
    val t = region.top.coerceIn(0, newH - h)
    return Rect(l, t, l + w, t + h)
}
