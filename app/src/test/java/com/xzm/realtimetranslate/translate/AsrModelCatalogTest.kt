package com.xzm.realtimetranslate.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModelCatalogTest {

    @Test
    fun `Aliyun realtime ASR models list is populated and unique`() {
        val models = AsrModelCatalog.ALIYUN_REALTIME_MODELS
        assertTrue("Aliyun models list should not be empty", models.isNotEmpty())

        val ids = models.map { it.id }
        assertEquals("Aliyun model IDs must be unique", ids.size, ids.distinct().size)

        // Core models must be present
        assertTrue("fun-asr-realtime must be in list", ids.contains("fun-asr-realtime"))
        assertTrue("paraformer-realtime-v2 must be in list", ids.contains("paraformer-realtime-v2"))
        assertTrue("paraformer-realtime-dialect-v1 must be in list", ids.contains("paraformer-realtime-dialect-v1"))

        // Check recommended flag
        val recommended = models.filter { it.recommended }.map { it.id }
        assertTrue(recommended.contains("fun-asr-realtime"))
        assertTrue(recommended.contains("paraformer-realtime-v2"))
    }

    @Test
    fun `Generic OpenAI compatible ASR models list is populated and unique`() {
        val models = AsrModelCatalog.GENERIC_OPENAI_MODELS
        assertTrue("Generic models list should not be empty", models.isNotEmpty())

        val ids = models.map { it.id }
        assertEquals("Generic model IDs must be unique", ids.size, ids.distinct().size)

        assertTrue(ids.contains("FunAudioLLM/SenseVoiceSmall"))
        assertTrue(ids.contains("openai/whisper-large-v3"))
        assertTrue(ids.contains("openai/whisper-large-v3-turbo"))
    }
}
