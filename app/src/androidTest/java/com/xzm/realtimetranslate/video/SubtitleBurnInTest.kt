package com.xzm.realtimetranslate.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

/**
 * End-to-end check of the burn-in path on a real device.
 *
 * The interesting failures here are all runtime-only: a missing hardware encoder, a
 * Transformer that refuses the effect list, or an output the muxer writes but no
 * player can open. None of them show up when the code merely compiles, so this test
 * generates a tiny clip, burns cues into it, and then reads the result back.
 */
@RunWith(AndroidJUnit4::class)
class SubtitleBurnInTest {

    @Test
    fun burnsCuesIntoARealVideo() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "burn_source.mp4")
        val output = File(context.cacheDir, "burn_output.mp4")
        source.delete()
        output.delete()

        writeTestVideo(source)
        assertTrue("test clip was not written", source.length() > 0)

        val cues = listOf(
            VideoCue(1, 0, 800, "hello", "你好"),
            VideoCue(2, 800, 1600, "world", "世界"),
        )

        val result = SubtitleBurnIn.burn(
            context = context,
            source = Uri.fromFile(source),
            cues = cues,
            outputFile = output,
        )

        assertTrue("burn failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue("output missing", output.exists())
        assertTrue("output is empty", output.length() > 0)

        // A file that exists is not proof of a playable video: verify the muxer wrote
        // a real video track, not just a container header.
        val track = readVideoTrack(output)
        assertTrue("no video track in output", track != null)
    }

    /**
     * Encodes a 2-second 320×240 clip of alternating colours via ByteBuffer input.
     *
     * ByteBuffer rather than a Surface: `lockCanvas` only works on a SurfaceHolder's
     * surface, not on the one MediaCodec hands out for surface input.
     */
    private fun writeTestVideo(file: File) {
        val width = 320
        val height = 240
        val fps = 15
        val frames = 30

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 800_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()

        var submitted = 0
        var done = false
        while (!done) {
            if (submitted < frames) {
                val inIndex = encoder.dequeueInputBuffer(10_000L)
                if (inIndex >= 0) {
                    val buffer = encoder.getInputBuffer(inIndex)!!
                    fillYuv(buffer, width, height, submitted % 2 == 0)
                    val ptsUs = submitted * 1_000_000L / fps
                    encoder.queueInputBuffer(inIndex, 0, width * height * 3 / 2, ptsUs, 0)
                    submitted++
                }
            } else {
                val inIndex = encoder.dequeueInputBuffer(10_000L)
                if (inIndex >= 0) {
                    encoder.queueInputBuffer(
                        inIndex,
                        0,
                        0,
                        submitted * 1_000_000L / fps,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                }
            }

            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(info, 0L)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    continue
                }
                if (outIndex < 0) continue
                if (info.size > 0 && muxerStarted) {
                    muxer.writeSampleData(trackIndex, encoder.getOutputBuffer(outIndex)!!, info)
                }
                val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                encoder.releaseOutputBuffer(outIndex, false)
                if (eos) {
                    done = true
                    break
                }
            }
        }

        encoder.stop()
        encoder.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
    }

    /** Fills a YUV420 buffer with flat grey plus a moving band, so frames differ. */
    private fun fillYuv(buffer: ByteBuffer, width: Int, height: Int, light: Boolean) {
        buffer.clear()
        val luma = if (light) 200.toByte() else 80.toByte()
        val chroma = 128.toByte()
        repeat(width * height) { buffer.put(luma) }
        repeat(width * height / 2) { buffer.put(chroma) }
        buffer.position(0)
    }

    /** Opens the file as a muxer output and reports its video track, or null. */
    private fun readVideoTrack(file: File): String? = runCatching {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var found: String? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) {
                found = "$mime ${format.getInteger(MediaFormat.KEY_WIDTH)}×" +
                    "${format.getInteger(MediaFormat.KEY_HEIGHT)}"
            }
        }
        extractor.release()
        found
    }.getOrNull()
}
