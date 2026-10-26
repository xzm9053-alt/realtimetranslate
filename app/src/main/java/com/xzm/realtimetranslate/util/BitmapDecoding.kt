package com.xzm.realtimetranslate.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.xzm.realtimetranslate.util.AppLog as Log

/**
 * Decodes user-picked pictures at a size the OCR and the cover painter can handle.
 *
 * Uses [ImageDecoder] rather than `BitmapFactory`: camera photos carry their
 * orientation in EXIF, which `BitmapFactory.decodeStream` ignores. A portrait photo
 * decoded that way reaches the recognizer lying on its side and reads as nothing at
 * all — which looks like poor OCR rather than a decoding bug. [ImageDecoder] applies
 * the EXIF rotation itself, and it has been available since API 28 while this app's
 * minSdk is 29.
 */
object BitmapDecoding {

    /**
     * Longest side of the decoded bitmap. A 4000×3000 photo is 48 MB at ARGB_8888 and
     * the pipeline holds the original plus a painted copy, so full resolution would
     * get the process killed on mid-range phones. 2000 px is far more than ML Kit
     * needs — [com.xzm.realtimetranslate.ocr.ScreenTextOcr] only upscales below
     * 1200×120 and leaves anything larger alone.
     */
    const val MAX_SIDE = 2000

    /**
     * Power-of-two sample factor that brings the longest side to at most [maxSide].
     *
     * [ImageDecoder.setTargetSampleSize] requires a power of two, and so does
     * `BitmapFactory.Options.inSampleSize`; returning anything else silently decodes
     * at the wrong size.
     */
    fun sampleSizeFor(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        if (width <= 0 || height <= 0 || maxSide <= 0) return 1
        val longest = maxOf(width, height)
        if (longest <= maxSide) return 1
        var sample = 1
        // Halve only while the longest side is still above the cap: 4000 with a 2000
        // cap wants 2, 8000 wants 4 (not 8, which would drop it to 1000), and 3999
        // wants 2 because 1999 already fits.
        while (longest / sample > maxSide) sample *= 2
        return sample
    }

    /**
     * Decodes [uri] with its orientation applied, longest side at most [maxSide].
     * Returns null when the picture cannot be read or is not a picture at all.
     */
    fun decode(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sample = sampleSizeFor(info.size.width, info.size.height, maxSide)
            if (sample > 1) decoder.setTargetSampleSize(sample)
            // The cover painter writes into this bitmap, so it must be mutable. Left
            // to itself ImageDecoder returns a hardware bitmap, which Canvas cannot
            // draw onto (and which also cannot be read back with getPixels, which the
            // background sampler needs).
            decoder.isMutableRequired = true
        }
    } catch (t: Throwable) {
        Log.w(TAG, "failed to decode $uri", t)
        null
    }

    private const val TAG = "BitmapDecoding"
}
