package com.xzm.realtimetranslate.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures microphone PCM and resamples to 16 kHz mono LE for Live Translate.
 *
 * 阈值比媒体侧高得多：实测安静房间的麥克风底噪就在 RMS 11~15，而人声在 20~74。
 * 用 15 会让底噪正好卡在阈值上来回横跳（旧日志：13.2 报警 → 0.2 秒后 15.4 恢复）。
 */
class MicAudioCapturer(
    private val scope: CoroutineScope,
    private val onSilenceAlert: ((durationMs: Long, source: String) -> Unit)? = null,
    private val onSoundRestored: ((source: String) -> Unit)? = null,
    private val onLevel: ((CaptureState, rms: Double) -> Unit)? = null,
) {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val silenceDetector = AudioSilenceDetector(
        audioSourceTag = "Microphone",
        silenceThresholdRms = SILENCE_THRESHOLD_RMS,
        onSilenceAlert = onSilenceAlert,
        onSoundRestored = onSoundRestored,
    )

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    fun start(onPcm16k: (ByteArray) -> Unit) {
        stop()
        val sourceRate = 44_100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sourceRate, channelConfig, encoding)
        val bufferSize = (minBuf * 2).coerceAtLeast(sourceRate / 10 * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sourceRate,
            channelConfig,
            encoding,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("麦克风 AudioRecord 初始化失败")
        }

        audioRecord = record
        running = true
        record.startRecording()

        captureJob = scope.launch(Dispatchers.IO) {
            val readBuf = ShortArray(bufferSize / 2)
            val resampler = PcmResampler(sourceRate, 16_000)
            var totalBytes = 0L
            var firstRead = true
            var zeroReads = 0
            var lastHeartbeat = System.currentTimeMillis()
            var lastLevelAt = 0L

            while (isActive && running) {
                val n = record.read(readBuf, 0, readBuf.size)
                if (n > 0) {
                    zeroReads = 0
                    totalBytes += n * 2L
                    silenceDetector.processPcm(readBuf, n)
                    if (firstRead) {
                        firstRead = false
                        Log.i(
                            TAG,
                            "pcm first read n=$n rms=%.1f state=${silenceDetector.currentState}"
                                .format(silenceDetector.currentRms),
                        )
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastLevelAt >= LEVEL_INTERVAL_MS) {
                        lastLevelAt = now
                        onLevel?.invoke(silenceDetector.currentState, silenceDetector.currentRms)
                    }
                    if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                        lastHeartbeat = now
                        Log.i(
                            TAG,
                            "pcm heartbeat bytes=$totalBytes rms=%.1f state=${silenceDetector.currentState}"
                                .format(silenceDetector.currentRms),
                        )
                    }
                    val pcm16k = resampler.resample(readBuf, n)
                    if (pcm16k.isNotEmpty()) onPcm16k(pcm16k)
                } else if (n == 0) {
                    zeroReads++
                    if (zeroReads == ZERO_READ_STALL_COUNT) {
                        Log.w(TAG, "mic read returned 0 x$zeroReads — 采集器活着但不给数据")
                        onLevel?.invoke(CaptureState.STALLED, 0.0)
                    }
                } else {
                    Log.w(TAG, "mic read error: $n")
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        captureJob?.cancel()
        captureJob = null
        silenceDetector.reset()
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord?.release()
        audioRecord = null
    }

    companion object {
        private const val TAG = "MicAudioCapturer"
        private const val SILENCE_THRESHOLD_RMS = 40.0f
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val LEVEL_INTERVAL_MS = 200L
        private const val ZERO_READ_STALL_COUNT = 25
    }
}
