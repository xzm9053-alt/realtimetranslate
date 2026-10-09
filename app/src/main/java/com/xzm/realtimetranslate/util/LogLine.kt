package com.xzm.realtimetranslate.util

/** A parsed log line. [level] is null for continuation lines (throwable stack frames). */
data class LogEntry(
    val level: Char?,
    val tag: String?,
    val message: String,
    val raw: String,
)

/**
 * Parses the fixed line format [AppLog] writes:
 *
 * ```
 * 2026-07-14 22:35:01.123 I LiveTranslateClient: WebSocket open code=101
 *     java.io.IOException: HTTP 401
 *         at com.xzm…(LiveTranslateClient.kt:141)
 * ```
 *
 * Stack frames carry no header of their own, so the log viewer treats every headerless
 * line as a continuation of the entry above it. That is what keeps a crash trace
 * attached to the `E` line it belongs to when the user filters down to errors only.
 */
object LogLine {

    private val HEADER = Regex(
        """^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) ([VDIWE]) ([^:]+): (.*)$"""
    )

    fun parse(line: String): LogEntry {
        val m = HEADER.find(line) ?: return LogEntry(null, null, line, line)
        return LogEntry(
            level = m.groupValues[2].firstOrNull(),
            tag = m.groupValues[3],
            message = m.groupValues[4],
            raw = line,
        )
    }

    /** The level of a header line, or null when [line] is a continuation. */
    fun levelOf(line: String): Char? = HEADER.find(line)?.groupValues?.get(2)?.firstOrNull()

    /** True for the levels a user reporting a problem actually needs to see. */
    fun isError(level: Char?): Boolean = level == 'W' || level == 'E'

    /**
     * Keeps warnings/errors and the continuation lines that follow them — heartbeats
     * (`TRANSPROF ocrloopidle …`) and other `I`/`D` chatter are what drowns a report,
     * and they are exactly what this drops.
     */
    fun filterErrors(lines: List<String>): List<String> {
        val out = ArrayList<String>(lines.size)
        var keep = false
        for (line in lines) {
            val level = levelOf(line)
            if (level != null) keep = isError(level)
            if (keep) out.add(line)
        }
        return out
    }
}
