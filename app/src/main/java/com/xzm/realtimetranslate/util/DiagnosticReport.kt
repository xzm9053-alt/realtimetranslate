package com.xzm.realtimetranslate.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the author needs to diagnose a report, as one plain-text file.
 *
 * A single `.txt` rather than an archive: the user forwards it through WeChat or mail as
 * one attachment, and both the author and the user can open it on the phone with nothing
 * installed. The whole thing is a few hundred KB, so compressing it buys little and adds
 * a failure mode.
 *
 * Pure text assembly — the Android side (device info, log tails, settings) is passed in,
 * which is also what makes the layout testable.
 */
object DiagnosticReport {

    /** Log tail carried in the report. Deep enough for a whole session, ~100 KB. */
    const val TAIL_LINES = 800

    /** Profile lines are dense; this is plenty to see a latency distribution. */
    const val PROF_LINES = 200

    private val GENERATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** The markers worth hoisting out of 800 lines of chatter. See [extractProf]. */
    private val PROF_MARKERS = listOf("TRANSPROF", "MODELPROBE", "OCRMAP")

    data class Input(
        val appName: String,
        val versionName: String,
        val versionCode: Int,
        val packageName: String,
        val generatedAtMillis: Long,
        /** [DeviceInfo.describe] output. */
        val device: String,
        /** [SettingsSummary.build] output. */
        val settings: String,
        /** [CrashReporter.read] output, or null when the last run did not crash. */
        val crash: String?,
        /** Tail of `app.log`. */
        val lines: List<String>,
        /** Tail of `content.log`, only when the user turned content logging on. */
        val contentLines: List<String>? = null,
    )

    fun build(input: Input): String = buildString {
        if (!input.contentLines.isNullOrEmpty()) {
            // First line of the file, before anything else: this one is the user's own
            // translated text, and it must not be forwarded by reflex.
            append("*** 本报告包含原文/译文（你在设置里打开了「记录翻译原文/译文」）。")
            append("转发给任何人之前请先确认。***\n\n")
        }
        append("========== LiveTranslate 诊断报告 ==========\n")
        append("generated: ${stamp(input.generatedAtMillis)}\n")
        append("app: ${input.appName} v${input.versionName}(${input.versionCode})\n")
        append("package: ${input.packageName}\n\n")

        append("---------- 设备 ----------\n")
        append(input.device.trimEnd('\n')).append('\n')

        append("\n---------- 设置 ----------\n")
        append(input.settings.trimEnd('\n')).append('\n')

        append("\n---------- 崩溃记录 ----------\n")
        if (input.crash.isNullOrBlank()) {
            append("(无。注意：这里只记录未捕获的 Java/Kotlin 异常；")
            append("被系统杀进程或原生崩溃不会留下记录。)\n")
        } else {
            append(input.crash.trimEnd('\n')).append('\n')
        }

        append("\n---------- 关键指标 (${PROF_MARKERS.joinToString("/")}) ----------\n")
        val prof = extractProf(input.lines)
        if (prof.isEmpty()) {
            append("(无)\n")
        } else {
            for (line in prof) append(line).append('\n')
        }

        append("\n---------- 日志尾部 (最后 ${input.lines.size} 行 / 上限 $TAIL_LINES) ----------\n")
        if (input.lines.isEmpty()) {
            append("(无)\n")
        } else {
            for (line in input.lines) append(line).append('\n')
        }

        if (!input.contentLines.isNullOrEmpty()) {
            append("\n---------- 原文/译文 (最后 ${input.contentLines.size} 行) ----------\n")
            for (line in input.contentLines) append(line).append('\n')
        }

        append("\n========== 报告结束 ==========\n")
    }

    /**
     * The lines that answer "why is it slow / why did it fail", pulled out so the author
     * does not have to hunt for them among heartbeats. Matching is on the *message* after
     * the `TAG: ` prefix, so a URL or a user string containing "TRANSPROF" cannot leak in.
     */
    fun extractProf(lines: List<String>): List<String> =
        lines.filter { line ->
            val message = LogLine.parse(line).message.trimStart()
            PROF_MARKERS.any { message.startsWith(it) }
        }.takeLast(PROF_LINES)

    /** File name for the shared/saved report: sortable, and unique per second. */
    fun fileName(generatedAtMillis: Long): String {
        val stamp = FILE_STAMP.format(
            Instant.ofEpochMilli(generatedAtMillis).atZone(ZoneId.systemDefault())
        )
        return "realtimetranslate-diagnostic-$stamp.txt"
    }

    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    private fun stamp(millis: Long): String =
        GENERATED_AT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
