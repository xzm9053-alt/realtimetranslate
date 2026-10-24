package com.xzm.realtimetranslate.data

/** What a screen-OCR session does with what it recognizes. */
enum class ScreenOcrMode {
    /** Capture the user's region and show the translation in a bubble (existing). */
    REGION,

    /** Capture the whole screen and paint the translation over the original text. */
    FULLSCREEN,
}
