package com.xzm.realtimetranslate.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.xzm.realtimetranslate.BuildConfig
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.UserSettings
import java.io.File

/**
 * Builds the diagnostic report and gets it off the device: share it through whatever the
 * user already uses (WeChat, mail), or save it to Downloads so they can attach it later.
 *
 * The report lives in `cacheDir/diagnostics/` — never in `filesDir` — because it is a
 * build artifact, not state: the system may reclaim it, and it is the one place the
 * report ever exists on disk before the user chooses where it goes.
 */
object LogSharing {

    private const val DIR = "diagnostics"

    /** Content log is chatty per sentence; this is a whole session's worth. */
    private const val CONTENT_LINES = 300

    /**
     * Assembles the report and returns the file. Blocking — file IO and log tail reads —
     * so callers run it on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun buildReport(context: Context, settings: UserSettings): File {
        val now = System.currentTimeMillis()
        val input = DiagnosticReport.Input(
            appName = context.getString(R.string.app_name),
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            packageName = context.packageName,
            generatedAtMillis = now,
            device = DeviceInfo.describe(context),
            settings = SettingsSummary.build(settings),
            crash = CrashReporter.read(context),
            lines = AppLog.tailMain(DiagnosticReport.TAIL_LINES),
            // Only when the user opted in; otherwise the file is never even read.
            contentLines = if (AppLog.contentEnabled) {
                AppLog.tailContent(CONTENT_LINES)
            } else {
                null
            },
        )

        val dir = File(context.cacheDir, DIR)
        // Every report is stale the moment it is written, so the previous one is swept
        // here rather than left to accumulate one file per tap.
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        if (!dir.isDirectory && !dir.mkdirs()) error("无法创建导出目录")
        return File(dir, DiagnosticReport.fileName(now)).apply { writeText(DiagnosticReport.build(input)) }
    }

    /**
     * Hands the file to the system share sheet.
     *
     * The authority is derived from `packageName` rather than hard-coded, because the
     * debug variant's id carries a `.debug` suffix and a literal authority would throw
     * `IllegalArgumentException` there and only there.
     */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.settings_share_chooser_title))
        // The ViewModel, not an Activity, starts this — a chooser launched without a task
        // would crash.
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Saves the same report to public Downloads. Returns the display path. */
    fun save(context: Context, file: File): Result<String> =
        ExportTranslator.saveToDownloads(
            context = context,
            fileName = file.name,
            content = file.readText(),
            mimeType = "text/plain",
        )
}
