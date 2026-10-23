package com.xzm.realtimetranslate.data

/**
 * Which language(s) a video subtitle line carries.
 *
 * Separate from [SubtitleDisplayMode], which governs the live floating overlay: that
 * one can also hide the source entirely, whereas a burned-in or exported subtitle has
 * only the two texts to choose between.
 */
enum class VideoSubtitleMode {
    /** Only the translation. */
    TRANSLATION,

    /** Only the recognized text. */
    SOURCE,

    /** Source on the first line, translation on the second. */
    BOTH,
    ;

    companion object {
        fun fromStorage(raw: String?): VideoSubtitleMode =
            entries.firstOrNull { it.name == raw } ?: TRANSLATION
    }
}
