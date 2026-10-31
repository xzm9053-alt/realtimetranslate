package com.xzm.realtimetranslate.video

import android.content.Context
import android.net.Uri
import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 视频翻译流水线：解码 → 重采样 → VAD 切段 → 识别 → 逐段翻译 → 字幕时间轴。
 *
 * 三个环节并行推进，中间用有界 Channel 连接：解码比识别快得多，有界通道负责背压，
 * 让内存只跟"正在处理的那几段"成正比，而不是跟着片长走。整片处理完才产出最终
 * 字幕列表（[SubtitleCueBuilder] 需要前后文才能合并与去重）。
 *
 * 识别器由调用方注入（本地 SenseVoice 或云端 Whisper），流水线只依赖
 * [BatchSpeechRecognizer] 这个"整段进、一句出"的契约。
 */
class VideoTranslationPipeline(
    private val context: Context,
    private val vadModelPath: String,
    private val recognizerFactory: () -> BatchSpeechRecognizer,
) {

    enum class Stage { EXTRACTING, RECOGNIZING }

    data class Result(
        val cues: List<TimedText>,
        val durationMs: Long,
        /** Segments VAD produced, including ones the recognizer dropped as noise. */
        val segmentCount: Int,
        val failedTranslations: Int,
    )

    suspend fun run(
        uri: Uri,
        translate: suspend (String) -> String?,
        onProgress: (Stage, Float) -> Unit = { _, _ -> },
        onSegment: (Int) -> Unit = {},
    ): Result = coroutineScope {
        val segments = Channel<VadSegment>(capacity = SEGMENT_BUFFER)
        val extractor = AudioPcmExtractor(context)
        val segmenter = StreamingVadSegmenter(vadModelPath)

        val startOffsetMs = AtomicLong(0L)
        val produced = AtomicInteger(0)
        val processed = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val durationMs = AtomicLong(0L)
        val cues = ArrayList<TimedText>()

        var recognizer: BatchSpeechRecognizer? = null
        try {
            val producer = launch(Dispatchers.Default) {
                try {
                    val info = extractor.extract(
                        uri = uri,
                        onProgress = { fraction -> onProgress(Stage.EXTRACTING, fraction) },
                        onAudioStart = { firstPtsUs -> startOffsetMs.set(firstPtsUs / 1000) },
                    ) { samples ->
                        // VAD 是纯 CPU 的同步调用，就在解码线程上做，省一次线程切换。
                        for (segment in segmenter.accept(samples)) {
                            produced.incrementAndGet()
                            segments.send(segment)
                        }
                    }
                    durationMs.set(info.durationMs)
                    for (segment in segmenter.flush()) {
                        produced.incrementAndGet()
                        segments.send(segment)
                    }
                } finally {
                    // release 必须与最后一次 native 调用在同一线程上先后发生，
                    // 所以在 producer 自己的 finally 里关，而不是外层协程被取消后去关。
                    runCatching { segmenter.close() }
                    segments.close()
                }
            }

            val consumer = launch(Dispatchers.Default) {
                val asr = recognizerFactory().also { recognizer = it }
                var lastReported = 0f
                try {
                    for (segment in segments) {
                        val offset = startOffsetMs.get()
                        val startMs = offset + VadTimeline.toMillis(segment.startSample)
                        val endMs = offset + VadTimeline.toMillis(segment.endSample)

                        val text = runCatching { asr.recognize(segment.samples) }
                            .onFailure { Log.w(TAG, "recognize failed", it) }
                            .getOrNull()
                        if (text != null) {
                            val translation = translateWithRetry(translate, text)
                            if (translation == null) failed.incrementAndGet()
                            cues += TimedText(
                                startMs = startMs,
                                endMs = endMs,
                                source = text,
                                translation = translation.orEmpty(),
                            )
                        }
                        val done = processed.incrementAndGet()
                        onSegment(done)
                        val total = produced.get().coerceAtLeast(1)
                        val fraction = (done.toFloat() / total).coerceIn(0f, 1f)
                        if (fraction >= lastReported) {
                            lastReported = fraction
                            onProgress(Stage.RECOGNIZING, fraction)
                        }
                    }
                } finally {
                    runCatching { asr.close() }
                }
            }

            listOf(producer, consumer).joinAll()
        } finally {
            // 正常路径下两个子协程都已关掉 native 资源；这里只在其中一个抛出后兜底，
            // 而且 recognizer 的关闭语义是幂等的 release。
            runCatching { recognizer?.close() }
        }

        val built = SubtitleCueBuilder.build(cues, durationMs.get())
        Result(
            cues = built,
            durationMs = durationMs.get(),
            segmentCount = produced.get(),
            failedTranslations = failed.get(),
        )
    }

    /** 单段翻译失败不中断整片：重试一次，仍失败就把原文当译文用。 */
    private suspend fun translateWithRetry(
        translate: suspend (String) -> String?,
        text: String,
    ): String? {
        repeat(MAX_TRANSLATE_ATTEMPTS) { attempt ->
            try {
                val result = withTimeout(TRANSLATE_TIMEOUT_MS) { translate(text) }
                if (!result.isNullOrBlank()) return result
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "translate attempt ${attempt + 1} failed", t)
            }
        }
        return null
    }

    private companion object {
        const val TAG = "VideoTranslationPipeline"
        const val SEGMENT_BUFFER = 4
        const val MAX_TRANSLATE_ATTEMPTS = 2
        const val TRANSLATE_TIMEOUT_MS = 45_000L
    }
}
