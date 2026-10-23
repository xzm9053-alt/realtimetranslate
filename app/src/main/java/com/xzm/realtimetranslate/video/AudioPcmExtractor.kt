package com.xzm.realtimetranslate.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 从视频文件里解出音轨，转成 16 kHz 单声道 float 后逐块回调。
 *
 * 全程流式：不落临时音频文件，也不把整段音频读进内存（两小时的片子那是几百 MB）。
 * 解码器输出格式不假设 16bit —— 位深、采样率、声道数都从 `INFO_OUTPUT_FORMAT_CHANGED`
 * 读出来，格式一变就新建一个 [Pcm16kConverter]。
 */
class AudioPcmExtractor(private val context: Context) {

    data class AudioInfo(
        val durationMs: Long,
        /** PTS of the first decoded audio buffer; the VAD sample clock starts here. */
        val firstPtsUs: Long,
    )

    suspend fun extract(
        uri: Uri,
        onProgress: (Float) -> Unit = {},
        /** Called once with the PTS of the first decoded audio buffer. */
        onAudioStart: (Long) -> Unit = {},
        onSamples: suspend (FloatArray) -> Unit,
    ): AudioInfo = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw IllegalStateException("这个视频没有音轨，无法识别")

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("无法读取音轨编码格式")
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION) / 1000L
            } else {
                0L
            }
            extractor.selectTrack(trackIndex)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var converter: Pcm16kConverter? = null
            var inputDone = false
            var outputDone = false
            var firstPtsUs = -1L
            var lastReportedPercent = -1

            while (!outputDone) {
                coroutineContext.ensureActive()

                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        val sampleSize = if (inputBuffer == null) -1 else extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val ptsUs = extractor.sampleTime
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, ptsUs, 0)
                            extractor.advance()
                            if (durationMs > 0) {
                                val percent = (ptsUs / 1000L * 100 / durationMs).toInt()
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress((ptsUs / 1000f / durationMs).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        converter = converterFor(decoder.outputFormat)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                    -> Unit

                    else -> if (outputIndex >= 0) {
                        val pcm = readBuffer(decoder, outputIndex, bufferInfo)
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (pcm != null) {
                            if (firstPtsUs < 0L) {
                                firstPtsUs = bufferInfo.presentationTimeUs
                                onAudioStart(firstPtsUs.coerceAtLeast(0L))
                            }
                            val samples = converter?.convert(pcm)
                            if (samples != null && samples.isNotEmpty()) onSamples(samples)
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            onProgress(1f)
            AudioInfo(durationMs = durationMs, firstPtsUs = firstPtsUs.coerceAtLeast(0L))
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun converterFor(outputFormat: MediaFormat): Pcm16kConverter {
        val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val encoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            PcmEncoding.ANDROID_PCM_16BIT
        }
        return Pcm16kConverter(
            inputSampleRate = sampleRate,
            channelCount = channels,
            encoding = PcmEncoding.fromAndroidEncoding(encoding),
        )
    }

    private fun readBuffer(
        codec: MediaCodec,
        outputIndex: Int,
        info: MediaCodec.BufferInfo,
    ): ByteArray? {
        if (info.size <= 0) return null
        val buffer = codec.getOutputBuffer(outputIndex) ?: return null
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val bytes = ByteArray(info.size)
        buffer.get(bytes)
        return bytes
    }

    private companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
