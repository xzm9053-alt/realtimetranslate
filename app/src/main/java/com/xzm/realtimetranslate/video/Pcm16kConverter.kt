package com.xzm.realtimetranslate.video

/** One decoded audio sample format as reported by MediaCodec's `KEY_PCM_ENCODING`. */
enum class PcmEncoding(val bytesPerSample: Int) {
    PCM8(1),
    PCM16(2),
    PCM24(3),
    PCM32(4),
    FLOAT32(4),
    ;

    companion object {
        // Literal values of android.media.AudioFormat's ENCODING_PCM_* constants;
        // kept local so this file (and its unit tests) never touch Android classes.
        const val ANDROID_PCM_16BIT = 2
        const val ANDROID_PCM_8BIT = 3
        const val ANDROID_PCM_FLOAT = 4
        const val ANDROID_PCM_24BIT_PACKED = 21
        const val ANDROID_PCM_32BIT = 22

        fun fromAndroidEncoding(value: Int): PcmEncoding = when (value) {
            ANDROID_PCM_FLOAT -> FLOAT32
            ANDROID_PCM_8BIT -> PCM8
            ANDROID_PCM_24BIT_PACKED -> PCM24
            ANDROID_PCM_32BIT -> PCM32
            else -> PCM16
        }
    }
}

/**
 * 把任意采样率 / 声道数 / 位深的 PCM 转成 16 kHz 单声道 float。
 *
 * 重采样是跨块连续的线性插值：游标属于转换器而不是每次调用，所以块边界处
 * 不会出现相位跳变（逐个块独立重采样会在边界产生咔哒声）。
 */
class Pcm16kConverter(
    private val inputSampleRate: Int,
    private val channelCount: Int,
    private val encoding: PcmEncoding,
    private val outputSampleRate: Int = OUTPUT_SAMPLE_RATE,
) {
    init {
        require(inputSampleRate > 0) { "inputSampleRate must be positive" }
        require(channelCount > 0) { "channelCount must be positive" }
    }

    private val bytesPerFrame = encoding.bytesPerSample * channelCount

    private var mono = FloatArray(INITIAL_CAPACITY)
    private var monoSize = 0

    /** Fractional read position in [mono]; preserved across calls on purpose. */
    private var cursor = 0.0

    /** Trailing bytes of an incomplete frame, carried into the next chunk. */
    private var carry = ByteArray(0)

    fun convert(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size): FloatArray {
        if (size <= 0) return EMPTY
        appendMono(bytes, offset, size)
        if (monoSize < 2) return EMPTY

        val ratio = inputSampleRate.toDouble() / outputSampleRate
        val estimate = (((monoSize - cursor) / ratio).toInt() + 2).coerceIn(1, monoSize * 4)
        var out = FloatArray(estimate)
        var count = 0
        while (true) {
            val index = cursor.toInt()
            if (index + 1 >= monoSize) break
            if (count == out.size) out = out.copyOf(out.size * 2)
            val fraction = (cursor - index).toFloat()
            out[count++] = mono[index] * (1f - fraction) + mono[index + 1] * fraction
            cursor += ratio
        }
        compact()
        return if (count == out.size) out else out.copyOf(count)
    }

    private fun appendMono(bytes: ByteArray, offset: Int, size: Int) {
        val data = if (carry.isEmpty()) {
            bytes.copyOfRange(offset, offset + size)
        } else {
            carry + bytes.copyOfRange(offset, offset + size)
        }
        val frames = data.size / bytesPerFrame
        if (frames > 0) {
            ensureCapacity(monoSize + frames)
            for (frame in 0 until frames) {
                var sum = 0f
                val base = frame * bytesPerFrame
                for (channel in 0 until channelCount) {
                    sum += readSample(data, base + channel * encoding.bytesPerSample)
                }
                mono[monoSize++] = sum / channelCount
            }
        }
        val used = frames * bytesPerFrame
        carry = if (used == data.size) EMPTY_BYTES else data.copyOfRange(used, data.size)
    }

    private fun readSample(data: ByteArray, pos: Int): Float = when (encoding) {
        PcmEncoding.PCM8 ->
            ((data[pos].toInt() and 0xff) - 128) / 128f
        PcmEncoding.PCM16 ->
            (((data[pos + 1].toInt() shl 8) or (data[pos].toInt() and 0xff)).toShort()) / 32768f
        PcmEncoding.PCM24 -> {
            val value = (data[pos].toInt() and 0xff) or
                ((data[pos + 1].toInt() and 0xff) shl 8) or
                (data[pos + 2].toInt() shl 16)
            value / 8_388_608f
        }
        PcmEncoding.PCM32 -> {
            val value = (data[pos].toInt() and 0xff) or
                ((data[pos + 1].toInt() and 0xff) shl 8) or
                ((data[pos + 2].toInt() and 0xff) shl 16) or
                (data[pos + 3].toInt() shl 24)
            value / 2_147_483_648f
        }
        PcmEncoding.FLOAT32 -> Float.fromBits(
            (data[pos].toInt() and 0xff) or
                ((data[pos + 1].toInt() and 0xff) shl 8) or
                ((data[pos + 2].toInt() and 0xff) shl 16) or
                (data[pos + 3].toInt() shl 24),
        )
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= mono.size) return
        var capacity = mono.size
        while (capacity < needed) capacity *= 2
        mono = mono.copyOf(capacity)
    }

    /** Drop samples that can no longer be read, keeping the one used for interpolation. */
    private fun compact() {
        val drop = cursor.toInt().coerceAtMost(monoSize)
        if (drop <= 0) return
        mono.copyInto(mono, 0, drop, monoSize)
        monoSize -= drop
        cursor -= drop
    }

    companion object {
        const val OUTPUT_SAMPLE_RATE = 16_000
        private const val INITIAL_CAPACITY = 8192
        private val EMPTY = FloatArray(0)
        private val EMPTY_BYTES = ByteArray(0)
    }
}
