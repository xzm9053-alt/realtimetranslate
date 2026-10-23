package com.xzm.realtimetranslate.video

/**
 * One subtitle line before it gets an index: the ASR text with its translated text
 * and the time window it should be on screen.
 */
data class TimedText(
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val translation: String = "",
)

/** A finished subtitle cue, ready for playback and SRT export. */
data class VideoCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val translation: String,
    /** Translation failed; [translation] falls back to the recognized text. */
    val failed: Boolean = false,
)

fun TimedText.toVideoCue(index: Int, failed: Boolean = false): VideoCue = VideoCue(
    index = index,
    startMs = startMs,
    endMs = endMs,
    source = source,
    translation = translation.ifBlank { source },
    failed = failed,
)
