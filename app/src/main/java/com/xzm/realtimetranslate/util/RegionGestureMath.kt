package com.xzm.realtimetranslate.util

import kotlin.math.max

/**
 * 识别框的纯几何表示。
 *
 * 刻意不依赖 `android.graphics.Rect`：这些函数要能在 JVM 单测里直接跑，
 * 而 Rect 的方法在普通 unit test 里是 not-mocked 桩。
 */
data class RegionBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * 识别框右下角圆点的手势几何计算。
 *
 * 圆点锚在选区右下角，所以缩放只动 right/bottom，left/top 固定；平移则整框移动、
 * 宽高不变。两种操作都必须夹在屏幕内，并保证选区不小于 [MIN_REGION_PX]。
 */
object RegionGestureMath {

    /** 与 ScreenTextSessionService 共用的最小选区边长。 */
    const val MIN_REGION_PX = 48

    fun resizeFromBottomRight(
        start: RegionBounds,
        dx: Int,
        dy: Int,
        screenW: Int,
        screenH: Int,
        minSizePx: Int = MIN_REGION_PX,
    ): RegionBounds {
        val minRight = start.left + minSizePx
        val minBottom = start.top + minSizePx
        val right = (start.right + dx).coerceIn(minRight, max(minRight, screenW))
        val bottom = (start.bottom + dy).coerceIn(minBottom, max(minBottom, screenH))
        return RegionBounds(start.left, start.top, right, bottom)
    }

    fun moveClamped(
        start: RegionBounds,
        dx: Int,
        dy: Int,
        screenW: Int,
        screenH: Int,
    ): RegionBounds {
        val left = (start.left + dx).coerceIn(0, max(0, screenW - start.width))
        val top = (start.top + dy).coerceIn(0, max(0, screenH - start.height))
        return RegionBounds(left, top, left + start.width, top + start.height)
    }
}
