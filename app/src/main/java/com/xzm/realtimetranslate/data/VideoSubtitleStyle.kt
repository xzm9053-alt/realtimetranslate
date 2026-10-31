package com.xzm.realtimetranslate.data

/**
 * 视频字幕的外观。预览层与压制层读同一份，
 * 否则会出现「预览是一个样、导出另一个样」的错位。
 */
data class VideoSubtitleStyle(
    /** 译文字号占屏宽的比例。用比例而不是绝对 px，因为压制需要按 1080p 等比放大。 */
    val fontScale: Float = Defaults.FONT_SCALE,
    /** 字幕背景板的不透明度（0 = 完全透明、1 = 纯黑）。 */
    val backgroundAlpha: Float = Defaults.BACKGROUND_ALPHA,
    /** 译文字色，ARGB Long。 */
    val textColor: Long = Defaults.TEXT_COLOR,
    /** 双语时原文行的字色。 */
    val sourceTextColor: Long = Defaults.SOURCE_TEXT_COLOR,
) {
    object Defaults {
        const val FONT_SCALE = 1.0f
        const val BACKGROUND_ALPHA = 0.667f
        const val TEXT_COLOR = 0xFFFFFFFF
        const val SOURCE_TEXT_COLOR = 0xDDFFFFFF
        const val FONT_SCALE_MIN = 0.6f
        const val FONT_SCALE_MAX = 1.6f
    }
}
