package com.xzm.realtimetranslate.util

import android.content.Context
import android.os.Process
import android.util.Log as AndroidLog
import com.xzm.realtimetranslate.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Drop-in replacement for [android.util.Log] that also appends to a rotating file under
 * `filesDir/logs/`, so an end user who cannot describe their problem can just send the
 * diagnostic report to the author.
 *
 * Design constraints, in order of importance:
 *
 * 1. **Never crash, never block.** Every call still writes to logcat — byte for byte what
 *    the app did before — and then hands the line to a queue. Sanitizing and disk IO
 *    happen on one daemon thread, so the cost stays off the audio-capture and OCR
 *    threads. Before [install], or after any IO failure, everything degrades to plain
 *    logcat. A logging bug must not be able to take the app down.
 * 2. **Nothing sensitive reaches the file.** Lines are scrubbed by [LogSanitizer] on the
 *    writer thread — logcat keeps the raw text (local, dev-only), the file and the
 *    in-memory ring never do.
 * 3. **One writer thread.** Serial writes mean no interleaved lines and no locking
 *    around rotation, which only the writer ever performs.
 *
 * Call sites elsewhere in the app opt in by swapping one import:
 * `import com.xzm.realtimetranslate.util.AppLog as Log`.
 */
object AppLog {

    const val TAG = "AppLog"

    const val MAIN_FILE_NAME = "app.log"
    const val MAIN_MAX_BYTES = 512L * 1024
    const val MAIN_BACKUPS = 3

    const val CONTENT_FILE_NAME = "content.log"
    const val CONTENT_MAX_BYTES = 256L * 1024
    const val CONTENT_BACKUPS = 2

    private const val QUEUE_CAPACITY = 2048
    private const val DRAIN_BATCH = 64
    private const val RING_CAPACITY = 256
    private const val WRITER_THREAD_NAME = "AppLog-writer"
    private const val FLUSH_TIMEOUT_MS = 300L

    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val queue = ArrayBlockingQueue<Record>(QUEUE_CAPACITY)
    private val droppedLines = AtomicLong(0)
    private val ring = ArrayDeque<String>()
    private val ringLock = Any()

    @Volatile private var mainStore: LogFileStore? = null
    @Volatile private var contentStore: LogFileStore? = null
    @Volatile private var writerAlive = false
    @Volatile private var logsDir: File? = null

    /**
     * When on, [content] calls are persisted — the original and translated text, which is
     * what you need to diagnose "the translation is wrong" but is also the user's private
     * content. Off by default; driven by the settings flow from `LiveTranslateApp`.
     */
    @Volatile var contentEnabled: Boolean = false

    // ---------------------------------------------------------------- public API

    fun v(tag: String?, msg: String?): Int = log('V', tag, msg, null)
    fun d(tag: String?, msg: String?): Int = log('D', tag, msg, null)
    fun i(tag: String?, msg: String?): Int = log('I', tag, msg, null)
    fun w(tag: String?, msg: String?): Int = log('W', tag, msg, null)
    fun e(tag: String?, msg: String?): Int = log('E', tag, msg, null)

    fun v(tag: String?, msg: String?, tr: Throwable?): Int = log('V', tag, msg, tr)
    fun d(tag: String?, msg: String?, tr: Throwable?): Int = log('D', tag, msg, tr)
    fun i(tag: String?, msg: String?, tr: Throwable?): Int = log('I', tag, msg, tr)
    fun w(tag: String?, msg: String?, tr: Throwable?): Int = log('W', tag, msg, tr)
    fun e(tag: String?, msg: String?, tr: Throwable?): Int = log('E', tag, msg, tr)

    /** `android.util.Log` also offers these; kept so an alias swap can never fail to compile. */
    fun w(tag: String?, tr: Throwable?): Int = log('W', tag, null, tr)
    fun e(tag: String?, tr: Throwable?): Int = log('E', tag, null, tr)

    /**
     * Records user-visible content (original or translated text). No-op unless
     * [contentEnabled] is on, and even then it goes to a *separate* file so ordinary logs
     * never accumulate private text.
     */
    fun content(tag: String, message: String) {
        if (!contentEnabled) return
        AndroidLog.d(tag, message)
        enqueue(LineRecord('D', tag, message, null, System.currentTimeMillis(), true))
    }

    /**
     * Opens the log files and starts the writer. Must be the first thing
     * `LiveTranslateApp.onCreate` does, so that anything logged during startup is
     * captured. Safe to call more than once.
     */
    fun install(context: Context) {
        if (mainStore != null) return
        runCatching {
            val dir = File(context.filesDir, "logs")
            val main = LogFileStore(dir, MAIN_FILE_NAME, MAIN_MAX_BYTES, MAIN_BACKUPS)
            val content = LogFileStore(dir, CONTENT_FILE_NAME, CONTENT_MAX_BYTES, CONTENT_BACKUPS)
            if (!main.ensureDir()) return@runCatching
            logsDir = dir
            mainStore = main
            contentStore = content
            writerAlive = true
            Thread({ writerLoop(main, content) }, WRITER_THREAD_NAME).apply {
                isDaemon = true
                start()
            }
            i(TAG, "==== app start v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) pid=${Process.myPid()} ====")
        }
    }

    /**
     * Blocks briefly until everything queued so far is on disk. Only for the crash
     * handler, where losing the last lines is exactly what we cannot afford.
     */
    fun flush() {
        if (!writerAlive) return
        val latch = CountDownLatch(1)
        if (queue.offer(BarrierRecord(latch))) {
            runCatching { latch.await(FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        } else {
            runCatching { Thread.sleep(50) }
        }
    }

    /** Last [RING_CAPACITY] sanitized lines, from memory — usable while crashing. */
    fun recentLines(): List<String> = synchronized(ringLock) { ring.toList() }

    /** Deletes every file in the log directory, including the crash record. */
    fun clear() {
        if (!writerAlive || !queue.offer(ClearRecord)) {
            runCatching { logsDir?.listFiles()?.forEach { it.delete() } }
        }
        synchronized(ringLock) { ring.clear() }
    }

    fun totalBytes(): Long = (mainStore?.totalBytes() ?: 0L) + (contentStore?.totalBytes() ?: 0L)

    fun logsDir(): File? = logsDir

    fun mainLogFile(): File? = mainStore?.current

    fun contentLogFile(): File? = contentStore?.current

    fun tailMain(maxLines: Int): List<String> = mainStore?.tail(maxLines) ?: emptyList()

    fun tailContent(maxLines: Int): List<String> = contentStore?.tail(maxLines) ?: emptyList()

    fun hasContentLog(): Boolean =
        contentStore?.current?.let { it.isFile && it.length() > 0L } == true

    // ------------------------------------------------------------- implementation

    private fun log(level: Char, tag: String?, msg: String?, tr: Throwable?): Int {
        val safeTag = tag ?: "?"
        val safeMsg = msg ?: "null"
        val result = when (level) {
            'V' -> AndroidLog.v(safeTag, safeMsg, tr)
            'D' -> AndroidLog.d(safeTag, safeMsg, tr)
            'I' -> AndroidLog.i(safeTag, safeMsg, tr)
            'W' -> AndroidLog.w(safeTag, safeMsg, tr)
            else -> AndroidLog.e(safeTag, safeMsg, tr)
        }
        enqueue(LineRecord(level, safeTag, safeMsg, tr, System.currentTimeMillis(), false))
        return result
    }

    private fun enqueue(record: LineRecord) {
        val installed = if (record.toContent) contentStore != null else mainStore != null
        if (!installed) return
        if (queue.offer(record)) return
        // Queue full. A warning or an error is worth more than a heartbeat, so those
        // evict the oldest entry instead of being dropped.
        if (record.level == 'W' || record.level == 'E') {
            queue.poll()
            droppedLines.incrementAndGet()
            if (!queue.offer(record)) droppedLines.incrementAndGet()
        } else {
            droppedLines.incrementAndGet()
        }
    }

    private fun writerLoop(mainFile: LogFileStore, contentFile: LogFileStore) {
        val main = RotatingWriter(mainFile)
        val content = RotatingWriter(contentFile)
        val batch = ArrayList<Record>(DRAIN_BATCH)
        val mainLines = ArrayList<String>(DRAIN_BATCH)
        val contentLines = ArrayList<String>(DRAIN_BATCH)

        while (true) {
            batch.clear()
            mainLines.clear()
            contentLines.clear()
            try {
                batch.add(queue.take())
                queue.drainTo(batch, DRAIN_BATCH - 1)

                for (record in batch) {
                    when (record) {
                        is ClearRecord -> {
                            main.write(mainLines); mainLines.clear()
                            content.write(contentLines); contentLines.clear()
                            main.close()
                            content.close()
                            mainFile.clear()
                            contentFile.clear()
                            logsDir?.listFiles()?.forEach { it.delete() }
                        }

                        is BarrierRecord -> {
                            main.write(mainLines); mainLines.clear()
                            content.write(contentLines); contentLines.clear()
                            record.latch.countDown()
                        }

                        is LineRecord -> {
                            val lines = format(record)
                            if (record.toContent) {
                                contentLines.addAll(lines)
                            } else {
                                mainLines.addAll(lines)
                                pushRing(lines)
                            }
                        }
                    }
                }

                main.write(mainLines)
                content.write(contentLines)
                reportDropped(main)
            } catch (t: Throwable) {
                // Disk full, revoked storage, ... Downgrade to logcat-only rather than
                // crashing. Nothing here may escape: this thread has no crash handler.
                runCatching { main.close() }
                runCatching { content.close() }
                mainStore = null
                contentStore = null
                writerAlive = false
                AndroidLog.w(TAG, "file logging disabled: ${t.javaClass.simpleName}: ${t.message}")
                return
            }
        }
    }

    private fun format(record: LineRecord): List<String> {
        val out = ArrayList<String>(4)
        out.add("${stamp(record.timeMillis)} ${record.level} ${record.tag}: ${LogSanitizer.sanitize(record.message)}")
        val error = record.error
        if (error != null) {
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            val trace = LogSanitizer.sanitize(sw.toString(), LogSanitizer.STACK_MAX_LEN)
            for (line in trace.split('\n')) {
                // Continuation lines carry no header, which is how LogLine tells them apart.
                if (line.isNotBlank()) out.add(line)
            }
        }
        return out
    }

    private fun pushRing(lines: List<String>) {
        synchronized(ringLock) {
            for (line in lines) {
                while (ring.size >= RING_CAPACITY) ring.removeFirst()
                ring.addLast(line)
            }
        }
    }

    private fun reportDropped(main: RotatingWriter) {
        val dropped = droppedLines.getAndSet(0)
        if (dropped <= 0L) return
        main.write(listOf("${stamp(System.currentTimeMillis())} W $TAG: dropped $dropped lines (log queue overflow)"))
    }

    private fun stamp(millis: Long): String =
        TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    // --------------------------------------------------------------- file writing

    private class RotatingWriter(private val store: LogFileStore) {
        private var writer: BufferedWriter? = null

        fun write(lines: List<String>) {
            if (lines.isEmpty()) return
            // Checked once per batch rather than per line: one stat per batch is free,
            // one per line would not be.
            if (writer != null && store.current.length() >= store.maxBytes) close()
            val out = writer ?: open()
            for (line in lines) {
                out.append(line)
                out.append('\n')
            }
            out.flush()
        }

        fun close() {
            val w = writer ?: return
            writer = null
            runCatching { w.flush() }
            runCatching { w.close() }
        }

        private fun open(): BufferedWriter {
            if (!store.ensureDir()) throw IOException("cannot create ${store.dir}")
            if (store.shouldRotate()) store.rotate()
            return BufferedWriter(
                OutputStreamWriter(FileOutputStream(store.current, true), Charsets.UTF_8)
            ).also { writer = it }
        }
    }

    private sealed class Record

    private class LineRecord(
        val level: Char,
        val tag: String,
        val message: String,
        val error: Throwable?,
        val timeMillis: Long,
        val toContent: Boolean,
    ) : Record()

    private class BarrierRecord(val latch: CountDownLatch) : Record()

    private object ClearRecord : Record()
}
