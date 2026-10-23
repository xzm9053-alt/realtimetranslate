package com.xzm.realtimetranslate.video

/** A VAD segment expressed in absolute 16 kHz sample indices. */
data class SampleSpan(
    val startSample: Long,
    val endSample: Long,
) {
    val lengthSamples: Long get() = endSample - startSample
}

/**
 * Silero VAD 报出的段起点是"喂进流里的第几个样本"，但这不是文档保证的行为；
 * 一旦它变成相对值或越界，时间轴会整体错位。这里做一次合理性校验，不可信时
 * 退回到"当前已喂样本数 - 段长"，宁可差一段也不做一条错的时间轴。
 */
object VadTimeline {

    const val SAMPLE_RATE = 16_000

    fun resolve(
        reportedStart: Int,
        lengthSamples: Int,
        totalFedSamples: Long,
        lastStartSample: Long,
    ): SampleSpan {
        val length = lengthSamples.coerceAtLeast(0)
        val latestPossible = (totalFedSamples - length).coerceAtLeast(0L)
        val candidate = reportedStart.toLong()
        val start = if (candidate in lastStartSample..latestPossible) {
            candidate
        } else {
            latestPossible
        }
        return SampleSpan(start, start + length)
    }

    fun toMillis(sample: Long, sampleRate: Int = SAMPLE_RATE): Long =
        sample * 1000L / sampleRate
}
