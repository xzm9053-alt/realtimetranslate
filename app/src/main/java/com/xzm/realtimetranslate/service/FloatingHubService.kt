package com.xzm.realtimetranslate.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.AudioSourceMode
import com.xzm.realtimetranslate.overlay.HubBallOverlay
import com.xzm.realtimetranslate.overlay.RegionSelectorOverlay
import com.xzm.realtimetranslate.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 悬浮球常驻前台服务：维持 HubBallOverlay 悬浮球的生命周期与常驻通知。
 */
class FloatingHubService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: HubBallOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = HubBallOverlay(this)
        overlay?.show()
        HubBus.setHubEnabled(true)

        // 监听命令流
        scope.launch {
            HubBus.commands.collect { cmd ->
                when (cmd) {
                    HubCommand.CloseHub -> stopSelf()
                    is HubCommand.LaunchStaticMode -> launchStatic(cmd.target)
                    is HubCommand.SwitchMode -> handleSwitchMode(cmd.mode)
                    else -> Unit
                }
            }
        }

        // 监听状态变化更新悬浮球
        scope.launch {
            HubBus.state.collect { state ->
                overlay?.updateState(state)
            }
        }
    }

    /**
     * 响应悬浮球发起的实时翻译模式切换：
     * 完全通过系统浮层（RegionSelectorOverlay）和透明跳板授权（ProjectionRequestActivity）处理，
     * 绝不跳转或唤起主 App 界面，让用户在第三方应用、游戏或视频中拥有纯粹的悬浮翻译体验。
     */
    private fun handleSwitchMode(mode: HubMode) {
        val app = applicationContext as LiveTranslateApp
        when (mode) {
            HubMode.SCREEN_REGION -> {
                // 如果当前正在声音同传，先停止声音同传
                if (SessionBus.state.value.status == SessionBus.Status.Running ||
                    SessionBus.state.value.status == SessionBus.Status.Starting
                ) {
                    scope.launch {
                        runCatching { SessionBus.stop() }
                        runCatching { SubtitleSessionService.stop(this@FloatingHubService) }
                    }
                }
                // 直接由前台服务展示选区浮层，完全不跳转进入主 App
                val selector = RegionSelectorOverlay(
                    context = this@FloatingHubService,
                    onConfirm = { region ->
                        ProjectionRequestActivity.requestOcr(this@FloatingHubService, region)
                    },
                    onCancel = { /* 用户取消选区 */ },
                )
                selector.show()
            }
            HubMode.VOICE -> {
                // 如果当前正在屏幕取词，先停止取词
                if (ScreenOcrBus.state.value.status.isActive) {
                    scope.launch {
                        runCatching { ScreenOcrBus.stop() }
                        runCatching { ScreenTextSessionService.stop(this@FloatingHubService) }
                    }
                }
                scope.launch {
                    val settings = app.settingsRepository.settings.first()
                    val audioSource = settings.audioSourceMode
                    if (audioSource == AudioSourceMode.MIC) {
                        SubtitleSessionService.start(
                            this@FloatingHubService,
                            audioSource = AudioSourceMode.MIC,
                            resultCode = 0,
                            data = Intent(),
                        )
                    } else {
                        ProjectionRequestActivity.requestVoice(this@FloatingHubService, audioSource)
                    }
                }
            }
            HubMode.IDLE -> Unit
        }
    }

    /**
     * Opens a static tool (camera / gallery / documents) in the main activity.
     *
     * Started from the service, not from the activity's command collector: HubBus
     * commands are a plain SharedFlow with no replay, so while the app is in the
     * background — exactly when the floating ball is being used — there is no
     * collector alive and the command would be dropped without a trace.
     *
     * Any running live session is stopped first. A screen cover drawn over this
     * app's own UI would both hide the picture and feed the cover back into OCR.
     */
    private fun launchStatic(target: StaticModeTarget) {
        HubBus.postCommand(HubCommand.StopCurrentMode)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_STATIC_MODE, target.name)
        }
        runCatching { startActivity(intent) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this,
            11,
            Intent(this, FloatingHubService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, LiveTranslateApp.CHANNEL_SUBTITLE)
            .setContentTitle(getString(R.string.hub_notification_title))
            .setContentText(getString(R.string.hub_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.hub_action_close), stopPi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        HubBus.setHubEnabled(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 55
        const val ACTION_START = "com.xzm.realtimetranslate.action.START_HUB"
        const val ACTION_STOP = "com.xzm.realtimetranslate.action.STOP_HUB"

        /** Set by [launchStatic]; read by MainActivity to open the matching tool page. */
        const val EXTRA_STATIC_MODE = "com.xzm.realtimetranslate.extra.STATIC_MODE"

        fun start(context: Context) {
            val intent = Intent(context, FloatingHubService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FloatingHubService::class.java).setAction(ACTION_STOP))
        }
    }
}
