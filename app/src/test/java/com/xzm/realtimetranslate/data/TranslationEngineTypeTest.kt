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

        assertTrue(TranslationEngineType.GOOGLE_API.requiresApiKey)
        // Cloud Translation is a plain API — the thinking switch must stay greyed out.
        assertFalse(TranslationEngineType.GOOGLE_API.isLlm)
    }

    @Test
    fun `stored values round-trip and unknown ones fall back to deepseek`() {
        TranslationEngineType.entries.forEach { type ->
            assertEquals(type, TranslationEngineType.fromStorage(type.name))
        }
        // Absent key (fresh install) and stale values from a removed engine.
        assertEquals(TranslationEngineType.DEEPSEEK, TranslationEngineType.fromStorage(null))
        assertEquals(TranslationEngineType.DEEPSEEK, TranslationEngineType.fromStorage(""))
        assertEquals(TranslationEngineType.DEEPSEEK, TranslationEngineType.fromStorage("GEMINI"))
        // Pre-existing installs stored the old two-value names — those must still load.
        assertEquals(TranslationEngineType.MICROSOFT, TranslationEngineType.fromStorage("MICROSOFT"))
    }
}
