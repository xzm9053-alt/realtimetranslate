package com.xzm.realtimetranslate.video

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.xzm.realtimetranslate.util.AppLog as Log

/** A VAD cut: absolute sample span plus the audio itself. */
class VadSegment(
    val startSample: Long,
    val endSample: Long,
    val samples: FloatArray,
) {
    val lengthSamples: Long get() = endSample - startSample
}

/**
 * Silero VAD 的流式封装，额外维护"已喂入多少样本"，把每段映射回绝对时间轴。
 *
 * 实时同传不需要时间戳，所以 [com.xzm.realtimetranslate.translate.AsrEngine] 里
 * 没有这一层；视频字幕必须先知道每句话出现在第几秒。
 */
class StreamingVadSegmenter(
    vadModelPath: String,
    minSilenceSec: Float = 0.3f,
    maxSpeechSec: Float = 15f,
    minSpeechSec: Float = 0.25f,
) : AutoCloseable {

    private val vad = Vad(
        assetManager = null,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = vadModelPath,
                threshold = 0.5f,
                minSilenceDuration = minSilenceSec,
                minSpeechDuration = minSpeechSec,
                windowSize = 512,
                maxSpeechDuration = maxSpeechSec,
            ),
            sampleRate = VadTimeline.SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
        ),
    )

    private var totalFedSamples = 0L
    private var lastStartSample = 0L
    private var fallbackWarned = false

    fun accept(samples: FloatArray): List<VadSegment> {
        if (samples.isEmpty()) return emptyList()
        vad.acceptWaveform(samples)
        totalFedSamples += samples.size
        return drain()
    }

    /** 片尾没有足够的静音时，VAD 手里还攥着最后一段，必须显式排空。 */
    fun flush(): List<VadSegment> {
        vad.flush()
        return drain()
    }

    private fun drain(): List<VadSegment> {
        val out = ArrayList<VadSegment>()
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            val samples = segment.samples
            val span = VadTimeline.resolve(
                reportedStart = segment.start,
                lengthSamples = samples.size,
                totalFedSamples = totalFedSamples,
                lastStartSample = lastStartSample,
            )
            if (!fallbackWarned && span.startSample != segment.start.toLong()) {
                fallbackWarned = true
                Log.w(TAG, "VAD start=${segment.start} implausible; using ${span.startSample}")
            }
            lastStartSample = span.startSample
            out += VadSegment(span.startSample, span.endSample, samples)
        }
        return out
    }

    override fun close() {
        runCatching { vad.release() }
    }

    private companion object {
        const val TAG = "StreamingVadSegmenter"
    }
}
