package com.xzm.realtimetranslate.ui.main

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.xzm.realtimetranslate.BuildConfig
import com.xzm.realtimetranslate.util.AppLog as Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.AudioSourceMode
import com.xzm.realtimetranslate.data.ScreenOcrMode
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.overlay.RegionSelectorOverlay
import com.xzm.realtimetranslate.service.FloatingHubService
import com.xzm.realtimetranslate.service.HubBus
import com.xzm.realtimetranslate.service.HubCommand
import com.xzm.realtimetranslate.service.HubMode
import com.xzm.realtimetranslate.service.ScreenOcrBus
import com.xzm.realtimetranslate.service.ScreenTextSessionService
import com.xzm.realtimetranslate.service.SessionBus
import com.xzm.realtimetranslate.service.StaticModeTarget
import com.xzm.realtimetranslate.service.SubtitleSessionService
import com.xzm.realtimetranslate.ui.components.UpdateDialog
import com.xzm.realtimetranslate.ui.history.HistoryScreen
import com.xzm.realtimetranslate.ui.history.HistoryViewModel
import com.xzm.realtimetranslate.ui.history.HistoryViewModelFactory
import com.xzm.realtimetranslate.ui.settings.SettingsScreen
import com.xzm.realtimetranslate.ui.settings.SettingsViewModel
import com.xzm.realtimetranslate.ui.settings.SettingsViewModelFactory
import com.xzm.realtimetranslate.ui.subtitle.SubtitleScreen
import com.xzm.realtimetranslate.ui.subtitle.SubtitleViewModel
import com.xzm.realtimetranslate.ui.subtitle.SubtitleViewModelFactory
import com.xzm.realtimetranslate.ui.theme.AppTheme
import com.xzm.realtimetranslate.update.RemoteUpdateInfo
import com.xzm.realtimetranslate.update.UpdateChecker
import com.xzm.realtimetranslate.update.UpdateCheckResult
import com.xzm.realtimetranslate.util.PermissionUtils
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    /**
     * A camera / gallery / document launch requested from the floating ball.
     *
     * Carried through a field rather than only through HubBus: the ball posts the
     * command while this activity may not exist at all, so the service starts us
     * with an extra instead. Both entry points (cold start and onNewIntent) land
     * here.
     */
    private val staticLaunch = mutableStateOf<StaticModeTarget?>(null)

    /**
     * 别的 App 递过来的视频。跟 [staticLaunch] 分开存，因为它们是两件事：
     * 悬浮球只要求“打开哪个页”，而这里还多一个要导入的文件。
     */
    private val pendingVideoUri = mutableStateOf<Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStaticLaunch(intent)
        handleSharedVideo(intent)
    }

    /** Consumes [FloatingHubService.EXTRA_STATIC_MODE] if present; unknown values are ignored. */
    private fun handleStaticLaunch(intent: Intent?) {
        val raw = intent?.getStringExtra(FloatingHubService.EXTRA_STATIC_MODE) ?: return
        // Clear it so a configuration change or a later onNewIntent does not reopen
        // the page after the user has navigated away from it.
        intent.removeExtra(FloatingHubService.EXTRA_STATIC_MODE)
        val target = StaticModeTarget.entries.firstOrNull { it.name == raw } ?: return
        staticLaunch.value = target
    }

    /**
     * 接收别的 App（目前是录制 App）通过 ACTION_SEND 递来的视频，并直接落到视频翻译页。
     *
     * 两件事必须同时做：设 `staticLaunch = VIDEO` 把页面切过去，
     * 再把 URI 存起来等 `MoreScreen` 取走 —— 只做前一件的话，用户看到的是
     * 一个空的视频页，还得自己去相册里找刚才那个文件。
     *
     * **不自动开始识别**：识别要跑好几分钟，且可能因未配 API Key、模型未就绪、
     * 实时字幕正在跑而失败 —— 自动开始只会让这些失败来得莫名其妙。
     * 留给用户点那一下，失败就发生在他刚刚主动点过的地方。
     */
    private fun handleSharedVideo(intent: Intent?) {
        if (intent == null) return
        if (!isVideoShare(intent.action, intent.type)) return
        val uri: Uri? = intent.parcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) return
        // 不清掉的话，旋转屏幕触发重建会把同一个文件再导一次
        intent.removeExtra(Intent.EXTRA_STREAM)
        pendingVideoUri.value = uri
        staticLaunch.value = StaticModeTarget.VIDEO
    }

    private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleStaticLaunch(intent)
        handleSharedVideo(intent)
        val app = application as LiveTranslateApp

        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                val pagerState = rememberPagerState(pageCount = { 3 })

                val subtitleVm: SubtitleViewModel = viewModel(
                    factory = SubtitleViewModelFactory(
                        application as Application,
                        app.settingsRepository,
                        app.apiKeyStore,
                    ),
                )
                val settingsVm: SettingsViewModel = viewModel(
                    factory = SettingsViewModelFactory(app, app.settingsRepository, app.apiKeyStore),
                )
                val historyVm: HistoryViewModel = viewModel(
                    factory = HistoryViewModelFactory(app, app.historyRepository),
                )

                val session by SessionBus.state.collectAsStateWithLifecycle()
                val ocrSession by ScreenOcrBus.state.collectAsStateWithLifecycle()
                val settings by subtitleVm.settings.collectAsStateWithLifecycle()
                val exportMessage by subtitleVm.exportMessage.collectAsStateWithLifecycle()

                var pendingStart by remember { mutableStateOf(false) }
                var pendingOcrStart by remember { mutableStateOf(false) }
                var pendingHubStart by remember { mutableStateOf(false) }
                var pendingOcrRegion by remember { mutableStateOf<Rect?>(null) }
                var updateInfoToPrompt by remember { mutableStateOf<RemoteUpdateInfo?>(null) }

                fun openVideoUrl(url: String) {
                    val targetUrl = url.ifBlank { "https://space.bilibili.com/1366321010" }
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to open video url: $targetUrl", e)
                    }
                }

                // 启动时在后台静默检查一次更新
                LaunchedEffect(Unit) {
                    val result = UpdateChecker().checkForUpdate(
                        UpdateChecker.DEFAULT_UPDATE_URL,
                        BuildConfig.VERSION_CODE,
                    )
                    if (result is UpdateCheckResult.HasUpdate) {
                        updateInfoToPrompt = result.info
                    }
                }

                val ocrProjectionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val region = pendingOcrRegion
                    pendingOcrRegion = null
                    if (result.resultCode == Activity.RESULT_OK && result.data != null && region != null) {
                        runCatching {
                            ScreenTextSessionService.start(
                                this@MainActivity,
                                region = region,
                                resultCode = result.resultCode,
                                data = result.data!!,
                                mode = ScreenOcrMode.REGION,
                            )
                        }.onFailure {
                            Log.e(TAG, "start ocr service failed", it)
                            ScreenOcrBus.setStatus(
                                ScreenOcrBus.Status.Error,
                                getString(R.string.msg_service_start_failed, it.message.orEmpty()),
                            )
                        }
                    } else {
                        ScreenOcrBus.setStatus(
                            ScreenOcrBus.Status.Error,
                            getString(R.string.msg_projection_cancelled),
                        )
                    }
                }

                fun requestOcrProjection(region: Rect) {
                    ScreenOcrBus.setStatus(
                        ScreenOcrBus.Status.Starting,
                        getString(R.string.msg_requesting_projection),
                    )
                    pendingOcrRegion = region
                    val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    ocrProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                }

                fun showRegionSelector() {
                    val selector = RegionSelectorOverlay(
                        context = this@MainActivity,
                        onConfirm = { region -> requestOcrProjection(region) },
                        onCancel = { /* 用户取消选区 */ },
                    )
                    selector.show()
                }

                /** Resumes an OCR start whose permissions are now granted. */
                fun continueOcrStart() {
                    showRegionSelector()
                }

                fun stopOcr() {
                    scope.launch {
                        runCatching { ScreenOcrBus.stop() }
                        runCatching { ScreenTextSessionService.stop(this@MainActivity) }
                    }
                }

                val projectionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        runCatching {
                            SubtitleSessionService.start(
                                this,
                                audioSource = settings.audioSourceMode,
                                resultCode = result.resultCode,
                                data = result.data!!,
                            )
                        }.onFailure {
                            Log.e(TAG, "start service failed", it)
                            SessionBus.setStatus(
                                SessionBus.Status.Error,
                                getString(R.string.msg_service_start_failed, it.message.orEmpty()),
                            )
                        }
                    } else {
                        SessionBus.setStatus(
                            SessionBus.Status.Error,
                            getString(R.string.msg_projection_cancelled),
                        )
                    }
                }

                val overlayLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    if (PermissionUtils.canDrawOverlays(this)) {
                        when {
                            pendingStart -> {
                                pendingStart = false
                                continueStartAfterPermissions(
                                    settings.audioSourceMode,
                                    projectionLauncher::launch,
                                )
                            }
                            pendingOcrStart -> {
                                pendingOcrStart = false
                                continueOcrStart()
                            }
                            pendingHubStart -> {
                                pendingHubStart = false
                                settingsVm.setHubEnabled(true)
                                FloatingHubService.start(this@MainActivity)
                            }
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { granted ->
                    val ok = granted.values.all { it }
                    if (!ok) {
                        SessionBus.setStatus(
                            SessionBus.Status.Error,
                            getString(R.string.msg_need_permissions),
                        )
                        return@rememberLauncherForActivityResult
                    }
                    if (!PermissionUtils.canDrawOverlays(this)) {
                        pendingStart = true
                        overlayLauncher.launch(PermissionUtils.overlaySettingsIntent(this))
                    } else {
                        continueStartAfterPermissions(settings.audioSourceMode, projectionLauncher::launch)
                    }
                }

                val ocrPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { granted ->
                    val ok = granted.values.all { it }
                    if (!ok) {
                        ScreenOcrBus.setStatus(
                            ScreenOcrBus.Status.Error,
                            getString(R.string.msg_need_permissions),
                        )
                        return@rememberLauncherForActivityResult
                    }
                    if (!PermissionUtils.canDrawOverlays(this)) {
                        pendingOcrStart = true
                        overlayLauncher.launch(PermissionUtils.overlaySettingsIntent(this))
                    } else {
                        continueOcrStart()
                    }
                }

                // 统一凭据门禁：引擎自带 requiresApiKey / keyLabelRes，调用点不再特判具体引擎。
                // 返回 null 表示可以启动，否则返回要展示的提示文案。
                fun missingEngineKeyMessage(): String? {
                    val engine = settings.translationEngine
                    if (app.apiKeyStore.hasKeyFor(engine)) return null
                    return getString(R.string.msg_need_api_key, getString(engine.keyLabelRes!!))
                }

                fun requestStartSubtitle() {
                    try {
                        // Mutual exclusion: starting audio subtitles stops a running OCR session
                        // (a paused one included — it still owns the screen-capture token).
                        if (ocrSession.status.isActive) {
                            stopOcr()
                        }
                        // 需要 Key 的引擎没填 Key 就不放行；免 Key 引擎（微软 / 谷歌免费）直接过。
                        missingEngineKeyMessage()?.let { message ->
                            SessionBus.setStatus(SessionBus.Status.Error, message)
                            scope.launch { pagerState.animateScrollToPage(1) }
                            return
                        }
                        val mode = settings.audioSourceMode
                        val needed = buildList {
                            if (mode.needsMicrophone) {
                                add(Manifest.permission.RECORD_AUDIO)
                            }
                            // Media capture also uses AudioRecord path that may need RECORD_AUDIO on some OEMs
                            if (mode.needsMediaProjection) {
                                add(Manifest.permission.RECORD_AUDIO)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.distinct().filter {
                            ContextCompat.checkSelfPermission(this, it) !=
                                PackageManager.PERMISSION_GRANTED
                        }
                        if (needed.isNotEmpty()) {
                            permissionLauncher.launch(needed.toTypedArray())
                            return
                        }
                        if (!PermissionUtils.canDrawOverlays(this)) {
                            pendingStart = true
                            overlayLauncher.launch(PermissionUtils.overlaySettingsIntent(this))
                            return
                        }
                        continueStartAfterPermissions(mode, projectionLauncher::launch)
                    } catch (t: Throwable) {
                        Log.e(TAG, "requestStartSubtitle", t)
                        SessionBus.setStatus(
                            SessionBus.Status.Error,
                            getString(R.string.msg_start_failed, t.message.orEmpty()),
                        )
                    }
                }

                fun requestStartOcr() {
                    try {
                        if (ocrSession.status.isActive) {
                            stopOcr()
                            return
                        }
                        // Mutual exclusion: starting OCR stops a running audio session.
                        if (session.status == SessionBus.Status.Running ||
                            session.status == SessionBus.Status.Starting
                        ) {
                            scope.launch {
                                runCatching { SessionBus.stop() }
                                runCatching {
                                    SubtitleSessionService.stop(this@MainActivity)
                                }
                            }
                        }
                        // 同 requestStartSubtitle：统一凭据门禁。
                        missingEngineKeyMessage()?.let { message ->
                            ScreenOcrBus.setStatus(ScreenOcrBus.Status.Error, message)
                            scope.launch { pagerState.animateScrollToPage(1) }
                            return
                        }
                        val needed = buildList {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.filter {
                            ContextCompat.checkSelfPermission(this, it) !=
                                PackageManager.PERMISSION_GRANTED
                        }
                        if (needed.isNotEmpty()) {
                            ocrPermissionLauncher.launch(needed.toTypedArray())
                            return
                        }
                        if (!PermissionUtils.canDrawOverlays(this)) {
                            pendingOcrStart = true
                            overlayLauncher.launch(PermissionUtils.overlaySettingsIntent(this))
                            return
                        }
                        continueOcrStart()
                    } catch (t: Throwable) {
                        Log.e(TAG, "requestStartOcr", t)
                        ScreenOcrBus.setStatus(
                            ScreenOcrBus.Status.Error,
                            getString(R.string.msg_start_failed, t.message.orEmpty()),
                        )
                    }
                }

                fun toggleHub(enabled: Boolean) {
                    if (enabled) {
                        if (!PermissionUtils.canDrawOverlays(this@MainActivity)) {
                            pendingHubStart = true
                            overlayLauncher.launch(PermissionUtils.overlaySettingsIntent(this@MainActivity))
                            return
                        }
                        settingsVm.setHubEnabled(true)
                        FloatingHubService.start(this@MainActivity)
                    } else {
                        settingsVm.setHubEnabled(false)
                        FloatingHubService.stop(this@MainActivity)
                    }
                }

                LaunchedEffect(settings.hubEnabled) {
                    if (settings.hubEnabled && PermissionUtils.canDrawOverlays(this@MainActivity)) {
                        FloatingHubService.start(this@MainActivity)
                    }
                }

                LaunchedEffect(Unit) {
                    HubBus.commands.collect { cmd ->
                        when (cmd) {
                            is HubCommand.LaunchStaticMode -> {
                                // Handled by FloatingHubService (it starts this activity with
                                // an extra). Reaching here means the ball posted while we were
                                // in the foreground; route it the same way.
                                staticLaunch.value = cmd.target
                            }
                            is HubCommand.CloseHub -> {
                                toggleHub(false)
                            }
                            else -> Unit
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MiuixTheme.colorScheme.surface,
                    bottomBar = {
                        NavigationBar(mode = NavigationBarDisplayMode.IconAndText) {
                            NavigationBarItem(
                                selected = pagerState.currentPage == 0,
                                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                                icon = Icons.Outlined.Subtitles,
                                label = stringResource(R.string.tab_subtitle),
                            )
                            NavigationBarItem(
                                selected = pagerState.currentPage == 1,
                                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                                icon = Icons.Outlined.Settings,
                                label = stringResource(R.string.tab_settings),
                            )
                            NavigationBarItem(
                                selected = pagerState.currentPage == 2,
                                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                                icon = Icons.Outlined.History,
                                label = stringResource(R.string.tab_history),
                            )
                        }
                    },
                ) { padding ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) { page ->
                        when (page) {
                            0 -> SubtitleScreen(
                                modifier = Modifier.fillMaxSize(),
                                settings = settings,
                                session = session,
                                exportMessage = exportMessage,
                                onSourceLanguage = { code ->
                                    scope.launch { subtitleVm.setSourceLanguage(code) }
                                },
                                onTargetLanguage = { code ->
                                    scope.launch { subtitleVm.setTargetLanguage(code) }
                                },
                                onAudioSource = { mode ->
                                    scope.launch { subtitleVm.setAudioSource(mode) }
                                },
                                ocrSession = ocrSession,
                                onStart = { requestStartSubtitle() },
                                onStop = {
                                    scope.launch {
                                        runCatching {
                                            SessionBus.stop()
                                            SubtitleSessionService.stop(this@MainActivity)
                                        }
                                    }
                                },
                                onOcrStart = { requestStartOcr() },
                                onOcrStop = { stopOcr() },
                                onExport = { subtitleVm.exportLastSession() },
                                canDrawOverlays = PermissionUtils.canDrawOverlays(this@MainActivity),
                                onUpdateSettings = { transform ->
                                    scope.launch { settingsVm.update(transform) }
                                },
                                onResetAppearance = {
                                    scope.launch { settingsVm.resetSubtitleAppearance() }
                                },
                                onOpenSettings = {
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                },
                                onToggleHub = { toggleHub(it) },
                                staticLaunch = staticLaunch.value,
                                pendingVideoUri = pendingVideoUri.value,
                                onStaticLaunchConsumed = {
                                    staticLaunch.value = null
                                    pendingVideoUri.value = null
                                },
                            )
                            1 -> SettingsScreen(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = settingsVm,
                                onOpenOverlayPermission = {
                                    startActivity(
                                        PermissionUtils.overlaySettingsIntent(this@MainActivity),
                                    )
                                },
                                onPromptUpdate = { updateInfoToPrompt = it },
                            )
                            else -> HistoryScreen(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = historyVm,
                            )
                        }
                    }
                }

                updateInfoToPrompt?.let { info ->
                    UpdateDialog(
                        info = info,
                        onDismiss = { updateInfoToPrompt = null },
                        onGoToVideo = { url ->
                            updateInfoToPrompt = null
                            openVideoUrl(url)
                        },
                    )
                }
            }
        }
    }

    private fun continueStartAfterPermissions(
        mode: AudioSourceMode,
        launchProjection: (Intent) -> Unit,
    ) {
        if (mode.needsMediaProjection) {
            SessionBus.setStatus(
                SessionBus.Status.Starting,
                getString(R.string.msg_requesting_projection),
            )
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            launchProjection(mpm.createScreenCaptureIntent())
        } else {
            runCatching {
                SubtitleSessionService.start(this, audioSource = mode)
            }.onFailure {
                SessionBus.setStatus(
                    SessionBus.Status.Error,
                    getString(R.string.msg_service_start_failed, it.message.orEmpty()),
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

/**
 * 这个 Intent 是不是「别的 App 递过来一段视频」。
 *
 * 抽成纯函数是为了能测：它决定的是**要不要把用户的画面切走**，
 * 而 `MainActivity` 本身在 JVM 单测里跑不起来。
 *
 * 只认 `ACTION_SEND`：`ACTION_VIEW` 是「打开」不是「分享」，
 * 而清单里本来也没注册 VIEW（那会让本应用混进相册的播放器选择列表）。
 */
internal fun isVideoShare(action: String?, mimeType: String?): Boolean {
    if (action != Intent.ACTION_SEND) return false
    return mimeType?.startsWith("video/") == true
}
