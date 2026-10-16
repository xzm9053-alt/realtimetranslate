package com.xzm.realtimetranslate.audio

import com.xzm.realtimetranslate.util.AppLog as Log
import kotlin.math.sqrt

/**
 * 实时 PCM16 音频能量监控与静音看门狗。
 * 用于监测系统内录或麦克风是否持续收到全零静音（例如目标 App 限制内录）。
 */
class AudioSilenceDetector(
    private val audioSourceTag: String,
    private val silenceThresholdRms: Float = 15.0f,
    private val warningDurationMs: Long = 10_000L,
    private val onSilenceAlert: ((durationMs: Long, audioSource: String) -> Unit)? = null,
    private val onSoundRestored: ((audioSource: String) -> Unit)? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    private var silenceStartMs: Long = 0L
    private var alertTriggered = false
    private var lastRms = 0.0

    val isAlertTriggered: Boolean get() = alertTriggered
    val currentRms: Double get() = lastRms

    /**
     * 分析 PCM16 ShortArray 音频帧能量
     */
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

    /**
     * 分析 PCM16 ByteArray 音频字节流能量
     */
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
        if (rms < silenceThresholdRms) {
            if (silenceStartMs == 0L) {
                silenceStartMs = now
            } else {
                val duration = now - silenceStartMs
                if (duration >= warningDurationMs && !alertTriggered) {
                    alertTriggered = true
                    Log.w(TAG, "[$audioSourceTag] 连续 ${duration / 1000} 秒检测到绝对静音 (RMS=%.1f)，可能被系统静音或应用限制内录".format(rms))
                    onSilenceAlert?.invoke(duration, audioSourceTag)
                }
            }
        } else {
            if (alertTriggered) {
                Log.i(TAG, "[$audioSourceTag] 音频信号恢复正常 (RMS=%.1f)".format(rms))
                onSoundRestored?.invoke(audioSourceTag)
            }
            silenceStartMs = 0L
            alertTriggered = false
        }
    }

    fun reset() {
        silenceStartMs = 0L
        alertTriggered = false
        lastRms = 0.0
    }

    companion object {
        private const val TAG = "AudioSilenceDetector"
    }
}
