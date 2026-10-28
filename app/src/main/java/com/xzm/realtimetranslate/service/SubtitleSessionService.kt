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
import com.xzm.realtimetranslate.util.AppLog as Log
import androidx.core.app.NotificationCompat
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.audio.AudioLevel
import com.xzm.realtimetranslate.audio.CaptureState
import com.xzm.realtimetranslate.audio.MicAudioCapturer
import com.xzm.realtimetranslate.audio.PcmMixer
import com.xzm.realtimetranslate.audio.SystemAudioCapturer
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
    private var overlay: SubtitleOverlayController? = null
    private var settingsJob: Job? = null
    private var eventsJob: Job? = null
    private var commandJob: Job? = null
    private var hubJob: Job? = null

    private var currentSettings: UserSettings = UserSettings()
    private var audioSourceMode: AudioSourceMode = AudioSourceMode.MEDIA
    private var captureStarted = false

    // 纯 Kotlin 流式双语文本缓冲状态机：接管增量拼接、累计重写、草稿世代隔离、CJK 智能排版与滚动截断
    private val inputBuffer = TextStreamBuffer(maxHistoryChars = 800)
    // 译文按设置的段落大小换行。每次会话启动时按当前设置重建。
    private var outputBuffer = TextStreamBuffer(maxHistoryChars = MAX_OUTPUT_HISTORY_CHARS)

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
        hubJob = scope.launch {
            HubBus.commands.collect { cmd ->
                when (cmd) {
                    is HubCommand.SetSubtitleWindowVisible -> {
                        if (cmd.visible) overlay?.showWindowForHub() else overlay?.hideWindowForHub()
                    }
                    is HubCommand.StopCurrentMode -> {
                        if (HubBus.state.value.mode == HubMode.VOICE) {
                            stopEverything("从悬浮球停止")
                        }
                    }
                    else -> Unit
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
                val rawMode = AudioSourceMode.fromStorage(modeName)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && rawMode.needsMediaProjection) {
                    Log.w(TAG, "AudioPlaybackCapture not supported on Android < 10, fallback to MIC")
                    audioSourceMode = AudioSourceMode.MIC
                } else {
                    audioSourceMode = rawMode
                }
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
        inputBuffer.reset()
        inputBuffer.startNewEpoch()
        captureStarted = false
        startAsForeground()

        val app = application as LiveTranslateApp

        scope.launch {
            currentSettings = app.settingsRepository.settings.first()
            // audioSourceMode already set from Intent EXTRA in onStartCommand.

            // Engine-aware credential gate: engines with requiresApiKey need a key,
            // keyless ones (Microsoft / Google free) pass straight through.
            val engineType = currentSettings.translationEngine
            // 段落大小是构造参数，必须读完成设置后才建；重置旧会话的残留状态也靠这次重建。
            outputBuffer = TextStreamBuffer(
                maxHistoryChars = MAX_OUTPUT_HISTORY_CHARS,
                sentencesPerParagraph = currentSettings.sentencesPerParagraph,
            )
            outputBuffer.startNewEpoch()
            if (!app.apiKeyStore.hasKeyFor(engineType)) {
                val label = getString(engineType.keyLabelRes!!)
                SessionBus.setStatus(
                    SessionBus.Status.Error,
                    getString(R.string.msg_need_api_key, label),
                )
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

            eventsJob = scope.launch {
                launch {
                    client.connectionState.collect { state ->
                        when (state) {
                            is RealtimeTranslationClient.ConnectionState.Ready -> {
                                SessionBus.setStatus(SessionBus.Status.Running, getString(R.string.status_translating))
                                HubBus.syncFromSession(SessionBus.Status.Running)
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
                            SessionBus.setStatus(SessionBus.Status.Running, getString(R.string.status_translating))
                            HubBus.syncFromSession(SessionBus.Status.Running)
                            startCapturePipeline(client)
                        }
                        is RealtimeTranslationClient.LiveEvent.InputTranscript -> {
                            inputBuffer.appendFinal(inputBuffer.currentEpoch, event.text)
                            inputBuffer.startNewEpoch()
                            val text = inputBuffer.getDisplayText()
                            overlay?.updateTranscripts(input = text, output = null)
                            SessionBus.setPreview(input = text)
                        }
                        // 草稿原文：只接在累积原文后面显示，不进任何持久缓冲。
                        // 下一句的草稿会整体替换它，定稿事件则把它清掉。
                        is RealtimeTranslationClient.LiveEvent.InputPartial -> {
                            inputBuffer.updateDraft(inputBuffer.currentEpoch, event.text)
                            val text = inputBuffer.getDisplayText()
                            overlay?.updateTranscripts(input = text, output = null)
                            SessionBus.setPreview(input = text)
                        }
                        is RealtimeTranslationClient.LiveEvent.OutputTranscript -> {
                            outputBuffer.appendFinal(outputBuffer.currentEpoch, event.text)
                            val text = outputBuffer.getDisplayText()
                            overlay?.updateTranscripts(input = null, output = text)
                            SessionBus.setPreview(output = text)
                        }
                        // 草稿译文：排在已定格的历史和当前句之后。
                        // 世代校验已在 RealtimeTranslationClient.draftWorker 内完成
                        // （req.epoch != draftEpoch.get() 即丢弃），到这里必然是当前世代的合法草稿；
                        // 这里若再用另一套计数器比较，两边起点不同会永远不相等、把草稿全丢掉。
                        is RealtimeTranslationClient.LiveEvent.OutputPartial -> {
                            outputBuffer.updateDraft(outputBuffer.currentEpoch, event.text)
                            val text = outputBuffer.getDisplayText()
                            overlay?.updateTranscripts(input = null, output = text)
                            SessionBus.setPreview(output = text)
                        }
                        // 新句开始：开启新世代，当前句定格进滚动历史（不清屏）。流式重写只改当前行、
                        // 不会逐段追加成上一句的重复堆叠。导出文本仍保持完整累积。
                        is RealtimeTranslationClient.LiveEvent.OutputReset -> {
                            outputBuffer.startNewEpoch()
                        }
                        // 译音播放已移除，音频块事件直接忽略。
                        is RealtimeTranslationClient.LiveEvent.AudioChunk -> Unit
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
                    currentSettings = s
                    overlay?.updateSettings(s)
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
        SessionBus.setAudio(0f, CaptureState.STARTING)

        val silenceCallback: (Long, String) -> Unit = { _, src ->
            if (currentSettings.silenceDetectionEnabled) {
                val hint = if (src.contains("Media")) {
                    getString(R.string.audio_hint_media_silent)
                } else {
                    getString(R.string.audio_hint_mic_silent)
                }
                SessionBus.setStatus(SessionBus.Status.Running, "$hint")
            }
        }
        // 声音回来就把状态改回去 —— 否则「未检测到声音」会永远挂在那里。
        val restoredCallback: (String) -> Unit = {
            SessionBus.setStatus(SessionBus.Status.Running, getString(R.string.status_translating))
        }
        val levelCallback: (CaptureState, Double) -> Unit = { state, rms ->
            SessionBus.noteRms(rms)
            SessionBus.setAudio(AudioLevel.normalize(rms), state)
            if (state == CaptureState.STALLED && currentSettings.silenceDetectionEnabled) {
                SessionBus.setStatus(
                    SessionBus.Status.Running,
                    getString(R.string.audio_hint_stalled),
                )
            }
        }
        try {
            when (audioSourceMode) {
                AudioSourceMode.MEDIA -> {
                    val projection = mediaProjection
                        ?: throw IllegalStateException("缺少 MediaProjection")
                    val cap = SystemAudioCapturer(
                        ioScope,
                        onSilenceAlert = silenceCallback,
                        onSoundRestored = restoredCallback,
                        onLevel = levelCallback,
                    )
                    mediaCapturer = cap
                    cap.start(projection) { pcm -> client.sendPcm16le(pcm, 16_000) }
                    SessionBus.projectionActivated = cap.projectionActivated
                }
                AudioSourceMode.MIC -> {
                    val mic = MicAudioCapturer(
                        ioScope,
                        onSilenceAlert = silenceCallback,
                        onSoundRestored = restoredCallback,
                        onLevel = levelCallback,
                    )
                    micCapturer = mic
                    mic.start { pcm -> client.sendPcm16le(pcm, 16_000) }
                }
                AudioSourceMode.MEDIA_AND_MIC -> {
                    val projection = mediaProjection
                        ?: throw IllegalStateException("缺少 MediaProjection")
                    val mixer = PcmMixer { mixed -> client.sendPcm16le(mixed, 16_000) }
                    pcmMixer = mixer
                    val media = SystemAudioCapturer(
                        ioScope,
                        onSilenceAlert = silenceCallback,
                        onSoundRestored = restoredCallback,
                        onLevel = levelCallback,
                    )
                    mediaCapturer = media
                    media.start(projection) { pcm -> mixer.offerMedia(pcm) }
                    SessionBus.projectionActivated = media.projectionActivated
                    val mic = MicAudioCapturer(
                        ioScope,
                        onSilenceAlert = silenceCallback,
                        onSoundRestored = restoredCallback,
                        onLevel = levelCallback,
                    )
                    micCapturer = mic
                    mic.start { pcm -> mixer.offerMic(pcm) }
                }
            }
            SessionBus.setStatus(SessionBus.Status.Running, getString(R.string.status_waiting_sound))
        } catch (e: Exception) {
            Log.e(TAG, "capture start failed", e)
            SessionBus.setStatus(SessionBus.Status.Error, e.message ?: "音频采集启动失败")
            stopEverything(e.message ?: "音频采集启动失败")
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
        val inFull = inputBuffer.getFullExportText()
        val outFull = outputBuffer.getFullExportText()
        if (inFull.isNotBlank() || outFull.isNotBlank()) {
            SessionBus.markSessionFinished(inFull, outFull, message)
            // Fire-and-forget via the repository's process-scoped IO scope:
            // this service's ioScope is cancelled in onDestroy right after.
            // SAVE_ALL keeps everything (no cap); AUTO_CLEAR trims to historyLimit.
            (application as LiveTranslateApp).historyRepository.append(
                HistoryEntry(System.currentTimeMillis(), inFull, outFull),
                maxEntries = if (currentSettings.historyMode == HistoryMode.SAVE_ALL) null
                else currentSettings.historyLimit,
            )
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
        overlay?.hide()
        overlay = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        settingsJob?.cancel()
        eventsJob?.cancel()
        hubJob?.cancel()
        hubJob = null
        HubBus.syncFromSession(SessionBus.Status.Stopped)
        captureStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mediaCapturer?.stop()
        micCapturer?.stop()
        pcmMixer?.close()
        liveClient?.destroy()
        overlay?.hide()
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        hubJob?.cancel()
        hubJob = null
        HubBus.syncFromSession(SessionBus.Status.Stopped)
        scope.cancel()
        ioScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SubtitleSessionService"
        private const val NOTIFICATION_ID = 42
        private const val MAX_OUTPUT_HISTORY_CHARS = 2000
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
