package com.xzm.realtimetranslate.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.ocr.ScreenTextCapturer
import com.xzm.realtimetranslate.ocr.ScreenTextOcr
import com.xzm.realtimetranslate.overlay.OcrBallOverlay
import com.xzm.realtimetranslate.overlay.RegionSelectorOverlay
import com.xzm.realtimetranslate.translate.TranslationEngine
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import com.xzm.realtimetranslate.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground OCR session: captures a screen region picked by the user,
 * recognizes text with ML Kit (bundled, offline) and streams the translation
 * to the floating subtitle overlay. Runs independently of — and mutually
 * exclusive with — the audio [SubtitleSessionService].
 */
class ScreenTextSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaProjection: MediaProjection? = null
    private var capturer: ScreenTextCapturer? = null
    private var ocr: ScreenTextOcr? = null
    private var overlay: OcrBallOverlay? = null
    private var settingsJob: Job? = null
    private var commandJob: Job? = null
    private var ocrJob: Job? = null

    @Volatile
    private var currentSettings: UserSettings = UserSettings()

    @Volatile
    private var currentRegion: Rect = Rect()

    /** 帧去重与文本去重状态：点球重选区域时由 [updateRegion] 复位，迫使下一帧重新识别。 */
    @Volatile
    private var lastHash = Long.MIN_VALUE

    @Volatile
    private var lastText = ""

    @Volatile
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerComponentCallbacks(this)
        commandJob = scope.launch {
            ScreenOcrBus.commands.collect { cmd ->
                when (cmd) {
                    ScreenOcrBus.Command.Stop -> stopEverything(getString(R.string.ocr_stopped))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything(getString(R.string.ocr_stopped))
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val region = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_REGION, Rect::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_REGION)
                }
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (region == null || resultCode != Activity.RESULT_OK || data == null) {
                    ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, getString(R.string.ocr_auth_failed))
                    stopSelf()
                    return START_NOT_STICKY
                }
                startSession(region, resultCode, data)
            }
        }
        return START_STICKY
    }

    private fun startSession(region: Rect, resultCode: Int, data: Intent) {
        stopped = false
        currentRegion = region
        ScreenOcrBus.setStatus(ScreenOcrBus.Status.Starting, getString(R.string.ocr_starting))
        startAsForeground()

        val app = application as LiveTranslateApp

        scope.launch {
            currentSettings = app.settingsRepository.settings.first()

            // Engine-aware credential gate: DeepSeek needs a key; Microsoft is keyless.
            if (currentSettings.translationEngine == TranslationEngineType.DEEPSEEK &&
                !app.apiKeyStore.hasDeepSeekKey()
            ) {
                ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, getString(R.string.msg_need_deepseek_key))
                stopSelf()
                return@launch
            }

            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(resultCode, data)
            if (projection == null) {
                ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, getString(R.string.ocr_projection_failed))
                stopSelf()
                return@launch
            }
            // Register the callback BEFORE createVirtualDisplay (Android 14 rule).
            mediaProjection = projection
            projection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopEverything(getString(R.string.ocr_projection_stopped))
                    }
                },
                null,
            )

            // 半球球 + 译文气泡：点球 → 服务内直接弹区域选择器重选，不重启投影/服务。
            val ocrOverlay = OcrBallOverlay(this@ScreenTextSessionService) {
                if (stopped) return@OcrBallOverlay
                RegionSelectorOverlay(
                    this@ScreenTextSessionService,
                    onConfirm = { newRegion -> updateRegion(newRegion) },
                    onCancel = { },
                ).show()
            }
            overlay = ocrOverlay
            ocrOverlay.show(currentSettings)

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = wm.currentWindowMetrics.bounds
            val displayW = bounds.width()
            val displayH = bounds.height()
            val densityDpi = resources.configuration.densityDpi
            if (!regionFits(region, displayW, displayH)) {
                ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, getString(R.string.ocr_region_invalid))
                stopEverything(getString(R.string.ocr_region_invalid))
                return@launch
            }
            val capturer = ScreenTextCapturer(projection, displayW, displayH, densityDpi, null)
            this@ScreenTextSessionService.capturer = capturer
            capturer.start()

            val ocrEngine = ScreenTextOcr()
            ocr = ocrEngine

            settingsJob = scope.launch {
                app.settingsRepository.settings.collectLatest { s ->
                    currentSettings = s
                    overlay?.updateSettings(s)
                }
            }

            ScreenOcrBus.setStatus(ScreenOcrBus.Status.Running, getString(R.string.ocr_running))
            runOcrLoop()
        }
    }

    /**
     * Serial loop: capture → skip unchanged frames → OCR → translate → overlay.
     *
     * The loop runs on the main dispatcher because [translateAndShow] touches the
     * overlay's view hierarchy (main-thread only). The two expensive steps — frame
     * capture (pixel copy) and OCR — are delegated to [Dispatchers.IO] via
     * [withContext]; the translation engines stream on IO via flowOn, so collecting
     * them here never blocks the main thread.
     */
    /** 点球重选区域：只换 capture 区域并复位去重状态，下一帧即按新区域识别。 */
    private fun updateRegion(newRegion: Rect) {
        if (stopped) return
        currentRegion = newRegion
        lastHash = Long.MIN_VALUE
        lastText = ""
        ScreenOcrBus.setStatus(ScreenOcrBus.Status.Running, getString(R.string.ocr_region_updated))
    }

    private fun runOcrLoop() {
        ocrJob?.cancel()
        ocrJob = scope.launch {
            val app = application as LiveTranslateApp
            val engine = TranslationEngineFactory.create(
                settings = currentSettings,
                apiKey = app.apiKeyStore.getDeepSeekKey(),
            )
            while (isActive && !stopped) {
                val frame = withContext(Dispatchers.IO) {
                    try {
                        capturer?.captureRegion(currentRegion)
                    } catch (t: Throwable) {
                        Log.w(TAG, "captureRegion failed", t)
                        null
                    }
                }
                if (frame != null && frame.hash != lastHash) {
                    lastHash = frame.hash
                    val text = withContext(Dispatchers.IO) {
                        ocr?.recognize(frame.bitmap, currentSettings.ocrScript)
                    }
                    if (!text.isNullOrBlank() && text != lastText) {
                        lastText = text
                        translateAndShow(engine, text)
                    }
                }
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    private suspend fun translateAndShow(engine: TranslationEngine, text: String) {
        val overlay = overlay ?: return
        overlay.updateTranscripts(input = text, output = null)
        ScreenOcrBus.setPreview(input = text)
        try {
            engine.translate(
                text = text,
                sourceLang = currentSettings.sourceLanguageCode,
                targetLang = currentSettings.targetLanguageCode,
            ).collect { fragment ->
                if (stopped) return@collect
                overlay.updateTranscripts(input = text, output = fragment)
                ScreenOcrBus.setPreview(input = text, output = fragment)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "translate failed", t)
            if (!stopped) {
                overlay.updateTranscripts(
                    input = text,
                    output = getString(R.string.ocr_translate_failed),
                )
            }
        }
    }

    private fun regionFits(region: Rect, displayW: Int, displayH: Int): Boolean =
        region.left >= 0 && region.top >= 0 &&
            region.right <= displayW && region.bottom <= displayH &&
            region.width() >= MIN_REGION_PX && region.height() >= MIN_REGION_PX

    /** Rotation / display change: resize the virtual display; abort if the region went stale. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (stopped || capturer == null) return
        // 球/气泡随屏幕尺寸变化重新贴边与排布。
        overlay?.reclamp()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        val newW = bounds.width()
        val newH = bounds.height()
        val dpi = resources.configuration.densityDpi
        ioScope.launch {
            capturer?.resize(newW, newH, dpi)
        }
        if (!regionFits(currentRegion, newW, newH)) {
            // stopEverything hides the overlay view hierarchy → main thread only.
            scope.launch {
                stopEverything(getString(R.string.ocr_region_invalid))
            }
        }
    }

    private fun startAsForeground() {
        val stopIntent = Intent(this, ScreenTextSessionService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPi = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, LiveTranslateApp.CHANNEL_SUBTITLE)
            .setContentTitle(getString(R.string.ocr_notification_title))
            .setContentText(getString(R.string.ocr_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.action_stop), stopPi)
            .setOngoing(true)
            .build()

        // Android 14: startForeground(TYPE_MEDIA_PROJECTION) must come BEFORE
        // getMediaProjection(). The type also justifies the special-use permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopEverything(message: String) {
        if (stopped) return
        stopped = true
        ScreenOcrBus.setStatus(ScreenOcrBus.Status.Stopped, message)
        ocrJob?.cancel()
        ocrJob = null
        ocr?.close()
        ocr = null
        capturer?.release()
        capturer = null
        overlay?.hide()
        overlay = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        settingsJob?.cancel()
        settingsJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        unregisterComponentCallbacks(this)
        ocr?.close()
        capturer?.release()
        overlay?.hide()
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        commandJob?.cancel()
        scope.cancel()
        ioScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenTextSessionService"
        private const val NOTIFICATION_ID = 43
        private const val FRAME_INTERVAL_MS = 900L
        private const val MIN_REGION_PX = 48
        const val ACTION_START = "com.xzm.realtimetranslate.action.START_SCREEN_OCR"
        const val ACTION_STOP = "com.xzm.realtimetranslate.action.STOP_SCREEN_OCR"
        const val EXTRA_REGION = "region"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, region: Rect, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenTextSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REGION, region)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenTextSessionService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
