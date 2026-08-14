package com.xzm.realtimetranslate.data

/**
 * What the floating subtitle overlay shows.
 */
enum class SubtitleDisplayMode {
    /** 原文 + 译文：上下分栏，中间分隔线，各占一半。 */
    BOTH,

    /** 仅原文：只显示识别出的原文，占满全高。 */
    SOURCE,

    /** 仅译文：只显示翻译结果，占满全高。 */
    TRANSLATION,
    ;

    companion object {
        fun fromStorage(raw: String?): SubtitleDisplayMode =
            entries.firstOrNull { it.name == raw } ?: TRANSLATION
    }
}
