package com.xzm.realtimetranslate.ocr

/** 与 Android [android.graphics.Rect] 等价的纯 Kotlin 矩形，便于在 JVM 单测里跑几何逻辑。 */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun offset(dx: Int, dy: Int): PixelRect =
        PixelRect(left + dx, top + dy, right + dx, bottom + dy)

    /** 把预处理放大过的坐标换回源图坐标。 */
    fun scaleToSource(sx: Float, sy: Float): PixelRect {
        val l = (left / sx).toInt()
        val t = (top / sy).toInt()
        return PixelRect(
            left = l,
            top = t,
            right = (right / sx).toInt().coerceAtLeast(l),
            bottom = (bottom / sy).toInt().coerceAtLeast(t),
        )
    }

    fun union(other: PixelRect): PixelRect = PixelRect(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    fun intersects(other: PixelRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun padded(pad: Int): PixelRect = PixelRect(
        left = left - pad,
        top = top - pad,
        right = right + pad,
        bottom = bottom + pad,
    )

    val area: Int get() = width * height

    fun intersectionArea(other: PixelRect): Int {
        val xOverlap = maxOf(0, minOf(right, other.right) - maxOf(left, other.left))
        val yOverlap = maxOf(0, minOf(bottom, other.bottom) - maxOf(top, other.top))
        return xOverlap * yOverlap
    }

    fun overlapRatio(other: PixelRect): Float {
        val inter = intersectionArea(other)
        if (inter <= 0) return 0f
        val minArea = minOf(area, other.area)
        return if (minArea > 0) inter.toFloat() / minArea else 0f
    }
}

data class OcrLine(val text: String, val rect: PixelRect)

data class OcrBlock(val lines: List<OcrLine>) {

    val rect: PixelRect = lines.firstOrNull()?.rect
        ?.let { first -> lines.drop(1).fold(first) { acc, line -> acc.union(line.rect) } }
        ?: PixelRect(0, 0, 0, 0)

    val text: String = lines.joinToString("\n") { it.text }
}

data class OcrPage(val width: Int, val height: Int, val blocks: List<OcrBlock>)
