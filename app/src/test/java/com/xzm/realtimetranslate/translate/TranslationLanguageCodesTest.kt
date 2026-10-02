package com.xzm.realtimetranslate.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationLanguageCodesTest {

    @Test
    fun `google uses legacy codes for the two chinese variants`() {
        assertEquals("zh-CN", TranslationLanguageCodes.googleLangCode("zh-Hans"))
        assertEquals("zh-TW", TranslationLanguageCodes.googleLangCode("zh-Hant"))
    }

    @Test
    fun `both portuguese variants collapse onto google's single code`() {
        assertEquals("pt", TranslationLanguageCodes.googleLangCode("pt-BR"))
        assertEquals("pt", TranslationLanguageCodes.googleLangCode("pt-PT"))
    }

    @Test
    fun `unknown and plain codes pass through untouched`() {
        assertEquals("ja", TranslationLanguageCodes.googleLangCode("ja"))
        assertEquals("en", TranslationLanguageCodes.googleLangCode("en"))
        assertEquals("auto", TranslationLanguageCodes.googleLangCode("auto"))
        assertEquals("xx-YY", TranslationLanguageCodes.googleLangCode("xx-YY"))
    }
}
