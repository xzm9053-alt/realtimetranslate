package com.xzm.realtimetranslate.data

import androidx.annotation.StringRes
import com.xzm.realtimetranslate.R

/**
 * 翻译领域风格：同一句话，不同领域该译成不同的语气。
 *
 * 仅对大模型翻译引擎生效（DeepSeek / 智谱 / Gemini / 通用 OpenAI）。微软与谷歌免费端点
 * 接受的是纯文本，没有系统提示词的概念，选了也不会生效。
 */
enum class TranslationDomain(
    @StringRes val labelRes: Int,
    @StringRes val summaryRes: Int,
) {
    GENERAL(R.string.domain_general, R.string.domain_general_summary),
    GAME_ANIME(R.string.domain_game_anime, R.string.domain_game_anime_summary),
    BUSINESS(R.string.domain_business, R.string.domain_business_summary),
    ACADEMIC(R.string.domain_academic, R.string.domain_academic_summary),
    ENTERTAINMENT(R.string.domain_entertainment, R.string.domain_entertainment_summary),
    ;

    companion object {
        fun fromStorage(name: String?): TranslationDomain =
            entries.firstOrNull { it.name == name } ?: GENERAL
    }
}
