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
import com.xzm.realtimetranslate.overlay.HubBallOverlay
import com.xzm.realtimetranslate.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
