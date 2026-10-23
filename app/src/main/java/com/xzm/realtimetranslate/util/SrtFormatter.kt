package com.xzm.realtimetranslate.util

import com.xzm.realtimetranslate.video.VideoCue

/** Renders subtitle cues as SRT (SubRip) text. Pure so it is unit-testable. */
object SrtFormatter {

    private const val MIN_DURATION_MS = 800L

    fun format(cues: List<VideoCue>): String = buildString {
        cues.forEachIndexed { position, cue ->
            val start = cue.startMs.coerceAtLeast(0L)
            val end = maxOf(cue.endMs, start + MIN_DURATION_MS)
            val text = cue.translation.ifBlank { cue.source }
            append(position + 1).append('\n')
            append(timestamp(start)).append(" --> ").append(timestamp(end)).append('\n')
            append(normalize(text)).append('\n')
            if (position != cues.lastIndex) append('\n')
        }
    }

    fun timestamp(millis: Long): String {
        val total = millis.coerceAtLeast(0L)
        val hours = total / 3_600_000
        val minutes = total / 60_000 % 60
        val seconds = total / 1_000 % 60
        val ms = total % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, ms)
    }

    private fun normalize(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').trim()
}
