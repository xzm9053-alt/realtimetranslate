package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.util.AppLog as Log
import android.os.SystemClock
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
 *
 * 可选地，[onPartial] 让同一套模型还能"边说边出"：VAD 还没切段时，把当前这一段的
 * 音频反复重解码，用 [StablePrefix] 求出两次都同意的前缀当作草稿报出去。模型、下载、
 * 联网都不变，代价是 CPU（每次草稿都是一次完整的整段解码）。
 */
class AsrEngine(
    modelDir: File,
    sileroVadPath: String,
    private val onSegment: (String) -> Unit,
    numThreads: Int = 2,
    /** SenseVoice language tag ("auto"/"zh"/"en"/"ja"/"ko"/"yue"); empty behaves as auto. */
    language: String = "auto",
    /** 停顿多久切一段（Silero minSilenceDuration）。 */
    vadMinSilenceDuration: Float = 0.2f,
    /** 单段语音最长多久强制切段（Silero maxSpeechDuration）。 */
    private val vadMaxSpeechDuration: Float = 2f,
    /**
     * 「边说边出」草稿回调：句子还没被 VAD 切出来之前，把已经稳下来的前半句报上来
     * （见 [StablePrefix]）。传 null 就整条草稿链路关掉——不重解码，也就没有额外耗电。
     */
    private val onPartial: ((String) -> Unit)? = null,
    /** 两次草稿识别之间的最小间隔；缓冲越长实际间隔越大，见 [maybeRunPartial]。 */
    private val partialIntervalMillis: Long = 400,
) {

    // VAD runs synchronously on the caller thread (cheap); recognition is queued.
    private val vad = Vad(
        assetManager = null,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = sileroVadPath,
                threshold = 0.5f,
                minSilenceDuration = vadMinSilenceDuration,
                minSpeechDuration = 0.25f,
                windowSize = 512,
                maxSpeechDuration = vadMaxSpeechDuration,
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
                    // "auto" = auto-detect among zh/en/ja/ko/yue; explicit tag pins the language
                    language = language,
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

    // ---- 「边说边出」状态：只有 onPartial 不为 null 时才会被用到 ------------------
    /**
     * 当前这句"还没被 VAD 切出来"的音频。每次草稿识别都把这一整段重新解码一遍——
     * 这正是 SenseVoice 这种非流式模型能边说边出的代价：不是增量解码，是反复整段解码。
     */
    private var pending = FloatArray(SAMPLE_RATE * 2)
    private var pendingSize = 0
    private val stablePrefix = StablePrefix()
    /** 草稿识别同一时刻只允许一个在跑；跑完才允许下一个进队列。 */
    private val partialInFlight = AtomicBoolean(false)
    private var lastPartialAtMs = 0L

    /**
     * 草稿识别和定稿识别共用这一个单线程 executor，这是刻意的：
     *  - sherpa-onnx 的 OfflineRecognizer 不是线程安全的，两个模型实例又要再吃一份
     *    239MB 的模型内存，手机上不能这么干；
     *  - 同一个队列是 FIFO，草稿总是在"它所属那句"的定稿之前入队，所以草稿结果不可能
     *    跑到定稿后面去，顺序天然是对的。
     * 代价是最坏情况下定稿要等一个正在跑的草稿解码结束，[maybeRunPartial] 用自适应
     * 间隔把这个等待压在一小段时间内。
     */

    /**
     * 保护 native 句柄：任何一次 vad / recognizer 调用都不能和 [close] 里的 release
     * 重叠——把句柄从正在跑的 native 解码底下抽走是进程级崩溃，不是异常。
     * 两把锁分开，且从不嵌套：解码要占住 recognizer 几百毫秒，如果和喂音频共用一把锁，
     * 音频回调就会被解码卡住丢帧。
     */
    private val vadLock = Any()
    private val recognizerLock = Any()

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
        var popped = false
        synchronized(vadLock) {
            vad.acceptWaveform(floats)
            while (!vad.empty()) {
                val segment = vad.front()
                vad.pop()
                popped = true
                // 这句定稿了：草稿缓冲和"已经稳下来"的判断一起清零，从下一句重新攒。
                pendingSize = 0
                stablePrefix.reset()
                val samples = segment.samples
                recognitionExecutor.execute { runRecognition(samples) }
            }
            // 这一段是 VAD 认定的语音，先攒着——但只在下一次切段之前有用。
            // 切段那一块音频已经由定稿识别覆盖了，不必再进草稿缓冲。
            if (!popped && onPartial != null && vad.isSpeechDetected()) {
                appendPending(floats)
            }
        }
        if (!popped) maybeRunPartial()
    }

    fun reset() {
        synchronized(vadLock) { runCatching { vad.reset() } }
        pendingSize = 0
        stablePrefix.reset()
    }

    fun close() {
        if (closed.getAndSet(true)) return
        // 释放排在所有已入队的识别之后、且在识别线程上完成：直接在这里 release，
        // 就会和队列里正在跑的 native 解码抢同一份模型。
        recognitionExecutor.execute {
            synchronized(recognizerLock) { runCatching { recognizer.release() } }
            synchronized(vadLock) { runCatching { vad.release() } }
        }
        recognitionExecutor.shutdown()
    }

    private fun appendPending(frames: FloatArray) {
        if (pendingSize + frames.size > pending.size) {
            var capacity = pending.size
            while (capacity < pendingSize + frames.size) capacity *= 2
            pending = pending.copyOf(capacity)
        }
        System.arraycopy(frames, 0, pending, pendingSize, frames.size)
        pendingSize += frames.size
        // 兜底上限：正常情况下 VAD 到 maxSpeechDuration 就切段了，草稿缓冲攒不到这么长；
        // 万一没切（长时间连续说话），也绝不让每次重解码越来越贵。
        // 真触到这个上限时草稿会停在已显示的部分——比卡顿好。
        val cap = (SAMPLE_RATE * (vadMaxSpeechDuration + 0.5f)).toInt()
        if (pendingSize > cap) {
            System.arraycopy(pending, pendingSize - cap, pending, 0, cap)
            pendingSize = cap
        }
    }

    /**
     * 决定要不要再跑一次草稿识别。
     *
     * 间隔随缓冲长度线性放大：解码耗时本身就随音频长度线性增长，固定间隔会让长句子
     * 上的草稿识别连轴转，把紧随其后的定稿识别一直挤在队列里。
     */
    private fun maybeRunPartial() {
        if (pendingSize < MIN_PARTIAL_SAMPLES) return
        val pendingMs = pendingSize * 1000L / SAMPLE_RATE
        val interval = maxOf(partialIntervalMillis, pendingMs * PARTIAL_DUTY_PERCENT / 100)
        val now = SystemClock.elapsedRealtime()
        if (now - lastPartialAtMs < interval) return
        if (!partialInFlight.compareAndSet(false, true)) return
        lastPartialAtMs = now
        val snapshot = pending.copyOf(pendingSize)
        recognitionExecutor.execute { runPartial(snapshot) }
    }

    private fun runPartial(samples: FloatArray) {
        try {
            if (closed.get()) return
            val callback = onPartial ?: return
            val text = transcribe(samples) ?: return
            // 两次结果都对上的那一段才算数；没变长就不必打扰下游。
            val stable = stablePrefix.accept(text) ?: return
            callback(stable)
        } catch (t: Throwable) {
            Log.e(TAG, "partial recognition failed", t)
        } finally {
            partialInFlight.set(false)
        }
    }

    private fun runRecognition(samples: FloatArray) {
        if (closed.get()) return
        try {
            val text = transcribe(samples) ?: return
            onSegment(text)
        } catch (t: Throwable) {
            Log.e(TAG, "recognition failed", t)
        }
    }

    /**
     * 一段音频 → 文字；结果是噪声/空白时返回 null。
     * SenseVoice 会吐 `<|...|>` 标签（语种/情绪/事件），要剥掉。
     */
    private fun transcribe(samples: FloatArray): String? {
        val text = synchronized(recognizerLock) {
            if (closed.get()) return null
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                recognizer.decode(stream)
                recognizer.getResult(stream).text
                    .replace(TAG_PATTERN, "")
                    .trim()
            } finally {
                stream.release()
            }
        }
        return when {
            text.isBlank() -> null
            text.equals("Nose", ignoreCase = true) -> null // 噪声标记
            text.equals("null", ignoreCase = true) -> null // 防御性边界情况
            else -> text
        }
    }

    companion object {
        private const val TAG = "AsrEngine"
        private const val SAMPLE_RATE = 16_000

        /** 短于这个长度的音频不值得解码：太短识别不出东西，白烧 CPU。 */
        private const val MIN_PARTIAL_SAMPLES = SAMPLE_RATE * 8 / 10

        /**
         * 草稿识别允许占用的时间比例（%）：间隔至少是缓冲长度的这个百分比。
         * 取 50 是因为草稿解码本身就要花掉缓冲时长的一小部分，留出余量后，紧随其后的
         * 定稿识别最多等一个草稿解码（默认参数下约 0.2~0.5 秒）。
         */
        private const val PARTIAL_DUTY_PERCENT = 50L

        private val TAG_PATTERN = Regex("<\\|[^|]*\\|>")

        /**
         * Map a UserSettings.sourceLanguageCode to the SenseVoice language tag.
         * SenseVoice supports auto/zh/en/ja/ko/yue only; unsupported codes (and
         * "auto") fall back to auto-detect so the recognizer never breaks.
         */
        fun senseVoiceLanguageFor(code: String): String = when (code) {
            "zh-Hans", "zh-Hant" -> "zh"
            "en" -> "en"
            "ja" -> "ja"
            "ko" -> "ko"
            "yue" -> "yue"
            else -> "auto"
        }
    }
}
