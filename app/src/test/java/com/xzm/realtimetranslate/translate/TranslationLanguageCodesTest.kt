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

class UnescapeHtmlEntitiesTest {

    @Test
    fun `decodes the entities cloud translation v2 actually emits`() {
        // v2 escapes even with format=text, so an apostrophe arrives as &#39;.
        assertEquals("it's", unescapeHtmlEntities("it&#39;s"))
        assertEquals("a & b", unescapeHtmlEntities("a &amp; b"))
        assertEquals("\"quoted\"", unescapeHtmlEntities("&quot;quoted&quot;"))
        assertEquals("a < b > c", unescapeHtmlEntities("a &lt; b &gt; c"))
    }

    @Test
    fun `does not double-decode an escaped entity`() {
        // The trap with sequential replace(): &amp; first turns this into "&#39;",
        // which the next step then decodes into "'" — a character the source never had.
        // Picking the wrong order silently corrupts text; a single pass cannot.
        assertEquals("&#39;", unescapeHtmlEntities("&amp;#39;"))
        assertEquals("&amp;", unescapeHtmlEntities("&amp;amp;"))
    }

    @Test
    fun `decodes numeric entities in both bases`() {
        assertEquals("'", unescapeHtmlEntities("&#39;"))
        assertEquals("'", unescapeHtmlEntities("&#x27;"))
        assertEquals("你", unescapeHtmlEntities("&#20320;"))
    }

    @Test
    fun `leaves plain text and unknown entities alone`() {
        assertEquals("plain text", unescapeHtmlEntities("plain text"))
        assertEquals("100% sure", unescapeHtmlEntities("100% sure"))
        // Not a known name and not numeric — must survive verbatim.
        assertEquals("&bogus;", unescapeHtmlEntities("&bogus;"))
        // A bare ampersand is not the start of an entity.
        assertEquals("fish & chips", unescapeHtmlEntities("fish & chips"))
    }

    @Test
    fun `keeps the non-breaking space as such instead of a plain space`() {
        assertEquals("a b", unescapeHtmlEntities("a&nbsp;b"))
    }
}
