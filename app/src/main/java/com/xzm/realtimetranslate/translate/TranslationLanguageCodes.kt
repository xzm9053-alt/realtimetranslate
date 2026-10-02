package com.xzm.realtimetranslate.translate

/**
 * The app's BCP-47-ish language codes ("zh-Hans", "pt-BR") translated into whatever
 * a translation provider wants. Unknown codes pass through unchanged, which is the
 * right default: most providers accept a bare language subtag.
 */
object TranslationLanguageCodes {

    /** The keyless Google web endpoint uses Google's legacy language codes. */
    fun googleLangCode(code: String): String = when (code) {
        "zh-Hans" -> "zh-CN"
        "zh-Hant" -> "zh-TW"
        // Google has one Portuguese target; pt-BR/pt-PT both collapse onto it.
        "pt-BR", "pt-PT" -> "pt"
        else -> code
    }
}
