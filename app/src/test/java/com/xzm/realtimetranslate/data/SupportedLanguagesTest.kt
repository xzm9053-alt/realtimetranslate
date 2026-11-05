package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLanguagesTest {

    @Test
    fun testFilterForParaformerAndFireRedAsr() {
        val paraformer = LocalAsrModelCatalog.getPresetById("paraformer-zh")
        val options = SupportedLanguages.filterSourceOptionsForModel(paraformer)
        val codes = options.map { it.code }

        // 仅包含简体和繁体中文，不包含 auto 和其他外语
        assertTrue(codes.contains("zh-Hans"))
        assertTrue(codes.contains("zh-Hant"))
        assertFalse(codes.contains("auto"))
        assertFalse(codes.contains("en"))
        assertFalse(codes.contains("ja"))
        assertEquals(2, codes.size)

        val fireRed = LocalAsrModelCatalog.getPresetById("fire-red-asr")
        val fireRedOptions = SupportedLanguages.filterSourceOptionsForModel(fireRed)
        val fireRedCodes = fireRedOptions.map { it.code }
        assertTrue(fireRedCodes.contains("zh-Hans"))
        assertTrue(fireRedCodes.contains("zh-Hant"))
        assertFalse(fireRedCodes.contains("auto"))
        assertFalse(fireRedCodes.contains("ja"))
    }

    @Test
    fun testFilterForMoonshine() {
        val moonshine = LocalAsrModelCatalog.getPresetById("moonshine-tiny")
        val options = SupportedLanguages.filterSourceOptionsForModel(moonshine)
        val codes = options.map { it.code }

        // 纯英文，仅包含 en
        assertEquals(listOf("en"), codes)
    }

    @Test
    fun testFilterForSenseVoice() {
        val senseVoice = LocalAsrModelCatalog.getPresetById("sensevoice-small")
        val options = SupportedLanguages.filterSourceOptionsForModel(senseVoice)
        val codes = options.map { it.code }

        // 支持 auto, zh(简/繁), en, ja, ko
        assertTrue(codes.contains("auto"))
        assertTrue(codes.contains("zh-Hans"))
        assertTrue(codes.contains("zh-Hant"))
        assertTrue(codes.contains("en"))
        assertTrue(codes.contains("ja"))
        assertTrue(codes.contains("ko"))
        assertFalse(codes.contains("es"))
        assertFalse(codes.contains("fr"))
    }

    @Test
    fun testFilterForWhisperAndQwen3() {
        val whisperTurbo = LocalAsrModelCatalog.getPresetById("whisper-large-v3-turbo")
        val options = SupportedLanguages.filterSourceOptionsForModel(whisperTurbo)
        val codes = options.map { it.code }

        // 全球全语种支持
        assertTrue(codes.contains("auto"))
        assertTrue(codes.contains("es"))
        assertTrue(codes.contains("ru"))
        assertEquals(SupportedLanguages.sourceOptions.size, options.size)

        val qwen3 = LocalAsrModelCatalog.getPresetById("qwen3-asr-0.6b")
        val qwen3Options = SupportedLanguages.filterSourceOptionsForModel(qwen3)
        assertEquals(SupportedLanguages.sourceOptions.size, qwen3Options.size)
    }

    @Test
    fun testFilterByAsrEngineType() {
        // Aliyun FunASR: 支持 auto, zh(简/繁), en
        val aliyunOpts = SupportedLanguages.filterSourceOptions(AsrEngineType.ALIYUN_FUN_ASR, null).map { it.code }
        assertTrue(aliyunOpts.contains("auto"))
        assertTrue(aliyunOpts.contains("zh-Hans"))
        assertTrue(aliyunOpts.contains("zh-Hant"))
        assertTrue(aliyunOpts.contains("en"))
        assertFalse(aliyunOpts.contains("ja"))
        assertFalse(aliyunOpts.contains("de"))

        // 通用云端 OpenAI 兼容 (Whisper): 全语种
        val cloudOpts = SupportedLanguages.filterSourceOptions(AsrEngineType.GENERIC_OPENAI, null)
        assertEquals(SupportedLanguages.sourceOptions.size, cloudOpts.size)
    }

    @Test
    fun testSanitizeSourceLanguage() {
        val paraformerOpts = SupportedLanguages.filterSourceOptionsForModel(
            LocalAsrModelCatalog.getPresetById("paraformer-zh")
        )
        // 原选日语 ja，在 Paraformer 下自动纠正为 zh-Hans
        assertEquals("zh-Hans", SupportedLanguages.sanitizeSourceLanguage("ja", paraformerOpts))

        // 原选繁体中文 zh-Hant，在 Paraformer 下合法保留
        assertEquals("zh-Hant", SupportedLanguages.sanitizeSourceLanguage("zh-Hant", paraformerOpts))

        val senseVoiceOpts = SupportedLanguages.filterSourceOptionsForModel(
            LocalAsrModelCatalog.getPresetById("sensevoice-small")
        )
        // 原选西班牙语 es，在 SenseVoice 下自动纠正为 auto
        assertEquals("auto", SupportedLanguages.sanitizeSourceLanguage("es", senseVoiceOpts))
    }
}
