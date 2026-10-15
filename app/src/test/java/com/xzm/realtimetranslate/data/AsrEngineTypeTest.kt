package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrEngineTypeTest {

    @Test
    fun `fromStorage returns correct enum or falls back to LOCAL_SENSEVOICE`() {
        assertEquals(AsrEngineType.LOCAL_SENSEVOICE, AsrEngineType.fromStorage("LOCAL_SENSEVOICE"))
        assertEquals(AsrEngineType.ALIYUN_FUN_ASR, AsrEngineType.fromStorage("ALIYUN_FUN_ASR"))
        assertEquals(AsrEngineType.GENERIC_OPENAI, AsrEngineType.fromStorage("GENERIC_OPENAI"))

        // Fallbacks for unknown or null names
        assertEquals(AsrEngineType.LOCAL_SENSEVOICE, AsrEngineType.fromStorage(null))
        assertEquals(AsrEngineType.LOCAL_SENSEVOICE, AsrEngineType.fromStorage(""))
        assertEquals(AsrEngineType.LOCAL_SENSEVOICE, AsrEngineType.fromStorage("NON_EXISTENT_ASR"))
    }

    @Test
    fun `capability matrix matches the ASR engines as shipped`() {
        // Local SenseVoice: offline, no key required, not cloud
        assertFalse(AsrEngineType.LOCAL_SENSEVOICE.requiresApiKey)
        assertFalse(AsrEngineType.LOCAL_SENSEVOICE.showsApiKeyField)
        assertFalse(AsrEngineType.LOCAL_SENSEVOICE.isCloud)
        assertFalse(AsrEngineType.LOCAL_SENSEVOICE.isStreaming)

        // Aliyun FunASR: cloud WebSocket streaming, requires API key
        assertTrue(AsrEngineType.ALIYUN_FUN_ASR.requiresApiKey)
        assertTrue(AsrEngineType.ALIYUN_FUN_ASR.showsApiKeyField)
        assertTrue(AsrEngineType.ALIYUN_FUN_ASR.isCloud)
        assertTrue(AsrEngineType.ALIYUN_FUN_ASR.isStreaming)

        // Generic OpenAI: cloud HTTP, optional API key
        assertFalse(AsrEngineType.GENERIC_OPENAI.requiresApiKey)
        assertTrue(AsrEngineType.GENERIC_OPENAI.showsApiKeyField)
        assertTrue(AsrEngineType.GENERIC_OPENAI.isCloud)
        assertFalse(AsrEngineType.GENERIC_OPENAI.isStreaming)
    }

    @Test
    fun `requiring a key implies showing the field`() {
        AsrEngineType.entries.forEach { type ->
            if (type.requiresApiKey) {
                assertTrue("$type requires a key but hides the field", type.showsApiKeyField)
            }
        }
    }
}
