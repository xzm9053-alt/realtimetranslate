package com.xzm.realtimetranslate.translate

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassthroughTranslationEngineTest {

    @Test
    fun `echoes the recognized text unchanged`() = runBlocking {
        val out = PassthroughTranslationEngine
            .translate("你好，世界", sourceLang = "auto", targetLang = "zh-Hans")
            .toList()
        assertEquals(listOf("你好，世界"), out)
    }

    @Test
    fun `draft is the same passthrough, never a network call`() = runBlocking {
        val out = PassthroughTranslationEngine
            .translateDraft("Hello wor", sourceLang = "auto", targetLang = "zh-Hans")
            .toList()
        assertEquals(listOf("Hello wor"), out)
    }

    @Test
    fun `connection test succeeds without any key or network`() = runBlocking {
        val result = PassthroughTranslationEngine.testConnection("zh-Hans")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `empty input stays empty instead of inventing text`() = runBlocking {
        val out = PassthroughTranslationEngine
            .translate("", sourceLang = "auto", targetLang = "zh-Hans")
            .toList()
        assertEquals(listOf(""), out)
    }
}
