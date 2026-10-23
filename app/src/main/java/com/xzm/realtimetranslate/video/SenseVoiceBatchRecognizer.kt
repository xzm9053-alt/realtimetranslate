package com.xzm.realtimetranslate.video

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.xzm.realtimetranslate.translate.AsrEngine
import java.io.File

/**
 * 整段识别用的 SenseVoice 封装（视频字幕）。
 *
 * 与 [AsrEngine] 的区别：这里是"一段音频进、一句文字出"，没有 VAD，也没有草稿重解码。
 * 同一个实例串行识别所有段，避免为每段加载一次 239 MB 的模型。
 */
class SenseVoiceBatchRecognizer(
    modelDir: File,
    language: String = "auto",
    numThreads: Int = 2,
) : AutoCloseable {

    private val recognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = VadTimeline.SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = File(modelDir, "model.int8.onnx").absolutePath,
                    language = language,
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = numThreads,
            ),
        ),
    )

    /** Returns null for silence/noise so the caller can drop the segment. */
    fun recognize(samples: FloatArray): String? {
        if (samples.isEmpty()) return null
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, VadTimeline.SAMPLE_RATE)
            recognizer.decode(stream)
            AsrEngine.cleanSenseVoiceText(recognizer.getResult(stream).text)
        } finally {
            stream.release()
        }
    }

    override fun close() {
        runCatching { recognizer.release() }
    }
}
