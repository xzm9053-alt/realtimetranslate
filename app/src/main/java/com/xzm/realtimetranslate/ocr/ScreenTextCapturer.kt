package com.xzm.realtimetranslate.ocr

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.Log

/**
 * Captures the full screen via MediaProjection → a 1:1 full-resolution
 * [VirtualDisplay] backed by an [ImageReader] (RGBA_8888, maxImages=2).
 *
 * Coordinates are identity-mapped: a region chosen in screen pixels is a region
 * in virtual-display pixels, so the crop rectangle needs no scaling. We keep
 * full resolution instead of downscaling the virtual display because small CJK
 * caption text becomes unrecognizable when the display is shrunk.
 */
class ScreenTextCapturer(
    private val mediaProjection: MediaProjection,
    displayW: Int,
    displayH: Int,
    private val densityDpi: Int,
    private val handler: Handler?,
) {

    /** Crop + a coarse content hash so the caller can skip OCR on unchanged frames. */
    class RegionFrame(val bitmap: Bitmap, val hash: Long)

    @Volatile
    private var displayW = displayW

    @Volatile
    private var displayH = displayH

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var cropBitmap: Bitmap? = null

    fun start() {
        val reader = ImageReader.newInstance(
            displayW, displayH, PixelFormat.RGBA_8888, MAX_IMAGES,
        )
        imageReader = reader
        // Caller must register MediaProjection.Callback BEFORE this call
        // (Android 14 requires it) and use startForeground(mediaProjection)
        // before getMediaProjection().
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenTextOCR",
            displayW, displayH, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
        Log.d(TAG, "virtual display created: ${displayW}x${displayH} @ ${densityDpi}dpi")
    }

    /** Returns the latest frame cropped to [region] (screen px), or null when no frame is ready. */
    fun captureRegion(region: Rect): RegionFrame? {
        val reader = imageReader ?: return null
        val image: Image = try {
            reader.acquireLatestImage() ?: return null
        } catch (t: Throwable) {
            Log.w(TAG, "acquireLatestImage failed", t)
            return null
        }
        try {
            // Defensive clamp: selection may be stale after a rotation.
            val left = region.left.coerceIn(0, displayW - 1)
            val top = region.top.coerceIn(0, displayH - 1)
            val right = region.right.coerceIn(left + 1, displayW)
            val bottom = region.bottom.coerceIn(top + 1, displayH)
            val bitmap = copyRegion(image, Rect(left, top, right, bottom))
            return RegionFrame(bitmap, coarseHash(bitmap))
        } finally {
            // Must always close, even on error paths.
            image.close()
        }
    }

    /** Handles display rotation: keep the same projection token, swap the ImageReader. */
    fun resize(w: Int, h: Int, dpi: Int) {
        displayW = w
        displayH = h
        virtualDisplay?.resize(w, h, dpi)
        imageReader?.close()
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, MAX_IMAGES)
        imageReader = reader
        virtualDisplay?.setSurface(reader.surface)
    }

    fun release() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
    }

    /**
     * Copies [region] out of the RGBA_8888 plane row by row. ImageReader planes
     * on some devices (MIUI especially) pad rows, so `buffer` is not a tightly
     * packed bitmap — a direct `copyPixelsFromBuffer` would smear every row.
     */
    private fun copyRegion(image: Image, region: Rect): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = region.width()
        val h = region.height()

        var bm = cropBitmap
        if (bm == null || bm.width != w || bm.height != h) {
            bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            cropBitmap = bm
        }

        val pixels = IntArray(w * h)
        val row = ByteArray(rowStride)
        var idx = 0
        var y = region.top
        while (y < region.bottom) {
            buffer.position(y * rowStride + region.left * pixelStride)
            buffer.get(row, 0, w * pixelStride)
            var i = 0
            while (i < w * pixelStride) {
                // RGBA_8888 plane byte order is R,G,B,A on the wire; even if a
                // device flips R/B, the luminance OCR relies on is unaffected.
                val r = row[i].toInt() and 0xFF
                val g = row[i + 1].toInt() and 0xFF
                val b = row[i + 2].toInt() and 0xFF
                val a = row[i + 3].toInt() and 0xFF
                pixels[idx++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                i += pixelStride
            }
            y++
        }
        bm.setPixels(pixels, 0, w, 0, 0, w, h)
        return bm
    }

    /** Coarse sampled content hash — cheap, just enough to skip identical frames. */
    private fun coarseHash(bitmap: Bitmap): Long {
        var hash = 0L
        val stepX = maxOf(1, bitmap.width / 24)
        val stepY = maxOf(1, bitmap.height / 24)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                hash = hash * 31 + bitmap.getPixel(x, y).toLong()
                x += stepX
            }
            y += stepY
        }
        return hash
    }

    companion object {
        private const val TAG = "ScreenTextCapturer"
        private const val MAX_IMAGES = 2
    }
}
