package com.xzm.realtimetranslate.audio

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures other apps' media playback via AudioPlaybackCapture (API 29+),
 * then downsamples to 16-bit mono PCM @ 16 kHz for Live Translate.
 *
 * 必须在投影会话上挂一个活着的 VirtualDisplay。部分 ROM（已确认包括
 * vivo V2054A / Android 11）在投影从未被真正使用时不开放音频数据：
 * AudioRecord 照样初始化成功、read() 照样返回满长缓冲区，但内容全是 0。
 * 表现就是静音看门狗报「收到的是数字静音」。
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SystemAudioCapturer(
    private val scope: CoroutineScope,
    private val onSilenceAlert: ((durationMs: Long, source: String) -> Unit)? = null,
    private val onSoundRestored: ((source: String) -> Unit)? = null,
    private val onLevel: ((CaptureState, rms: Double) -> Unit)? = null,
) {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var displayThread: HandlerThread? = null

    private val silenceDetector = AudioSilenceDetector(
        audioSourceTag = "MediaProjection",
        silenceThresholdRms = SILENCE_THRESHOLD_RMS,
        onSilenceAlert = onSilenceAlert,
        onSoundRestored = onSoundRestored,
    )

    /** 最近一次尝试激活投影的结果，供诊断报告读取。 */
    @Volatile
    var projectionActivated: Boolean = false
        private set

    @Volatile
    private var running = false

    fun start(
        mediaProjection: MediaProjection,
        onPcm16k: (ByteArray) -> Unit,
    ) {
        stop()

        if (ACTIVATE_PROJECTION_FOR_AUDIO) {
            activateProjection(mediaProjection)
        }

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val sourceRate = 44_100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sourceRate, channelConfig, encoding)
        val bufferSize = (minBuf * 2).coerceAtLeast(sourceRate / 10 * 2)

        val record = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sourceRate)
                    .setChannelMask(channelConfig)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord 初始化失败，无法内录系统音频")
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
                        Log.w(TAG, "pcm read returned 0 x$zeroReads — 采集器活着但不给数据")
                        onLevel?.invoke(CaptureState.STALLED, 0.0)
                    }
                } else {
                    Log.w(TAG, "AudioRecord read error: $n")
                    break
                }
            }
        }
    }

    /**
     * 在投影会话上建一个 1x1 的空屏。不渲染、不占内存，只为把投影真正激活。
     *
     * 失败不阻断音频：宽容的 ROM 不需要它也能出声，不能因为这一步把本来能用的机型搞坏。
     */
    private fun activateProjection(mediaProjection: MediaProjection) {
        val thread = HandlerThread("AudioCaptureDisplay").apply { start() }
        displayThread = thread
        val handler = Handler(thread.looper)

        val created = runCatching {
            mediaProjection.createVirtualDisplay(
                "AudioCapture",
                1,
                1,
                1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null,
                null,
                handler,
            )
        }.getOrNull()

        virtualDisplay = created
        projectionActivated = created != null
        if (created != null) {
            Log.i(TAG, "virtual display created (audio-only activation)")
        } else {
            Log.w(TAG, "virtual display creation failed — continuing without activation")
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
        // VirtualDisplay 必须先于 projection 释放，否则后续会话会撞上投影数量超限。
        virtualDisplay?.release()
        virtualDisplay = null
        displayThread?.quitSafely()
        displayThread = null
        projectionActivated = false
    }

    companion object {
        private const val TAG = "SystemAudioCapturer"
        private const val SILENCE_THRESHOLD_RMS = 15.0f
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val LEVEL_INTERVAL_MS = 200L
        private const val ZERO_READ_STALL_COUNT = 25

        /**
         * 投影激活开关。若线上反馈「本来能用的机型反而坏了」，
         * 改成 false 重新打包即可回到旧行为，其余改动全部保留。
         */
        private const val ACTIVATE_PROJECTION_FOR_AUDIO = true
    }
}
