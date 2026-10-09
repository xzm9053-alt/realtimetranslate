package com.xzm.realtimetranslate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.HistoryRepository
import com.xzm.realtimetranslate.data.UserSettingsRepository
import com.xzm.realtimetranslate.util.AppLog
import com.xzm.realtimetranslate.util.CrashReporter
import com.xzm.realtimetranslate.util.ModelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiveTranslateApp : Application() {
    lateinit var settingsRepository: UserSettingsRepository
        private set
    lateinit var apiKeyStore: ApiKeyStore
        private set
    lateinit var modelManager: ModelDownloader
        private set
    lateinit var historyRepository: HistoryRepository
        private set

    // Application-scoped IO scope: lives as long as the process, so it can't leak.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Must be the very first thing: repositories below already log from their
        // constructors, and anything logged before this only reaches logcat.
        AppLog.install(this)
        CrashReporter.install(this)
        settingsRepository = UserSettingsRepository(this)
        apiKeyStore = ApiKeyStore(this)
        modelManager = ModelDownloader(this)
        historyRepository = HistoryRepository(this)
        // Fast path: unpack the bundled models in the background so they are
        // ready by the time the user starts a session. connect() also blocks on
        // the same idempotent seed as a fallback.
        appScope.launch { runCatching { modelManager.seedFromAssetsIfNeeded() } }
        // Content logging is a setting, but the logger is a process-wide singleton — the
        // flow is the only thing that can turn it on and off while the app runs.
        appScope.launch {
            settingsRepository.settings.collect { AppLog.contentEnabled = it.diagnosticLogContent }
        }
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_SUBTITLE,
            getString(R.string.notification_channel_subtitle),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_SUBTITLE = "subtitle_session"
    }
}
