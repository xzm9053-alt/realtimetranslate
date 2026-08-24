package com.xzm.realtimetranslate.data

/**
 * Which ML Kit bundled OCR recognizer to run for screen-region text recognition.
 * CHINESE_MIX handles Chinese with embedded Latin letters/digits; JAPANESE and
 * KOREAN use the dedicated per-script recognizers. All are bundled, offline, and
 * require no Google Play Services.
 */
enum class OcrScript {
    CHINESE_MIX,
    JAPANESE,
    KOREAN,
    ;

    companion object {
        fun fromStorage(v: String?): OcrScript =
            entries.firstOrNull { it.name == v } ?: CHINESE_MIX
    }
}
