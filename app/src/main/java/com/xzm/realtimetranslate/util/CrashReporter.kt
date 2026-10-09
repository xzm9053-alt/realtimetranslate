package com.xzm.realtimetranslate.util

import android.content.Context
import android.os.Process
import com.xzm.realtimetranslate.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Records the crash that kills the app, so "the app just closes" becomes a stack trace the
 * author can read.
 *
 * The file is written to `filesDir/logs/crash.log` — separate from [AppLog]'s rotating
 * files on purpose: rotation would eventually eat the one record that matters, and the
 * report wants it at the top rather than buried in 800 lines of heartbeats.
 *
 * Scope, which the UI copy must not overpromise: this catches uncaught Java/Kotlin
 * exceptions only. A native crash, the low-memory killer, or the user swiping the app
 * away never reaches this handler, so a missing crash record does not mean "no crash".
 */
object CrashReporter {

    const val FILE_NAME = "crash.log"

    /** Cap on the pre-crash context we copy into the crash file. */
    private const val RECENT_LINES = 200

    /** Writing must not outlive the dying process; the system kills us regardless. */
    private const val WRITE_TIMEOUT_MS = 1500L

    /** How long a recorded crash keeps being reported as "last run crashed". */
    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    private val installed = AtomicBoolean(false)

    @Volatile private var appContext: Context? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Nothing in here may throw: an exception from the crash handler replaces the
            // original one and the real cause is lost forever.
            runCatching { record(thread, error) }
            // Always delegate. The default handler is what shows the "app stopped" dialog
            // and kills the process; swallowing it would leave a half-dead app behind.
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun crashFile(context: Context): File = File(logsDir(context), FILE_NAME)

    /** True when a crash was recorded recently enough that the user still remembers it. */
    fun hasRecentCrash(context: Context): Boolean {
        val file = crashFile(context)
        if (!file.isFile) return false
        val age = System.currentTimeMillis() - file.lastModified()
        return age in 0..RECENT_WINDOW_MS
    }

    /** The crash record, or null when there is none. Read off the main thread. */
    fun read(context: Context): String? =
        runCatching { crashFile(context).takeIf { it.isFile }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { crashFile(context).delete() }
    }

    // ------------------------------------------------------------- implementation

    private fun logsDir(context: Context): File = File(context.filesDir, "logs")

    private fun record(thread: Thread, error: Throwable) {
        val context = appContext ?: return
        // An anchor in the ordinary log, so the report's tail shows where the app died
        // even if someone reads app.log alone.
        AppLog.e(AppLog.TAG, "CRASH → $FILE_NAME (${error.javaClass.name})")
        // Bounded internally, and the whole point is that the last few lines before the
        // crash are the interesting ones.
        AppLog.flush()

        val text = buildText(context, thread, error)
        // Disk IO on a thread we can time-box: a hung write must not turn a crash into an
        // ANR. Daemon, so it can never hold the process open either.
        val worker = Thread({ runCatching { writeCrash(context, text) } }, "CrashReporter-writer")
        worker.isDaemon = true
        worker.start()
        runCatching { worker.join(WRITE_TIMEOUT_MS) }
    }

    private fun buildText(context: Context, thread: Thread, error: Throwable): String = buildString {
        append("================ CRASH ================\n")
        append("time: ${stamp(System.currentTimeMillis())}\n")
        append("app: ${context.applicationInfo.loadLabel(context.packageManager)} ")
        append("v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})\n")
        append("package: ${context.packageName}\n")
        append("thread: ${thread.name}\n")
        append("pid: ${Process.myPid()}\n")
        append(DeviceInfo.describe(context))
        append("--------------- exception ---------------\n")
        append(stackTraceOf(error))
        append("\n--------------- last $RECENT_LINES log lines ---------------\n")
        val recent = AppLog.recentLines().takeLast(RECENT_LINES)
        if (recent.isEmpty()) {
            // The ring only exists once AppLog is installed, and it is cleared by
            // "clear logs" — fall back to whatever did reach disk.
            append("(in-memory log empty; disk tail below)\n")
            append(AppLog.tailMain(RECENT_LINES).joinToString("\n"))
            append("\n")
        } else {
            for (line in recent) append(line).append('\n')
        }
        append("================ END ================\n")
    }

    private fun stackTraceOf(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return LogSanitizer.sanitize(sw.toString(), LogSanitizer.STACK_MAX_LEN)
    }

    private fun writeCrash(context: Context, text: String) {
        val file = crashFile(context)
        if (!file.parentFile!!.isDirectory && !file.parentFile!!.mkdirs()) return
        // Overwrite: only the most recent crash is of interest, and the user is asked to
        // send one report, not a history.
        file.writeText(text)
    }

    private fun stamp(millis: Long): String =
        FORMAT.format(Date(millis))

    private val FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
}
