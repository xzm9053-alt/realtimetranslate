package com.xzm.realtimetranslate.audio

import com.xzm.realtimetranslate.util.AppLog as Log
import kotlin.math.sqrt

/**
 * 实时 PCM16 音频能量监控。
 *
 * 两条独立计时，因为「收到的是数字静音」和「只是没人说话」是两回事：
 *
 * - [zeroSinceMs] —— 每个采样都精确为 0，持续 [zeroDurationMs] → [CaptureState.DIGITAL_SILENCE]
 * - [quietSinceMs] —— 有底噪但低于阈值，持续 [warningDurationMs] → [CaptureState.QUIET]
 *
 * 上一版只有一个 `rms < threshold` 计时器，且把结果一律写成「可能限制内录」，
 * 把安静误报成故障。
 */
class AudioSilenceDetector(
    private val audioSourceTag: String,
    private val silenceThresholdRms: Float = 15.0f,
    private val warningDurationMs: Long = 10_000L,
    private val zeroDurationMs: Long = 3_000L,
    private val onSilenceAlert: ((durationMs: Long, audioSource: String) -> Unit)? = null,
    private val onSoundRestored: ((audioSource: String) -> Unit)? = null,
    private val onStateChanged: ((state: CaptureState, rms: Double) -> Unit)? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    private var zeroSinceMs: Long = 0L
    private var quietSinceMs: Long = 0L
    private var alertTriggered = false
    private var lastRms = 0.0
    private var lastState = CaptureState.STARTING

    /**
     * 本次会话是否真的收到过声音。
     *
     * 这决定了零值的含义：见过声音之后的零是播放间隙（路由切换、缓冲区下溢，实测有 3~4 秒），
     * 不该报警；从未见过声音的零才是「投影没被激活」。
     */
    private var sawSound = false

    val isAlertTriggered: Boolean get() = alertTriggered
    val currentRms: Double get() = lastRms
    val currentState: CaptureState get() = lastState

    /** 分析 PCM16 ShortArray 音频帧能量 */
    fun processPcm(samples: ShortArray, count: Int) {
        if (count <= 0) return
        var sumSquares = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / count)
        lastRms = rms
        checkRms(rms)
    }

    /** 分析 PCM16 ByteArray 音频字节流能量 */
    fun processPcmBytes(pcmBytes: ByteArray, byteCount: Int) {
        if (byteCount <= 1) return
        val numSamples = byteCount / 2
        var sumSquares = 0.0
        for (i in 0 until numSamples) {
            val lo = pcmBytes[i * 2].toInt() and 0xFF
            val hi = pcmBytes[i * 2 + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toDouble()
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / numSamples)
        lastRms = rms
        checkRms(rms)
    }

    private fun checkRms(rms: Double) {
        val now = timeProvider()
        if (rms >= silenceThresholdRms) sawSound = true

        val state = when {
            rms == 0.0 -> {
                quietSinceMs = 0L
                if (zeroSinceMs == 0L) zeroSinceMs = now
                val silentFor = now - zeroSinceMs
                when {
                    // 从未出声就一直零 —— 这才是投影没激活 / 被拒。
                    !sawSound && silentFor >= zeroDurationMs -> CaptureState.DIGITAL_SILENCE
                    // 出过声之后的零是播放间隙，要更长才算真断。
                    sawSound && silentFor >= GAP_DURATION_MS -> CaptureState.DIGITAL_SILENCE
                    else -> CaptureState.STARTING
                }
            }
            rms < silenceThresholdRms -> {
                zeroSinceMs = 0L
                if (quietSinceMs == 0L) quietSinceMs = now
                if (now - quietSinceMs >= warningDurationMs) CaptureState.QUIET
                else CaptureState.FLOWING
            }
            else -> {
                zeroSinceMs = 0L
                quietSinceMs = 0L
                CaptureState.FLOWING
            }
        }

        if (state == CaptureState.DIGITAL_SILENCE && !alertTriggered) {
            alertTriggered = true
            val duration = now - zeroSinceMs
            Log.w(
                TAG,
                "[$audioSourceTag] 收到的是数字静音 (RMS=0.0, 持续 ${duration / 1000} 秒)",
            )
            onSilenceAlert?.invoke(duration, audioSourceTag)
        } else if (alertTriggered && state == CaptureState.FLOWING) {
            alertTriggered = false
            Log.i(TAG, "[$audioSourceTag] 音频信号恢复正常 (RMS=%.1f)".format(rms))
            onSoundRestored?.invoke(audioSourceTag)
        }

        if (state != lastState) {
            lastState = state
            onStateChanged?.invoke(state, rms)
        }
    }

    fun reset() {
        zeroSinceMs = 0L
        quietSinceMs = 0L
        alertTriggered = false
        lastRms = 0.0
        lastState = CaptureState.STARTING
        sawSound = false
    }

    companion object {
        private const val TAG = "AudioSilenceDetector"

        /** 出过声之后，多长的连续零才算真断开。实测播放中的空档最长约 4 秒。 */
        private const val GAP_DURATION_MS = 8_000L
    }
}
