package com.xzm.realtimetranslate.util

import android.content.Context
import android.util.DisplayMetrics
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
