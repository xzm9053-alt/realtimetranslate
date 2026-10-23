package com.xzm.realtimetranslate.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModelCatalogTest {

    private val models = AsrModelCatalog.ALIYUN_REALTIME_MODELS
    private val ids = models.map { it.id }

    @Test
    fun `Aliyun realtime ASR models list is populated and unique`() {
        assertTrue("Aliyun models list should not be empty", models.isNotEmpty())
        assertEquals("Aliyun model IDs must be unique", ids.size, ids.distinct().size)

        assertTrue("fun-asr-realtime must be in list", ids.contains("fun-asr-realtime"))
        assertTrue("paraformer-realtime-v2 must be in list", ids.contains("paraformer-realtime-v2"))

        val recommended = models.filter { it.recommended }.map { it.id }
        assertTrue(recommended.contains("fun-asr-realtime"))
        assertTrue(recommended.contains("qwen-audio-3.1-asr-flash-streaming"))
        assertTrue(recommended.contains("qwen3-asr-flash-realtime"))
    }

    /**
     * Bailian retired these. Keeping them in the picker is exactly what made users see
     * "connects, then fails" on models that no longer exist.
     */
    @Test
    fun `retired Aliyun models are not offered`() {
        listOf(
            "paraformer-realtime-v1",
            "paraformer-realtime-dialect-v1",
            "paraformer-realtime-8k-v1",
            "conformer-realtime-v1",
            "conformer-realtime-8k-v1",
        ).forEach { retired ->
            assertFalse("$retired was retired by Bailian", ids.contains(retired))
        }
    }

    /** Chinese-only phone models cannot serve a translation app. */
    @Test
    fun `Chinese-only models are not offered`() {
        listOf(
            "paraformer-realtime-8k-v2",
            "fun-asr-flash-8k-realtime",
            "fun-asr-flash-8k-realtime-2026-01-28",
        ).forEach { chineseOnly ->
            assertFalse("$chineseOnly only recognises Chinese", ids.contains(chineseOnly))
        }
    }

    /** Recording-file models are not streaming and cannot feed live translation. */
    @Test
    fun `non-streaming transcription models are not offered`() {
        listOf(
            "fun-asr",
            "fun-asr-flash-2026-06-15",
            "paraformer-v2",
            "paraformer-8k-v2",
            "qwen3-asr-flash-filetrans",
            "qwen3-asr-flash",
        ).forEach { fileOnly ->
            assertFalse("$fileOnly is a recording-file model", ids.contains(fileOnly))
        }
    }

    @Test
    fun `every model declares a group so the picker can nest`() {
        models.forEach { model ->
            assertTrue("${model.id} needs a group", model.group.isNotBlank())
        }
        assertEquals(
            "Groups must cover every model exactly once",
            models.size,
            AsrModelCatalog.ALIYUN_GROUPS.sumOf { it.second.size },
        )
    }

    @Test
    fun `Qwen3 realtime is offered through the OpenAI Realtime protocol`() {
        val preset = AsrModelCatalog.aliyunPreset("qwen3-asr-flash-realtime")
        assertTrue("qwen3-asr-flash-realtime must be offered", preset != null)
        assertEquals(AsrProtocol.OPENAI_REALTIME, preset!!.protocol)
        assertEquals(AsrProtocol.OPENAI_REALTIME, AsrModelCatalog.protocolFor(preset.id))

        models.filter { it.id != "qwen3-asr-flash-realtime" }.forEach {
            assertEquals("${it.id} speaks run-task", AsrProtocol.RUN_TASK, it.protocol)
        }
    }

    @Test
    fun `catalog carries the month it was last verified`() {
        assertTrue(AsrModelCatalog.CATALOG_LAST_VERIFIED.isNotBlank())
    }

    /**
     * `-message` models are the short-audio HTTP API. They never answer on the
     * run-task WebSocket, so offering one means "connects, then fails".
     */
    @Test
    fun `short audio message models are not offered`() {
        assertTrue(
            "Short-audio HTTP models cannot stream: ${ids.filter { it.endsWith("-message") }}",
            ids.none { it.endsWith("-message") },
        )
    }

    @Test
    fun `Generic OpenAI compatible ASR models list is populated and unique`() {
        val generic = AsrModelCatalog.GENERIC_OPENAI_MODELS
        assertTrue("Generic models list should not be empty", generic.isNotEmpty())

        val genericIds = generic.map { it.id }
        assertEquals("Generic model IDs must be unique", genericIds.size, genericIds.distinct().size)

        assertTrue(genericIds.contains("FunAudioLLM/SenseVoiceSmall"))
        assertTrue(genericIds.contains("openai/whisper-large-v3"))
        assertTrue(genericIds.contains("openai/whisper-large-v3-turbo"))
    }
}
