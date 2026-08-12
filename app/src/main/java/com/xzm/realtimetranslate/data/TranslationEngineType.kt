package com.xzm.realtimetranslate.data

/**
 * Which backend turns a recognized sentence into Chinese subtitles.
 */
enum class TranslationEngineType {
    DEEPSEEK,
    MICROSOFT,
    ;

    companion object {
        fun fromStorage(value: String?): TranslationEngineType =
            entries.firstOrNull { it.name == value } ?: DEEPSEEK
    }
}
