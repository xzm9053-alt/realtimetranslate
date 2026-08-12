package com.xzm.realtimetranslate.translate

import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local speech recognition: Silero VAD splits the 16 kHz mono PCM16 stream into
 * speech segments, SenseVoice (via sherpa-onnx OfflineRecognizer) transcribes
 * each segment on a background executor.
 *
 * [onSegment] is invoked on a background thread per recognized sentence.
 */
class AsrEngine(
    modelDir: File,
    sileroVadPath: String,
    private val onSegment: (String) -> Unit,
    numThreads: Int = 2,
) {

    // VAD runs synchronously on the caller thread (cheap); recognition is queued.
    private val vad = Vad(
        assetManager = null,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = sileroVadPath,
                threshold = 0.5f,
                minSilenceDuration = 0.2f,   // 停顿 200ms 切一段（用户指定，提速）
                minSpeechDuration = 0.25f,
                windowSize = 512,
                maxSpeechDuration = 2f,      // 单段最长 2s 强制切段（用户指定，字幕更快更跟嘴）
            ),
            sampleRate = 16000,
            numThreads = 1,
            provider = "cpu",
        ),
    )

    private val recognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = File(modelDir, "model.int8.onnx").absolutePath,
                    // empty language = auto-detect among zh/en/ja/ko/yue
                    language = "",
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = numThreads,
            ),
        ),
    )

    private val recognitionExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "asr-recognition").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)

    /** Feed 16 kHz mono PCM16 little-endian bytes. Any thread. */
    @Synchronized
    fun acceptPcm(pcm: ByteArray) {
        if (closed.get()) return
        if (pcm.size < 2) return
        val floats = FloatArray(pcm.size / 2)
        for (i in floats.indices) {
            val lo = pcm[i * 2].toInt() and 0xff
            val hi = pcm[i * 2 + 1].toInt()
            val s = ((hi shl 8) or lo).toShort()
            floats[i] = s / 32768.0f
        }
        vad.acceptWaveform(floats)
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            val samples = segment.samples
            recognitionExecutor.execute { runRecognition(samples) }
        }
    }

    fun reset() {
        runCatching { vad.reset() }
    }

    fun close() {
        if (closed.getAndSet(true)) return
        recognitionExecutor.shutdown()
        runCatching { vad.release() }
        runCatching { recognizer.release() }
    }

    private fun runRecognition(samples: FloatArray) {
        if (closed.get()) return
        try {
            val stream = recognizer.createStream()
            stream.acceptWaveform(samples, 16000)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            stream.release()
            // SenseVoice can emit <|...|> tags (emotion/event) and "Nose" for noise.
            var text = result.text
            text = text.replace(Regex("<\\|[^|]*\\|>"), "").trim()
            if (text.isBlank()) return
            if (text.equals("Nose", ignoreCase = true)) return
            if (text.equals("null", ignoreCase = true)) return // defensive edge case
            onSegment(text)
        } catch (t: Throwable) {
            Log.e(TAG, "recognition failed", t)
        }
    }

    companion object {
        private const val TAG = "AsrEngine"
    }
}
