package com.xzm.realtimetranslate.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.audio.MicAudioCapturer
import com.xzm.realtimetranslate.audio.PcmMixer
import com.xzm.realtimetranslate.audio.SystemAudioCapturer
import com.xzm.realtimetranslate.audio.TranslatedAudioPlayer
import com.xzm.realtimetranslate.data.AudioSourceMode
import com.xzm.realtimetranslate.data.HistoryEntry
import com.xzm.realtimetranslate.data.HistoryMode
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.live.RealtimeTranslationClient
import com.xzm.realtimetranslate.overlay.SubtitleOverlayController
import com.xzm.realtimetranslate.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground session: audio capture (media / mic / both) + Live WS + floating overlay.
 */
class SubtitleSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaProjection: MediaProjection? = null
    private var mediaCapturer: SystemAudioCapturer? = null
    private var micCapturer: MicAudioCapturer? = null
    private var pcmMixer: PcmMixer? = null
    private var liveClient: RealtimeTranslationClient? = null
    private var audioPlayer: TranslatedAudioPlayer? = null
    private var overlay: SubtitleOverlayController? = null
    private var settingsJob: Job? = null
    private var eventsJob: Job? = null
    private var commandJob: Job? = null

    private var currentSettings: UserSettings = UserSettings()
    private var audioSourceMode: AudioSourceMode = AudioSourceMode.MEDIA
    private var captureStarted = false

    private var accumulatedInput = StringBuilder()
    private var accumulatedOutput = StringBuilder()
    private var fullInput = StringBuilder()
    private var fullOutput = StringBuilder()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        commandJob = scope.launch {
            SessionBus.commands.collect { cmd ->
                when (cmd) {
                    SessionBus.Command.Stop -> stopEverything("已停止")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything("已停止")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val modeName = intent.getStringExtra(EXTRA_AUDIO_SOURCE)
                audioSourceMode = AudioSourceMode.fromStorage(modeName)
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (audioSourceMode.needsMediaProjection) {
                    if (data == null || resultCode != Activity.RESULT_OK) {
                        SessionBus.setStatus(SessionBus.Status.Error, "录屏授权失败")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    startSession(resultCode, data)
                } else {
                    startSession(resultCode = null, data = null)
                }
            }
        }
        return START_STICKY
    }

    private fun startSession(resultCode: Int?, data: Intent?) {
        SessionBus.setStatus(SessionBus.Status.Starting, "正在启动…")
        SessionBus.clearExport()
        accumulatedInput.clear()
        accumulatedOutput.clear()
        fullInput.clear()
        fullOutput.clear()
        captureStarted = false
        startAsForeground()

        val app = application as LiveTranslateApp

        scope.launch {
            currentSettings = app.settingsRepository.settings.first()
            // audioSourceMode already set from Intent EXTRA in onStartCommand.

            // Engine-aware credential gate: DeepSeek needs a key; Microsoft is keyless.
            if (currentSettings.translationEngine == TranslationEngineType.DEEPSEEK &&
                !app.apiKeyStore.hasDeepSeekKey()
            ) {
                SessionBus.setStatus(SessionBus.Status.Error, "请先在设置中填写 DeepSeek API Key")
                stopSelf()
                return@launch
            }

            if (audioSourceMode.needsMediaProjection) {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = mpm.getMediaProjection(resultCode!!, data!!)
                if (projection == null) {
                    SessionBus.setStatus(SessionBus.Status.Error, "无法创建 MediaProjection")
                    stopSelf()
                    return@launch
                }
                mediaProjection = projection
                projection.registerCallback(
                    object : MediaProjection.Callback() {
                        override fun onStop() {
                            stopEverything("录屏权限已撤销")
                        }
                    },
                    null,
                )
            }

            val overlayController = SubtitleOverlayController(this@SubtitleSessionService) { x, y, w, h ->
                ioScope.launch {
                    app.settingsRepository.update {
                        it.copy(overlayX = x, overlayY = y, overlayWidthDp = w, overlayHeightDp = h)
                    }
                }
            }
            overlay = overlayController
            overlayController.show(currentSettings)

            val client = RealtimeTranslationClient(app)
            liveClient = client

            val player = TranslatedAudioPlayer()
            audioPlayer = player
            player.setEnabled(currentSettings.playTranslatedAudio)
            player.setVolume(currentSettings.translatedVolume)

            eventsJob = scope.launch {
                launch {
                    client.connectionState.collect { state ->
                        when (state) {
                            is RealtimeTranslationClient.ConnectionState.Ready -> {
                                SessionBus.setStatus(SessionBus.Status.Running, "翻译中")
                                startCapturePipeline(client)
                            }
                            is RealtimeTranslationClient.ConnectionState.Failed -> {
                                SessionBus.setStatus(SessionBus.Status.Error, state.message)
                            }
                            else -> Unit
                        }
                    }
                }
                client.events.collect { event ->
                    when (event) {
                        is RealtimeTranslationClient.LiveEvent.SetupComplete -> {
                            SessionBus.setStatus(SessionBus.Status.Running, "翻译中")
                            startCapturePipeline(client)
                        }
                        is RealtimeTranslationClient.LiveEvent.InputTranscript -> {
                            appendTranscript(accumulatedInput, event.text)
                            appendFull(fullInput, event.text)
                            val text = accumulatedInput.toString()
                            overlay?.updateTranscripts(input = text, output = null)
                            SessionBus.setPreview(input = text)
                        }
                        is RealtimeTranslationClient.LiveEvent.OutputTranscript -> {
                            appendTranscript(accumulatedOutput, event.text)
                            appendFull(fullOutput, event.text)
                            val text = accumulatedOutput.toString()
                            overlay?.updateTranscripts(input = null, output = text)
                            SessionBus.setPreview(output = text)
                        }
                        // 新句开始：清空当前输出行，避免流式翻译的增量被逐段追加成上一句的重复堆叠。
                        // fullOutput 保持累积，导出会话仍保留完整历史。
                        is RealtimeTranslationClient.LiveEvent.OutputReset -> {
                            accumulatedOutput.clear()
                            overlay?.updateTranscripts(input = null, output = "")
                            SessionBus.setPreview(output = "")
                        }
                        is RealtimeTranslationClient.LiveEvent.AudioChunk -> {
                            if (currentSettings.playTranslatedAudio) {
                                player.playPcm(event.pcm, event.mimeType)
                            }
                        }
                        is RealtimeTranslationClient.LiveEvent.Error -> {
                            SessionBus.setStatus(SessionBus.Status.Error, event.message)
                        }
                        is RealtimeTranslationClient.LiveEvent.Debug -> {
                            Log.d(TAG, event.message)
                        }
                    }
                }
            }

            settingsJob = scope.launch {
                app.settingsRepository.settings.collectLatest { s ->
                    val prevPlay = currentSettings.playTranslatedAudio
                    currentSettings = s
                    overlay?.updateSettings(s)
                    player.setEnabled(s.playTranslatedAudio)
                    player.setVolume(s.translatedVolume)
                    if (prevPlay && !s.playTranslatedAudio) {
                        Log.i(TAG, "translated audio disabled")
                    }
                }
            }

            client.connect(
                RealtimeTranslationClient.SessionConfig(
                    endpoint = currentSettings.endpoint,
                    apiKey = app.apiKeyStore.getDeepSeekKey(),
                    modelId = currentSettings.deepseekModel,
                    targetLanguageCode = currentSettings.targetLanguageCode,
                    echoTargetLanguage = true,
                ),
            )
        }
    }

    private fun startCapturePipeline(client: RealtimeTranslationClient) {
        if (captureStarted) return
        captureStarted = true
        try {
            when (audioSourceMode) {
                AudioSourceMode.MEDIA -> {
                    val projection = mediaProjection
                        ?: throw IllegalStateException("缺少 MediaProjection")
                    val cap = SystemAudioCapturer(ioScope)
                    mediaCapturer = cap
                    cap.start(projection) { pcm -> client.sendPcm16le(pcm, 16_000) }
                }
                AudioSourceMode.MIC -> {
                    val mic = MicAudioCapturer(ioScope)
                    micCapturer = mic
                    mic.start { pcm -> client.sendPcm16le(pcm, 16_000) }
                }
                AudioSourceMode.MEDIA_AND_MIC -> {
                    val projection = mediaProjection
                        ?: throw IllegalStateException("缺少 MediaProjection")
                    val mixer = PcmMixer { mixed -> client.sendPcm16le(mixed, 16_000) }
                    pcmMixer = mixer
                    val media = SystemAudioCapturer(ioScope)
                    mediaCapturer = media
                    media.start(projection) { pcm -> mixer.offerMedia(pcm) }
                    val mic = MicAudioCapturer(ioScope)
                    micCapturer = mic
                    mic.start { pcm -> mixer.offerMic(pcm) }
                }
            }
            SessionBus.setStatus(SessionBus.Status.Running, "翻译中 · 等待声音…")
        } catch (e: Exception) {
            Log.e(TAG, "capture start failed", e)
            SessionBus.setStatus(SessionBus.Status.Error, e.message ?: "音频采集启动失败")
            stopEverything(e.message ?: "音频采集启动失败")
        }
    }

    private fun appendTranscript(buffer: StringBuilder, chunk: String) {
        if (chunk.length >= buffer.length && chunk.startsWith(buffer.toString())) {
            buffer.clear()
            buffer.append(chunk)
        } else if (buffer.endsWith(chunk)) {
            // ignore
        } else {
            if (buffer.isNotEmpty() && !buffer.last().isWhitespace() && chunk.isNotEmpty() &&
                !chunk.first().isWhitespace()
            ) {
                buffer.append(' ')
            }
            buffer.append(chunk)
        }
        if (buffer.length > 800) {
            buffer.delete(0, buffer.length - 800)
        }
    }

    private fun appendFull(buffer: StringBuilder, chunk: String) {
        // Prefer cumulative server rewrites when present
        if (chunk.length >= buffer.length && buffer.isNotEmpty() && chunk.startsWith(buffer.toString())) {
            buffer.clear()
            buffer.append(chunk)
            return
        }
        if (buffer.endsWith(chunk)) return
        if (buffer.isNotEmpty() && !buffer.last().isWhitespace() && chunk.isNotEmpty() &&
            !chunk.first().isWhitespace()
        ) {
            buffer.append(' ')
        }
        buffer.append(chunk)
        // Cap export size ~200k chars
        if (buffer.length > 200_000) {
            buffer.delete(0, buffer.length - 200_000)
        }
    }

    private fun startAsForeground() {
        val stopIntent = Intent(this, SubtitleSessionService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, LiveTranslateApp.CHANNEL_SUBTITLE)
            .setContentTitle(getString(R.string.notification_subtitle_running))
            .setContentText(getString(R.string.notification_subtitle_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.action_stop), stopPi)
            .setOngoing(true)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (audioSourceMode) {
                AudioSourceMode.MEDIA ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                AudioSourceMode.MIC ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                AudioSourceMode.MEDIA_AND_MIC ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        } else {
            0
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fgsType != 0) {
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopEverything(message: String) {
        val inFull = fullInput.toString()
        val outFull = fullOutput.toString()
        if (inFull.isNotBlank() || outFull.isNotBlank()) {
            SessionBus.markSessionFinished(inFull, outFull, message)
            if (currentSettings.historyMode == HistoryMode.SAVE_ALL) {
                // Fire-and-forget via the repository's process-scoped IO scope:
                // this service's ioScope is cancelled in onDestroy right after.
                (application as LiveTranslateApp).historyRepository.append(
                    HistoryEntry(System.currentTimeMillis(), inFull, outFull),
                )
            }
        } else {
            SessionBus.setStatus(SessionBus.Status.Stopped, message)
        }
        mediaCapturer?.stop()
        mediaCapturer = null
        micCapturer?.stop()
        micCapturer = null
        pcmMixer?.close()
        pcmMixer = null
        liveClient?.close()
        liveClient?.destroy()
        liveClient = null
        audioPlayer?.release()
        audioPlayer = null
        overlay?.hide()
        overlay = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        settingsJob?.cancel()
        eventsJob?.cancel()
        captureStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mediaCapturer?.stop()
        micCapturer?.stop()
        pcmMixer?.close()
        liveClient?.destroy()
        audioPlayer?.release()
        overlay?.hide()
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        scope.cancel()
        ioScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SubtitleSessionService"
        private const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.xzm.realtimetranslate.action.START_SUBTITLE"
        const val ACTION_STOP = "com.xzm.realtimetranslate.action.STOP_SUBTITLE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_AUDIO_SOURCE = "audio_source"

        fun start(
            context: Context,
            audioSource: AudioSourceMode,
            resultCode: Int? = null,
            data: Intent? = null,
        ) {
            val intent = Intent(context, SubtitleSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_AUDIO_SOURCE, audioSource.name)
                if (resultCode != null && data != null) {
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_RESULT_DATA, data)
                }
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SubtitleSessionService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
