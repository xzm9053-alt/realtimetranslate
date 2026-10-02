package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationEngineTypeTest {

    @Test
    fun `a key label exists exactly for the engines that need a key`() {
        // The settings screen and the credential gate both read keyLabelRes without a
        // null check, so these two flags must never disagree.
        TranslationEngineType.entries.forEach { type ->
            if (type.requiresApiKey) {
                assertNotNull("$type requires a key but has no label", type.keyLabelRes)
            } else {
                assertNull("$type needs no key but declares a label", type.keyLabelRes)
            }
        }
    }

    @Test
    fun `capability matrix matches the engines as shipped`() {
        assertTrue(TranslationEngineType.DEEPSEEK.requiresApiKey)
        assertTrue(TranslationEngineType.DEEPSEEK.isLlm)

        assertTrue(TranslationEngineType.ZHIPU.requiresApiKey)
        assertTrue(TranslationEngineType.ZHIPU.isLlm)

        // Both free channels must stay keyless, or the gate would block a session
        // the user was explicitly promised needs no key.
        assertFalse(TranslationEngineType.MICROSOFT.requiresApiKey)
        assertFalse(TranslationEngineType.MICROSOFT.isLlm)
        assertFalse(TranslationEngineType.GOOGLE_FREE.requiresApiKey)
        assertFalse(TranslationEngineType.GOOGLE_FREE.isLlm)

        // Gemini is an LLM reached through an OpenAI-compatible layer, so unlike the
        // plain translation APIs the thinking switch must stay live for it.
        assertTrue(TranslationEngineType.GEMINI.requiresApiKey)
        assertTrue(TranslationEngineType.GEMINI.isLlm)
    }

    @Test
    fun `stored values round-trip and unknown ones fall back to deepseek`() {
        TranslationEngineType.entries.forEach { type ->
            assertEquals(type, TranslationEngineType.fromStorage(type.name))
        }
        // Absent key (fresh install).
        assertEquals(TranslationEngineType.DEEPSEEK, TranslationEngineType.fromStorage(null))
        assertEquals(TranslationEngineType.DEEPSEEK, TranslationEngineType.fromStorage(""))
        assertEquals(TranslationEngineType.DEEPSEEK, TranslationEngineType.fromStorage("NOT_AN_ENGINE"))
        // Pre-existing installs stored the old two-value names — those must still load.
        assertEquals(TranslationEngineType.MICROSOFT, TranslationEngineType.fromStorage("MICROSOFT"))
    }

    @Test
    fun `a value from a removed engine falls back instead of crashing`() {
        // GOOGLE_API (Cloud Translation v2) shipped briefly and was then dropped. An
        // install that had it selected still has the name on disk; selecting an engine
        // must never throw, so it degrades to the default.
        assertEquals(TranslationEngineType.DEEPSEEK, TranslationEngineType.fromStorage("GOOGLE_API"))
    }
}
