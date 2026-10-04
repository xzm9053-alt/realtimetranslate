package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationEngineTypeTest {

    @Test
    fun `a key label exists exactly for the engines that show a key field`() {
        // The settings screen reads keyLabelRes without a null check on the branch where
        // showsApiKeyField is true, so a field without a label is a crash waiting to
        // happen; a label without a field is dead copy.
        TranslationEngineType.entries.forEach { type ->
            if (type.showsApiKeyField) {
                assertNotNull("$type shows a key field but has no label", type.keyLabelRes)
            } else {
                assertNull("$type has no key field but declares a label", type.keyLabelRes)
            }
        }
    }

    @Test
    fun `requiring a key implies showing the field, never the other way round`() {
        // The gate (requiresApiKey) and the UI (showsApiKeyField) used to be one flag.
        // They are separate now because of OPENAI_COMPAT: a local Ollama has no key to
        // give, so a session must start without one while the field stays on screen.
        // The implication holds in one direction only — that asymmetry is the feature.
        TranslationEngineType.entries.forEach { type ->
            if (type.requiresApiKey) {
                assertTrue("$type gates on a key it never asks for", type.showsApiKeyField)
            }
        }

        assertFalse(TranslationEngineType.OPENAI_COMPAT.requiresApiKey)
        assertTrue(TranslationEngineType.OPENAI_COMPAT.showsApiKeyField)
        assertEquals(ApiKeyRequirement.OPTIONAL, TranslationEngineType.OPENAI_COMPAT.apiKeyRequirement)
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

        // The user-supplied endpoint: an LLM (thinking switch live, model picker shown)
        // whose address and model are unknown at build time, hence no presets.
        assertFalse(TranslationEngineType.OPENAI_COMPAT.requiresApiKey)
        assertTrue(TranslationEngineType.OPENAI_COMPAT.isLlm)
        assertFalse(TranslationEngineType.OPENAI_COMPAT.hasPresetModels)
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
