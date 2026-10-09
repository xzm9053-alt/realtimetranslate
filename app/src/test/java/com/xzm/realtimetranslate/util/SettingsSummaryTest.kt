package com.xzm.realtimetranslate.util

import com.xzm.realtimetranslate.data.AudioSourceMode
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report this summary ends up in is a file the user forwards to a stranger, so the
 * only test that really matters is that no credential survives the trip. The rest pins
 * that the *active* engine's model and URL are the ones reported — a summary that lists
 * the DeepSeek model while the app is talking to Zhipu would send a diagnosis down the
 * wrong path entirely.
 */
class SettingsSummaryTest {

    @Test
    fun `the hugging face token is never printed, only whether one is set`() {
        val secret = "hf_" + "a".repeat(30)
        val withToken = SettingsSummary.build(UserSettings(huggingfaceToken = secret))

        assertFalse(withToken.contains(secret))
        assertTrue(withToken.contains("huggingfaceToken: 已设置"))
        assertTrue(SettingsSummary.build(UserSettings()).contains("huggingfaceToken: 未设置"))
    }

    @Test
    fun `a key carried in the base url is redacted`() {
        val out = SettingsSummary.build(
            UserSettings(
                translationEngine = TranslationEngineType.OPENAI_COMPAT,
                genericBaseUrl = "https://relay.example/v1?key=SECRET123&x=1",
            )
        )

        assertFalse(out.contains("SECRET123"))
        assertTrue(out.contains("?key=***"))
    }

    @Test
    fun `the active engine decides which model and url are reported`() {
        val zhipu = UserSettings(translationEngine = TranslationEngineType.ZHIPU)
        assertEquals(UserSettings.Defaults.ZHIPU_MODEL, SettingsSummary.modelOf(zhipu))
        assertEquals(UserSettings.Defaults.ZHIPU_BASE_URL, SettingsSummary.baseUrlOf(zhipu))

        val deepseek = UserSettings(translationEngine = TranslationEngineType.DEEPSEEK)
        assertEquals(UserSettings.Defaults.DEEPSEEK_MODEL, SettingsSummary.modelOf(deepseek))
        assertEquals(UserSettings.Defaults.DEEPSEEK_BASE_URL, SettingsSummary.baseUrlOf(deepseek))
    }

    @Test
    fun `an unconfigured generic engine says so rather than reporting a blank`() {
        // Blank is the whole point of the generic slot: it has no ship-time default, and
        // an empty line in the report reads as a rendering bug rather than a missing setup.
        val out = SettingsSummary.build(
            UserSettings(translationEngine = TranslationEngineType.OPENAI_COMPAT)
        )

        assertTrue(out.contains("(未设置)"))
        assertTrue(out.contains("thinkingOffStyle"))
    }

    @Test
    fun `engines with no model of their own do not borrow someone else's`() {
        val microsoft = UserSettings(translationEngine = TranslationEngineType.MICROSOFT)
        assertEquals("(不适用)", SettingsSummary.modelOf(microsoft))
        assertEquals("(内置)", SettingsSummary.baseUrlOf(microsoft))
    }

    @Test
    fun `the summary reports the languages and audio source in use`() {
        val out = SettingsSummary.build(
            UserSettings(
                sourceLanguageCode = "ja",
                targetLanguageCode = "zh-Hans",
                audioSourceMode = AudioSourceMode.MEDIA_AND_MIC,
            )
        )

        assertTrue(out.contains("source=ja"))
        assertTrue(out.contains("target=zh-Hans"))
        assertTrue(out.contains("audioSource: MEDIA_AND_MIC"))
    }
}
