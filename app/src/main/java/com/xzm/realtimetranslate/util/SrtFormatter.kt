package com.xzm.realtimetranslate.util

import com.xzm.realtimetranslate.data.VideoSubtitleMode
import com.xzm.realtimetranslate.video.VideoCue

/** Renders subtitle cues as SRT (SubRip) text. Pure so it is unit-testable. */
object SrtFormatter {

    private const val MIN_DURATION_MS = 800L

    fun format(
        cues: List<VideoCue>,
        mode: VideoSubtitleMode = VideoSubtitleMode.TRANSLATION,
    ): String = buildString {
        cues.forEachIndexed { position, cue ->
            val start = cue.startMs.coerceAtLeast(0L)
            val end = maxOf(cue.endMs, start + MIN_DURATION_MS)
            val text = render(cue, mode)
            append(position + 1).append('\n')
            append(timestamp(start)).append(" --> ").append(timestamp(end)).append('\n')
            append(normalize(text)).append('\n')
            if (position != cues.lastIndex) append('\n')
        }
    }

    /**
     * The two texts a cue can show, in the order they appear on screen.
     *
     * BOTH keeps the source on top and the translation underneath, matching the live
     * subtitle overlay. When translation and source are the same string — which is
     * exactly what the recognize-only engine produces — the second copy is dropped,
     * otherwise the subtitle would say everything twice.
     */
    fun render(cue: VideoCue, mode: VideoSubtitleMode): String {
        val source = cue.source.trim()
        val translation = cue.translation.trim()
        return when (mode) {
            VideoSubtitleMode.TRANSLATION -> translation.ifBlank { source }
            VideoSubtitleMode.SOURCE -> source.ifBlank { translation }
            VideoSubtitleMode.BOTH -> when {
                source.isBlank() -> translation
                translation.isBlank() -> source
                source == translation -> source
                else -> "$source\n$translation"
            }
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
