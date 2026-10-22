package com.xzm.realtimetranslate.data

/**
 * Which ML Kit bundled OCR recognizer to run for screen-region text recognition.
 *
 * - [AUTO] (default) runs the Latin + Japanese recognizers in parallel and picks
 *   the better result — covers the common foreign-language screens (English /
 *   Japanese, where the Japanese model also reads kanji and embedded Latin).
 * - [LATIN] is the dedicated Latin model from `com.google.mlkit:text-recognition`,
 *   much stronger than the CJK models for English / Latin UI text and digits.
 * - [CHINESE_MIX] / [JAPANESE] / [KOREAN] are the per-script CJK recognizers.
 *
 * All models are bundled, offline, and require no Google Play Services.
 */
enum class OcrScript {
    AUTO,
    LATIN,
    CHINESE_MIX,
    JAPANESE,
    KOREAN,
    ;

    companion object {
        /**
         * [alreadyMigrated] separates a value the user picked from the legacy
         * factory default: older builds wrote CHINESE_MIX for every install, so it
         * is rewritten to AUTO exactly once, then honoured as a real choice.
         */
        fun fromStorage(v: String?, alreadyMigrated: Boolean = false): OcrScript {
            val stored = entries.firstOrNull { it.name == v } ?: return AUTO
            if (!alreadyMigrated && stored == CHINESE_MIX) return AUTO
            return stored
        }
    }
}
