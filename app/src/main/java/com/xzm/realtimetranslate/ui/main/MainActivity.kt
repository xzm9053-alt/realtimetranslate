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
import com.xzm.realtimetranslate.service.ScreenOcrBus
import com.xzm.realtimetranslate.service.ScreenTextSessionService
import com.xzm.realtimetranslate.service.SessionBus
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                var pendingOcrRegion by remember { mutableStateOf<Rect?>(null) }
                var pendingOcrMode by remember { mutableStateOf(ScreenOcrMode.REGION) }
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
                                this,
                                region = region,
                                resultCode = result.resultCode,
                                data = result.data!!,
                                mode = pendingOcrMode,
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

                fun requestOcrProjection(region: Rect, mode: ScreenOcrMode = ScreenOcrMode.REGION) {
                    ScreenOcrBus.setStatus(
                        ScreenOcrBus.Status.Starting,
                        getString(
                            if (mode == ScreenOcrMode.FULLSCREEN) R.string.cover_starting
                            else R.string.msg_requesting_projection,
                        ),
                    )
                    pendingOcrRegion = region
                    pendingOcrMode = mode
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

                /** Resumes an OCR start whose permissions are now granted, in the mode the user picked. */
                fun continueOcrStart() {
                    if (pendingOcrMode == ScreenOcrMode.FULLSCREEN) {
                        // The service turns this placeholder into the full screen; the
                        // selection overlay is deliberately skipped for this mode.
                        requestOcrProjection(Rect(0, 0, 1, 1), ScreenOcrMode.FULLSCREEN)
                    } else {
                        showRegionSelector()
                    }
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
                        pendingOcrMode = ScreenOcrMode.REGION
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

                /**
                 * Full-screen cover mode. Same permission chain as region OCR, minus the
                 * area selector — the whole screen is the capture area.
                 */
                fun requestStartFullscreen() {
                    try {
                        pendingOcrMode = ScreenOcrMode.FULLSCREEN
                        // Switching modes needs a fresh projection: the token carries
                        // nothing about the mode, but the service fixes the mode at start.
                        if (ocrSession.status.isActive) {
                            stopOcr()
                        }
                        if (session.status == SessionBus.Status.Running ||
                            session.status == SessionBus.Status.Starting
                        ) {
                            scope.launch {
                                runCatching { SessionBus.stop() }
                                runCatching { SubtitleSessionService.stop(this@MainActivity) }
                            }
                        }
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
                        Log.e(TAG, "requestStartFullscreen", t)
                        ScreenOcrBus.setStatus(
                            ScreenOcrBus.Status.Error,
                            getString(R.string.msg_start_failed, t.message.orEmpty()),
                        )
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
                                onFullscreenStart = { requestStartFullscreen() },
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
