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
        fun fromStorage(v: String?): OcrScript {
            // Migrate the legacy default (CHINESE_MIX, only ever auto-assigned) to
            // AUTO so existing installs benefit from English/Japanese auto-picking
            // without touching the setting.
            if (v == null || v == "CHINESE_MIX") return AUTO
            return entries.firstOrNull { it.name == v } ?: AUTO
        }
    }
}
