package com.xzm.realtimetranslate.ocr

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Picks the color that should fill a cover block so the replacement looks like
 * the original background rather than an obvious sticker.
 */
class BackgroundColorSampler {

    /**
     * Samples a thin ring just outside [rect]: the pixels there are background,
     * while the inside is usually text ink and would skew the result.
     */
    fun sample(bitmap: Bitmap, rect: PixelRect): Int {
        if (bitmap.isRecycled) return Color.WHITE
        val outer = rect.padded(RING).clamped(bitmap.width, bitmap.height)
        if (outer.width == 0 || outer.height == 0) return Color.WHITE

        val ring = ArrayList<Int>(outer.width * outer.height / 2)
        val row = IntArray(outer.width)
        for (y in outer.top until outer.bottom) {
            bitmap.getPixels(row, 0, outer.width, outer.left, y, outer.width, 1)
            val insideRow = y >= rect.top && y < rect.bottom
            for (i in 0 until outer.width) {
                val x = outer.left + i
                val insideRect = insideRow && x >= rect.left && x < rect.right
                if (!insideRect) ring.add(row[i])
            }
        }
        // The box covers the whole bitmap: no ring exists, fall back to the inside.
        if (ring.isEmpty()) {
            val w = outer.width
            val scan = IntArray(w * outer.height)
            bitmap.getPixels(scan, 0, w, outer.left, outer.top, w, outer.height)
            return ColorQuantizer.dominant(scan)
        }
        return ColorQuantizer.dominant(ring.toIntArray())
    }

    private fun PixelRect.padded(by: Int) =
        PixelRect(left - by, top - by, right + by, bottom + by)

    private fun PixelRect.clamped(maxWidth: Int, maxHeight: Int): PixelRect {
        val l = left.coerceIn(0, maxWidth)
        val t = top.coerceIn(0, maxHeight)
        return PixelRect(
            left = l,
            top = t,
            right = right.coerceIn(l, maxWidth),
            bottom = bottom.coerceIn(t, maxHeight),
        )
    }

    companion object {
        /** How many pixels outside the box are sampled. */
        private const val RING = 3
    }
}

/** Counts colors at 5 bits per channel so near-identical shades vote together. */
object ColorQuantizer {

    private const val OPAQUE = 0xFF000000.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()

    fun dominant(argb: IntArray): Int {
        if (argb.isEmpty()) return WHITE
        val counts = LinkedHashMap<Int, Int>()
        val firstPixel = HashMap<Int, Int>()
        for (pixel in argb) {
            if (pixel ushr 24 < 0x80) continue
            val key = (pixel shr 19 and 0x1F shl 10) or
                (pixel shr 11 and 0x1F shl 5) or
                (pixel shr 3 and 0x1F)
            counts[key] = (counts[key] ?: 0) + 1
            firstPixel.putIfAbsent(key, pixel)
        }
        var bestKey = 0
        var bestCount = 0
        for ((key, count) in counts) {
            if (count > bestCount) {
                bestKey = key
                bestCount = count
            }
        }
        if (bestCount == 0) return WHITE
        return firstPixel.getValue(bestKey) or OPAQUE
    }
}
