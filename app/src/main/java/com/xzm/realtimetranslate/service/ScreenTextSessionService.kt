package com.xzm.realtimetranslate.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.xzm.realtimetranslate.util.AppLog
import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.util.RegionGestureMath
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.HistoryEntry
import com.xzm.realtimetranslate.data.HistoryMode
import com.xzm.realtimetranslate.data.ScreenOcrMode
import com.xzm.realtimetranslate.ocr.PixelRect
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.ocr.BackgroundColorSampler
import com.xzm.realtimetranslate.ocr.ScreenTextCapturer
import com.xzm.realtimetranslate.ocr.ScreenTextOcr
import com.xzm.realtimetranslate.ocr.TextTarget
import com.xzm.realtimetranslate.ocr.TranslationPlanner
import com.xzm.realtimetranslate.overlay.CoverBlockManager
import com.xzm.realtimetranslate.overlay.CoverOverlayController
import com.xzm.realtimetranslate.overlay.CoverStyle
import com.xzm.realtimetranslate.overlay.CoverTarget
import com.xzm.realtimetranslate.overlay.OcrBallOverlay
import com.xzm.realtimetranslate.overlay.RegionSelectorOverlay
import com.xzm.realtimetranslate.translate.TranslationCache
import com.xzm.realtimetranslate.translate.TranslationEngine
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import com.xzm.realtimetranslate.ui.main.MainActivity
import com.xzm.realtimetranslate.util.clampRegion
import com.xzm.realtimetranslate.util.realDisplayRotation
import com.xzm.realtimetranslate.util.realScreenMetrics
import com.xzm.realtimetranslate.util.remapRegion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Foreground OCR session: captures a screen region picked by the user,
 * recognizes text with ML Kit (bundled, offline) and streams the translation
 * to the floating subtitle overlay. Runs independently of — and mutually
 * exclusive with — the audio [SubtitleSessionService].
 */
class ScreenTextSessionService : Service() {

    // SupervisorJob only isolates sibling coroutines — it does NOT stop an
    // uncaught exception in a root coroutine from reaching the thread's default
    // handler and killing the process. Hence the explicit handlers.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, t -> Log.e(TAG, "scope uncaught", t) },
    )
    private val ioScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, t -> Log.e(TAG, "ioScope uncaught", t) },
    )

    private var mediaProjection: MediaProjection? = null
    private var capturer: ScreenTextCapturer? = null
    private var ocr: ScreenTextOcr? = null
    private var overlay: OcrBallOverlay? = null
    private var coverOverlay: CoverOverlayController? = null
    private var settingsJob: Job? = null
    private var commandJob: Job? = null
    private var ocrJob: Job? = null

    /** Region bubble vs whole-screen cover. Fixed for the lifetime of a session. */
    @Volatile
    private var mode: ScreenOcrMode = ScreenOcrMode.REGION

    private val backgroundSampler = BackgroundColorSampler()

    /** Session-scoped: re-recognizing the same screen must not re-bill the engine. */
    private val translationCache = TranslationCache()

    /** Tracks and merges active cover blocks across frames. */
    private val coverBlockManager = CoverBlockManager()

    private var lastOutsideSamples: IntArray? = null
    private val sampleGridBuffer = IntArray(SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE)

    /** Bumped for every cover frame; a worker whose epoch is stale drops its results. */
    @Volatile
    private var coverEpoch = 0

    @Volatile
    private var currentSettings: UserSettings = UserSettings()

    @Volatile
    private var currentRegion: Rect = Rect()

    /** 坐标系 [currentRegion] 是在哪里被画出来的（尺寸 + 旋转）。旋转发生时靠它算出
     *  显示帧转过的角度，把选区映射到新坐标系；只在主线程读写。 */
    @Volatile
    private var regionSpaceW = 0

    @Volatile
    private var regionSpaceH = 0

    @Volatile
    private var regionSpaceRot = Surface.ROTATION_0

    /** 旋转/折叠后投影帧会乱几百 ms，这期间不采集，免得用旧坐标裁新尺寸的帧、
     *  冒出一条莫名其妙的译文气泡。 */
    @Volatile
    private var capturePausedUntil = 0L

    /** 帧去重与文本去重状态：点球重选区域时由 [updateRegion] 复位，迫使下一帧重新识别。 */
    @Volatile
    private var lastHash = Long.MIN_VALUE

    @Volatile
    private var lastText = ""

    /** 暂停 = 停止采集 / OCR / 翻译，但保留录屏授权、悬浮球与选区，随时可继续。
     *  与 [stopped] 的区别：暂停不释放任何资源，也不需要用户重新授权。 */
    @Volatile
    private var paused = false

    @Volatile
    private var stopped = false

    private val ocrRecords = mutableListOf<Pair<String, String>>()

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
            ACTION_TOGGLE_PAUSE -> {
                setPaused(!paused)
                return START_STICKY
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
                val requestedMode = intent.getStringExtra(EXTRA_MODE)
                    ?.let { name -> runCatching { ScreenOcrMode.valueOf(name) }.getOrNull() }
                    ?: ScreenOcrMode.REGION
                startSession(region, resultCode, data, requestedMode)
            }
        }
        return START_STICKY
    }

    private fun startSession(region: Rect, resultCode: Int, data: Intent, requestedMode: ScreenOcrMode) {
        stopped = false
        paused = false
        mode = requestedMode
        currentRegion = region
        ScreenOcrBus.setStatus(ScreenOcrBus.Status.Starting, getString(R.string.ocr_starting))
        startAsForeground()

        val app = application as LiveTranslateApp

        scope.launch {
            try {
                startSessionInternal(region, resultCode, data, app)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                // createVirtualDisplay / windowManager.addView / OCR init can all
                // throw; unguarded they would take the whole process down.
                Log.e(TAG, "startSession failed", t)
                val msg = getString(R.string.msg_service_start_failed, t.message.orEmpty())
                ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, msg)
                stopEverything(msg)
            }
        }
    }

    /** Body of [startSession], run on [scope] so a throw is reported and cleaned
     *  up instead of crashing the process. */
    private suspend fun startSessionInternal(
        region: Rect,
        resultCode: Int,
        data: Intent,
        app: LiveTranslateApp,
    ) {
        synchronized(ocrRecords) { ocrRecords.clear() }
        currentSettings = app.settingsRepository.settings.first()

        // Engine-aware credential gate: engines with requiresApiKey need a key,
        // keyless ones (Microsoft / Google free) pass straight through.
        val engineType = currentSettings.translationEngine
        if (!app.apiKeyStore.hasKeyFor(engineType)) {
            val label = getString(engineType.keyLabelRes!!)
            ScreenOcrBus.setStatus(
                ScreenOcrBus.Status.Error,
                getString(R.string.msg_need_api_key, label),
            )
            stopSelf()
            return
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, data)
        if (projection == null) {
            ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, getString(R.string.ocr_projection_failed))
            stopSelf()
            return
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

        // 半球球 + 译文气泡。点球 = 暂停/继续；长按 = 菜单（暂停、重选区域、取词框、停止）。
        // 全部在服务内处理，不重启投影、不需要重新授权。
        // 全屏覆盖模式没有球也没有气泡，输出全在覆盖层上。
        val fullscreen = mode == ScreenOcrMode.FULLSCREEN
        if (!fullscreen) {
            val ocrOverlay = OcrBallOverlay(
                context = this@ScreenTextSessionService,
                onTogglePause = { setPaused(!paused) },
                onReselectRegion = {
                    if (!stopped) {
                        RegionSelectorOverlay(
                            this@ScreenTextSessionService,
                            onConfirm = { newRegion -> updateRegion(newRegion) },
                            onCancel = { },
                        ).show()
                    }
                },
                onStop = { stopEverything(getString(R.string.ocr_stopped)) },
                onRegionMoved = { newRegion -> updateRegion(newRegion) },
            )
            overlay = ocrOverlay
            ocrOverlay.show(currentSettings)
            // 在球旁用一条淡色细框标出当前 OCR 选区，让用户知道在翻译屏幕哪块。
            ocrOverlay.showRegionOutline(region)
        }

        // The virtual display must match the real (full) display size so the
        // region Rect — which lives in full-screen coordinates — maps 1:1.
        val (displayW, displayH, _) = realScreenMetrics()
        val densityDpi = resources.configuration.densityDpi
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val windowBounds =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.currentWindowMetrics.bounds else null
        val rotation = realDisplayRotation()
        Log.i(TAG, "OCRMAP realScreen=${displayW}x$displayH dpi=$densityDpi rot=$rotation " +
            "windowBounds=$windowBounds region=$region mode=$mode")
        if (displayW < MIN_REGION_PX || displayH < MIN_REGION_PX) {
            ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, getString(R.string.ocr_region_invalid))
            stopEverything(getString(R.string.ocr_region_invalid))
            return
        }
        // The region was drawn in the coordinate space of *that* moment; if the
        // display rotated while the projection dialog was up, those numbers are
        // stale. Squeeze them back in range and carry on — never end the session
        // over an out-of-range region.
        val usableRegion = if (fullscreen) {
            Rect(0, 0, displayW, displayH)
        } else {
            clampRegion(region, displayW, displayH, MIN_REGION_PX)
        }
        if (!fullscreen && usableRegion != region) Log.i(TAG, "OCRMAP clamped $region -> $usableRegion")
        currentRegion = usableRegion
        regionSpaceW = displayW
        regionSpaceH = displayH
        regionSpaceRot = rotation
        overlay?.showRegionOutline(usableRegion)
        val capturer = ScreenTextCapturer(projection, displayW, displayH, densityDpi, null)
        this@ScreenTextSessionService.capturer = capturer
        capturer.start()

        val ocrEngine = ScreenTextOcr()
        ocr = ocrEngine

        if (fullscreen) {
            val cover = CoverOverlayController(this@ScreenTextSessionService)
            cover.show(coverStyleOf(currentSettings))
            coverOverlay = cover
        }

        settingsJob = scope.launch {
            var lastCoverStyle = coverStyleOf(currentSettings)
            app.settingsRepository.settings.collectLatest { s ->
                currentSettings = s
                overlay?.updateSettings(s)
                // Rebuilding the window is the cheapest way to restyle: the next
                // frame re-plans from scratch and every block comes back from the
                // translation cache, so this costs no network requests.
                val style = coverStyleOf(s)
                if (coverOverlay != null && style != lastCoverStyle) {
                    lastCoverStyle = style
                    coverEpoch++
                    coverBlockManager.clear()
                    coverOverlay?.show(style)
                }
            }
        }

        ScreenOcrBus.setStatus(
            ScreenOcrBus.Status.Running,
            getString(if (fullscreen) R.string.cover_notification_text else R.string.ocr_running),
        )
        runOcrLoop()
    }

    /**
     * Flattens the rects painted last frame so the recognizer sees blank there.
     *
     * Only regions the cover actually painted are erased, and each one is filled
     * with the colour sampled around it, so the rest of the screen — including
     * newly scrolled-in content — is untouched. Must run on the capture bitmap
     * before OCR; it mutates the frame in place.
     */
    private fun erasePaintedRegions(bitmap: Bitmap) {
        val rects = coverBlockManager.getPaintedRects()
        if (rects.isEmpty() || bitmap.isRecycled) return
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.FILL }
        // Pad by ~4-8px so CoverView's 2dp rounded rectangle padding is fully covered
        val pad = (4 * (bitmap.density / 160f)).toInt().coerceAtLeast(4)
        for (r in rects) {
            val padded = r.padded(pad)
            val left = padded.left.coerceIn(0, bitmap.width)
            val top = padded.top.coerceIn(0, bitmap.height)
            val right = padded.right.coerceIn(left, bitmap.width)
            val bottom = padded.bottom.coerceIn(top, bitmap.height)
            if (right <= left || bottom <= top) continue
            paint.color = backgroundSampler.sample(bitmap, r)
            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        }
    }

    /**
     * 计算覆盖色块外部区域的像素变化比例（0.0f..1.0f）。
     * 仅采样落在 [paintedRects] 外部的点，避开本应用绘制的覆盖色块造成的干扰。
     */
    private fun computeOutsideDiffRatio(bitmap: Bitmap, paintedRects: List<PixelRect>): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0f
        val stepX = maxOf(1, w / SAMPLE_GRID_SIZE)
        val stepY = maxOf(1, h / SAMPLE_GRID_SIZE)

        var idx = 0
        var y = stepY / 2
        while (y < h && idx < sampleGridBuffer.size) {
            var x = stepX / 2
            while (x < w && idx < sampleGridBuffer.size) {
                val isInside = paintedRects.any { r ->
                    x >= r.left && x < r.right && y >= r.top && y < r.bottom
                }
                sampleGridBuffer[idx++] = if (isInside) Int.MIN_VALUE else bitmap.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }

        val previous = lastOutsideSamples
        if (previous == null || previous.size != idx) {
            lastOutsideSamples = sampleGridBuffer.copyOf(idx)
            return 0f
        }

        var validPoints = 0
        var changedPoints = 0
        for (i in 0 until idx) {
            val curr = sampleGridBuffer[i]
            val prev = previous[i]
            if (curr != Int.MIN_VALUE && prev != Int.MIN_VALUE) {
                validPoints++
                if (curr != prev) {
                    changedPoints++
                }
            }
        }

        lastOutsideSamples = sampleGridBuffer.copyOf(idx)
        return if (validPoints > 0) changedPoints.toFloat() / validPoints else 0f
    }

    private fun coverStyleOf(settings: UserSettings): CoverStyle = CoverStyle(
        bilingual = settings.coverBilingual,
        fontScale = settings.coverFontScale,
        blockStyle = settings.coverBlockStyle,
    )

    /**
     * Capture loop: capture → skip unchanged frames → OCR → hand off to the
     * translate worker. The loop body never awaits a translation, so the capture
     * cadence stays at [FRAME_INTERVAL_MS] no matter how slow the engine is.
     *
     * The loop and the worker both run on the main dispatcher because the overlay
     * view hierarchy is main-thread only. The two expensive steps — frame capture
     * (pixel copy) and OCR — are delegated to [Dispatchers.IO] via [withContext];
     * the translation engines stream on IO via flowOn, so collecting them never
     * blocks the main thread.
     */
    /** 点球重选区域：只换 capture 区域并复位去重状态，下一帧即按新区域识别。 */
    private fun updateRegion(newRegion: Rect) {
        if (stopped) return
        val (w, h, _) = realScreenMetrics()
        if (w < MIN_REGION_PX || h < MIN_REGION_PX) return
        // 正常情况下是恒等变换（选择器就画在当前坐标系）；这里保留是为了维持
        // 「currentRegion 永远在当前显示范围内」这条不变式 —— 选择器自己的
        // 屏幕尺寸可能已经过期。
        currentRegion = clampRegion(newRegion, w, h, MIN_REGION_PX)
        regionSpaceW = w
        regionSpaceH = h
        regionSpaceRot = realDisplayRotation()
        lastHash = Long.MIN_VALUE
        lastText = ""
        overlay?.showRegionOutline(currentRegion)
        ScreenOcrBus.setStatus(
            if (paused) ScreenOcrBus.Status.Paused else ScreenOcrBus.Status.Running,
            getString(R.string.ocr_region_updated),
        )
    }

    /** 暂停 / 继续：只停采集、OCR 与翻译，保留录屏授权、悬浮球、选区和已显示的
     *  译文。恢复时清掉去重状态，下一帧立即按当前画面重新识别。 */
    private fun setPaused(value: Boolean) {
        if (stopped || (overlay == null && coverOverlay == null) || paused == value) return
        paused = value
        if (value) {
            ScreenOcrBus.setStatus(ScreenOcrBus.Status.Paused, getString(R.string.ocr_paused))
        } else {
            lastHash = Long.MIN_VALUE
            lastText = ""
            ScreenOcrBus.setStatus(ScreenOcrBus.Status.Running, getString(R.string.ocr_resumed))
        }
        overlay?.setPaused(value)
        updateNotification()
        Toast.makeText(
            this,
            if (value) R.string.ocr_paused_toast else R.string.ocr_resumed_toast,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun runOcrLoop() {
        ocrJob?.cancel()
        ocrJob = scope.launch {
            val app = application as LiveTranslateApp
            // liveSettings: 引擎每次请求重读设置，模型名 / baseUrl / 思考开关改动
            // 下一句即生效（重选区域不会重建引擎，靠这里生效）。
            val engine = TranslationEngineFactory.create(
                settings = currentSettings,
                keys = app.apiKeyStore,
                liveSettings = { app.settingsRepository.settings.first() },
            )
            // 翻译 worker：单槽（conflated）Channel = 同时最多一个翻译在飞，且翻译
            // 期间画面又换了只保留最新的那份文本。引擎再慢也冻不住上面的采集节拍，
            // 而气泡永远朝最新字幕收敛，不会卡在上一句上不动。
            // 它是 ocrJob 的子协程，换区域/停会话时随采集循环一起结束。
            val pendingText = Channel<String>(Channel.CONFLATED)
            launch {
                for (text in pendingText) {
                    if (stopped) break
                    if (paused) continue // 暂停期间不花翻译额度
                    val tTranslate = SystemClock.elapsedRealtime()
                    translateAndShow(engine, text)
                    // TRANSPROF 诊断（只读埋点）：这次翻译实际花了多久。
                    // 对照上面 ocrloop 的 cycle= 即可看出解耦是否生效。
                    Log.i(
                        TAG,
                        "TRANSPROF translateLatest chars=${text.length} " +
                            "ms=${SystemClock.elapsedRealtime() - tTranslate}",
                    )
                }
            }

            // 全屏覆盖模式的帧队列：同样 conflated —— 翻译慢时只保留最新一帧，
            // 屏幕不会在几秒前的画面上追帧。
            val coverFrames = Channel<CoverFrame>(Channel.CONFLATED)
            launch {
                for (frame in coverFrames) {
                    if (stopped) break
                    if (paused) continue
                    translateCoverFrame(engine, frame)
                }
            }

            // TRANSPROF 诊断（只读埋点）：空转轮次累积计数，每 10 轮汇报一次，
            // 用来判断「是不是每帧都在重发请求」。
            var idleRounds = 0
            val frameInterval = if (mode == ScreenOcrMode.FULLSCREEN) COVER_FRAME_INTERVAL_MS else FRAME_INTERVAL_MS
            while (isActive && !stopped) {
                if (paused) {
                    // 暂停：不做截图、不跑 OCR、不发请求，但仍保持循环可被恢复。
                    delay(frameInterval)
                    continue
                }
                if (SystemClock.elapsedRealtime() < capturePausedUntil) {
                    // 旋转/折叠刚发生：投影帧还在重建，此刻裁出来的是乱帧。
                    delay(DISPLAY_PAUSE_POLL_MS)
                    continue
                }
                val cycleStart = SystemClock.elapsedRealtime()
                val tCapture = SystemClock.elapsedRealtime()
                val frame = withContext(Dispatchers.IO) {
                    try {
                        capturer?.captureRegion(currentRegion)
                    } catch (t: Throwable) {
                        Log.w(TAG, "captureRegion failed", t)
                        null
                    }
                }
                val captureMs = SystemClock.elapsedRealtime() - tCapture
                if (frame != null && frame.hash != lastHash) {
                    lastHash = frame.hash
                    if (mode == ScreenOcrMode.FULLSCREEN) {
                        val painted = coverBlockManager.getPaintedRects()
                        if (painted.isNotEmpty()) {
                            val diff = computeOutsideDiffRatio(frame.bitmap, painted)
                            if (diff > SCREEN_CHANGE_DIFF_THRESHOLD) {
                                Log.i(
                                    TAG,
                                    "TRANSPROF screen changed diff=$diff > $SCREEN_CHANGE_DIFF_THRESHOLD, clearing previous translations",
                                )
                                coverBlockManager.clear()
                                coverOverlay?.update(emptyList())
                                lastOutsideSamples = null
                            } else if (diff in 0.01f..SCREEN_CHANGE_DIFF_THRESHOLD) {
                                val now = SystemClock.elapsedRealtime()
                                val pruned = coverBlockManager.pruneStale(SUBTITLE_STALE_TTL_MS, now)
                                if (pruned > 0) {
                                    coverOverlay?.update(coverBlockManager.getActiveTargets())
                                }
                            }
                        } else {
                            lastOutsideSamples = null
                        }
                        handleCoverFrame(frame.bitmap)?.let { coverFrames.trySend(it) }
                        Log.i(
                            TAG,
                            "TRANSPROF coverloop capture=$captureMs " +
                                "cycle=${SystemClock.elapsedRealtime() - cycleStart}",
                        )
                        idleRounds = 0
                    } else {
                        val tOcr = SystemClock.elapsedRealtime()
                        val text = withContext(Dispatchers.IO) {
                            ocr?.recognize(frame.bitmap, currentSettings.ocrScript)
                        }
                        val ocrMs = SystemClock.elapsedRealtime() - tOcr
                        if (!text.isNullOrBlank() && text != lastText) {
                            lastText = text
                            pendingText.trySend(text)
                            Log.i(
                                TAG,
                                "TRANSPROF ocrloop capture=$captureMs ocr=$ocrMs " +
                                    "ocrChars=${text.length} textChanged=true " +
                                    "cycle=${SystemClock.elapsedRealtime() - cycleStart}",
                            )
                            idleRounds = 0
                        } else {
                            idleRounds++
                            if (idleRounds % IDLE_LOG_ROUNDS == 0) {
                                Log.i(
                                    TAG,
                                    "TRANSPROF ocrloopidle rounds=$idleRounds hashChanged=true textChanged=false",
                                )
                            }
                        }
                    }
                } else {
                    idleRounds++
                    if (idleRounds % IDLE_LOG_ROUNDS == 0) {
                        Log.i(
                            TAG,
                            "TRANSPROF ocrloopidle rounds=$idleRounds hashChanged=false",
                        )
                    }
                }
                delay(frameInterval)
            }
            // 采集循环正常退出（stopped）：关掉 Channel 让 worker 一起收尾，
            // 否则父协程会一直等这个挂在 receive 上的子协程。
            pendingText.close()
            coverFrames.close()
        }
    }

    /** One full-screen frame's translation units plus the color sampled behind each. */
    private class CoverFrame(val epoch: Int, val requests: List<CoverRequest>)

    private class CoverRequest(val target: TextTarget, val background: Int)

    /**
     * OCR + background sampling for one cover frame.
     *
     * Must finish before returning to the capture loop: [ScreenTextCapturer] reuses
     * a single bitmap for every frame, so nothing here may hand `bitmap` to a
     * background coroutine — it would be overwritten by the next capture mid-read.
     */
    private suspend fun handleCoverFrame(bitmap: Bitmap): CoverFrame? {
        // Erase what we painted last frame before recognizing. Without FLAG_SECURE
        // (which blacks out MediaProjection on this ROM) the cover layer is part of
        // the capture, so OCR would read our own translations as fresh source text
        // and translate the same screen forever. Painting those rects flat leaves
        // the original content beneath them out of the OCR input, which is exactly
        // what we want: they are already translated and already on screen.
        erasePaintedRegions(bitmap)
        val page = withContext(Dispatchers.IO) {
            try {
                ocr?.recognizePage(bitmap, currentSettings.ocrScript)
            } catch (t: Throwable) {
                Log.w(TAG, "cover ocr failed", t)
                null
            }
        }
        if (page == null) {
            Log.i(TAG, "TRANSPROF coverplan NULL (no text found on frame)")
            return null
        }
        val targets = TranslationPlanner.plan(page, currentSettings.targetLanguageCode)
        Log.i(
            TAG,
            "TRANSPROF coverplan blocks=${page.blocks.size} targets=${targets.size} " +
                "target=${currentSettings.targetLanguageCode}",
        )
        if (targets.isEmpty()) {
            // All on-screen text is already covered or in target language.
            // Do NOT wipe the existing overlay: those blocks are already on screen.
            return null
        }
        val requests = withContext(Dispatchers.IO) {
            targets.map { CoverRequest(it, backgroundSampler.sample(bitmap, it.rect)) }
        }
        // Epoch must NOT advance per frame: a translation takes 500–1400 ms while
        // frames arrive every 400 ms, so any per-frame bump makes every in-flight
        // result look stale and it is silently dropped — the cover never paints.
        // Frame-level staleness is already handled by the CONFLATED frame channel.
        // [coverEpoch] only moves on mode switch / region change / rotation.
        return CoverFrame(coverEpoch, requests)
    }

    /** Translates one frame's blocks, at most [TRANSLATE_CONCURRENCY] in flight. */
    private suspend fun translateCoverFrame(engine: TranslationEngine, frame: CoverFrame) {
        val cover = coverOverlay ?: return
        val semaphore = Semaphore(TRANSLATE_CONCURRENCY)
        coroutineScope {
            frame.requests.forEachIndexed { index, request ->
                launch(Dispatchers.Default) {
                    val translated = semaphore.withPermit { translateTarget(engine, request.target) }
                    // A failure leaves the original text uncovered — never paint
                    // "翻译失败" over someone's screen.
                    if (translated.isNullOrBlank()) return@launch
                    if (frame.epoch != coverEpoch) return@launch
                    val target = CoverTarget(
                        rect = request.target.rect.offset(currentRegion.left, currentRegion.top),
                        text = translated,
                        background = request.background,
                        original = request.target.text,
                        createdAtMs = SystemClock.elapsedRealtime(),
                    )
                    coverBlockManager.updateTarget(target, request.target.key)
                    val (w, h, _) = realScreenMetrics()
                    coverBlockManager.pruneOffscreen(w, h)
                    val snapshot = coverBlockManager.getActiveTargets()
                    Log.i(
                        TAG,
                        "TRANSPROF coverpaint epoch=${frame.epoch} cur=$coverEpoch " +
                            "idx=$index n=${snapshot.size} rect=${target.rect} text=${target.text.take(12)}",
                    )
                    cover.update(snapshot)
                }
            }
        }
    }

    /** Cached per language pair + text, so a static screen costs nothing after the first pass. */
    private suspend fun translateTarget(engine: TranslationEngine, target: TextTarget): String? {
        val source = currentSettings.sourceLanguageCode
        val dest = currentSettings.targetLanguageCode
        val key = "$source>$dest|${target.key}"
        translationCache.get(key)?.let { return it }
        return try {
            val out = engine.translate(text = target.text, sourceLang = source, targetLang = dest).last()
            if (out.isBlank()) {
                null
            } else {
                translationCache.put(key, out)
                out
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "cover translate failed chars=${target.text.length}", t)
            null
        }
    }

    private suspend fun translateAndShow(engine: TranslationEngine, text: String) {
        val overlay = overlay ?: return
        overlay.updateTranscripts(input = text, output = null)
        ScreenOcrBus.setPreview(input = text)
        var outText = ""
        try {
            engine.translate(
                text = text,
                sourceLang = currentSettings.sourceLanguageCode,
                targetLang = currentSettings.targetLanguageCode,
            ).collect { fragment ->
                if (stopped) return@collect
                outText = fragment
                overlay.updateTranscripts(input = text, output = fragment)
                ScreenOcrBus.setPreview(input = text, output = fragment)
            }
            // OCR's answer to "the translation is wrong": what was recognized off the
            // screen next to what came back. Opt-in, and only into the content log.
            if (AppLog.contentEnabled && outText.isNotBlank()) {
                AppLog.content(TAG, "ocr ${text.take(500)} → ${outText.take(500)}")
            }
            if (outText.isNotBlank()) {
                synchronized(ocrRecords) {
                    if (ocrRecords.isEmpty() || ocrRecords.last().first != text || ocrRecords.last().second != outText) {
                        ocrRecords.add(text to outText)
                    }
                }
            }
        } catch (t: CancellationException) {
            // 停会话/换区域导致的取消。必须原样上抛：否则会被下面那个
            // catch (Throwable) 当成翻译失败，把「翻译失败」画到气泡上。
            throw t
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

    /** Rotation / display change: remap the region and (only if the frame size
     *  really changed) rebuild the capture pipeline. **Never ends the session.** */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (stopped || capturer == null) return
        // 球/气泡随屏幕尺寸变化重新贴边与排布。
        overlay?.reclamp()
        capturePausedUntil = SystemClock.elapsedRealtime() + DISPLAY_PAUSE_MS
        applyDisplayChange()
    }

    private fun applyDisplayChange() {
        if (stopped) return
        val capturer = this.capturer ?: return
        val (newW, newH, _) = realScreenMetrics()
        val newRot = realDisplayRotation()
        if (newW < MIN_REGION_PX || newH < MIN_REGION_PX) {
            // 现实设备上不可达（最小屏也远大于 48px），保留最后一条真正的失败路径。
            ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, getString(R.string.ocr_region_invalid))
            stopEverything(getString(R.string.ocr_region_invalid))
            return
        }
        val geometryChanged = newRot != regionSpaceRot || newW != regionSpaceW || newH != regionSpaceH

        // 1) 选区先映射进新坐标系，再无条件 clamp —— remapRegion 保证结果一定在界内。
        val region = remapRegion(
            region = currentRegion,
            oldW = regionSpaceW,
            oldH = regionSpaceH,
            oldRot = regionSpaceRot,
            newW = newW,
            newH = newH,
            newRot = newRot,
            minPx = MIN_REGION_PX,
        )
        regionSpaceW = newW
        regionSpaceH = newH
        regionSpaceRot = newRot

        // 2) 只有帧尺寸真变了才重建管线。守卫读 capturer 的活值（与 ImageReader
        //    同一步更新），不是本类另存的副本 —— 副本在 resize 失败后会脱节。
        //    系统也会为 AUTO_MIRROR 虚拟屏自动跟随旋转，所以这里可能什么都不用做。
        if (newW != capturer.width || newH != capturer.height) {
            val dpi = resources.configuration.densityDpi
            ioScope.launch {
                val ok = runCatching { capturer.resize(newW, newH, dpi) }
                    .onFailure { Log.w(TAG, "capturer.resize threw", it) }
                    .getOrDefault(false)
                if (!ok) {
                    // resize 是全有全无：reader/虚拟屏仍是旧尺寸，选区却已在新坐标系，
                    // 只有裁切内容暂时不对。绝不因此结束会话。
                    Log.w(TAG, "resize ${newW}x$newH@$dpi failed; keeping ${capturer.width}x${capturer.height}")
                }
            }
        }

        if (!geometryChanged) return // 纯 fontScale / uiMode / locale / 键盘变化

        if (mode == ScreenOcrMode.FULLSCREEN) {
            // 覆盖模式没有选区可映射：整屏重取，旧覆盖块必须清空，否则上一方向的
            // 译文会留在屏幕中间。
            currentRegion = Rect(0, 0, newW, newH)
            regionSpaceW = newW
            regionSpaceH = newH
            regionSpaceRot = newRot
            lastHash = Long.MIN_VALUE
            coverEpoch++
            coverBlockManager.clear()
            coverOverlay?.reclamp()
            // The rebuilt window starts empty, but the old view's blocks would keep
            // the next frame's identical rects filtered out as "already painted".
            coverOverlay?.update(emptyList())
            Log.i(TAG, "OCRMAP cover display change rot=$newRot ${newW}x$newH")
            return
        }

        currentRegion = region
        lastHash = Long.MIN_VALUE // 换了坐标系，强制下一帧重新识别
        overlay?.showRegionOutline(region) // 必须同步，否则外框还画在旧位置
        if (!regionFits(region, newW, newH)) {
            // clamp 不变式的哨兵；真打印了说明 remapRegion/clampRegion 被改坏了。
            Log.e(TAG, "BUG: remapped region $region out of ${newW}x$newH")
        }
        Log.i(TAG, "OCRMAP display change rot=$newRot ${newW}x$newH region=$region")
        ScreenOcrBus.setStatus(
            if (paused) ScreenOcrBus.Status.Paused else ScreenOcrBus.Status.Running,
            getString(R.string.ocr_region_adjusted),
        )
    }

    private fun startAsForeground() {
        val notification = buildNotification()

        // Android 14: startForeground(TYPE_MEDIA_PROJECTION) must come BEFORE
        // getMediaProjection(). The type also justifies the special-use permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this,
            3,
            Intent(this, ScreenTextSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPi = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pausePi = PendingIntent.getService(
            this,
            4,
            Intent(this, ScreenTextSessionService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, LiveTranslateApp.CHANNEL_SUBTITLE)
            .setContentTitle(
                getString(
                    if (mode == ScreenOcrMode.FULLSCREEN) R.string.cover_fullscreen_notification_title
                    else R.string.ocr_notification_title,
                ),
            )
            .setContentText(
                getString(
                    when {
                        paused -> R.string.ocr_notification_paused_text
                        mode == ScreenOcrMode.FULLSCREEN -> R.string.cover_notification_text
                        else -> R.string.ocr_notification_text
                    },
                ),
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPi)
            .addAction(
                0,
                getString(if (paused) R.string.ocr_action_resume else R.string.ocr_action_pause),
                pausePi,
            )
            .addAction(0, getString(R.string.action_stop), stopPi)
            .setOngoing(true)
            .build()
    }

    /** Repaints the ongoing notification after a pause/resume toggle. */
    private fun updateNotification() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification())
        }.onFailure { Log.w(TAG, "updateNotification failed", it) }
    }

    private fun stopEverything(message: String) {
        if (stopped) return
        stopped = true
        paused = false
        ScreenOcrBus.setStatus(ScreenOcrBus.Status.Stopped, message)
        val historyList = synchronized(ocrRecords) { ocrRecords.toList() }
        if (historyList.isNotEmpty()) {
            val inFull = historyList.joinToString("\n\n") { it.first }
            val outFull = historyList.joinToString("\n\n") { it.second }
            val app = application as LiveTranslateApp
            app.historyRepository.append(
                HistoryEntry(System.currentTimeMillis(), inFull, outFull),
                maxEntries = if (currentSettings.historyMode == HistoryMode.SAVE_ALL) null
                else currentSettings.historyLimit,
            )
        }
        ocrJob?.cancel()
        ocrJob = null
        ocr?.close()
        ocr = null
        capturer?.release()
        capturer = null
        overlay?.hide()
        overlay = null
        coverBlockManager.clear()
        lastOutsideSamples = null
        coverOverlay?.hide()
        coverOverlay = null
        coverEpoch++
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
        coverOverlay?.hide()
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
        /** Full-screen cover refresh. Cached text re-paints instantly, so this is the
         *  whole latency budget on a scrolled page — keep it short. */
        private const val COVER_FRAME_INTERVAL_MS = 400L
        /** Blocks translated in parallel within one cover frame. */
        private const val TRANSLATE_CONCURRENCY = 4
        private const val MIN_REGION_PX = RegionGestureMath.MIN_REGION_PX
        /** TRANSPROF 诊断：空转多少轮汇报一次心跳（900ms × 10 ≈ 9s）。 */
        private const val IDLE_LOG_ROUNDS = 10
        /** 旋转/折叠后暂停采集的时长，避开投影帧重建期间的乱帧。 */
        private const val DISPLAY_PAUSE_MS = 500L
        private const val DISPLAY_PAUSE_POLL_MS = 100L
        private const val SAMPLE_GRID_SIZE = 24
        private const val SCREEN_CHANGE_DIFF_THRESHOLD = 0.15f
        private const val SUBTITLE_STALE_TTL_MS = 3500L
        const val ACTION_START = "com.xzm.realtimetranslate.action.START_SCREEN_OCR"
        const val ACTION_STOP = "com.xzm.realtimetranslate.action.STOP_SCREEN_OCR"
        const val ACTION_TOGGLE_PAUSE = "com.xzm.realtimetranslate.action.TOGGLE_PAUSE_SCREEN_OCR"
        const val EXTRA_REGION = "region"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_MODE = "mode"

        fun start(
            context: Context,
            region: Rect,
            resultCode: Int,
            data: Intent,
            mode: ScreenOcrMode = ScreenOcrMode.REGION,
        ) {
            val intent = Intent(context, ScreenTextSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REGION, region)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_MODE, mode.name)
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
