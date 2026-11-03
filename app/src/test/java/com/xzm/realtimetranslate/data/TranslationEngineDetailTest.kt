package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationEngineDetailTest {

    @Test
    fun testAllTranslationEnginesCovered() {
        for (engine in TranslationEngineType.entries) {
            val detail = TranslationEngineCatalog.getDetail(engine)
            assertNotNull("Detail for engine $engine must not be null", detail)
            assertEquals(engine, detail.type)
            assertTrue("iconEmoji must not be blank for $engine", detail.iconEmoji.isNotBlank())
            assertTrue("categoryBadge must not be blank for $engine", detail.categoryBadge.isNotBlank())
            assertTrue("developer must not be blank for $engine", detail.developer.isNotBlank())
            assertTrue("highlight must not be blank for $engine", detail.highlight.isNotBlank())
            assertTrue("costDesc must not be blank for $engine", detail.costDesc.isNotBlank())
            assertTrue("suitableScenarios must not be blank for $engine", detail.suitableScenarios.isNotBlank())
            assertTrue("detailText must not be blank for $engine", detail.detailText.isNotBlank())
            assertTrue("brandColorLong must have non-zero alpha", (detail.brandColorLong and 0xFF000000L) != 0L)
        }
    }

    @Test
    fun testAllAsrEnginesCovered() {
        for (engine in AsrEngineType.entries) {
            val detail = TranslationEngineCatalog.getAsrDetail(engine)
            assertNotNull("Detail for ASR engine $engine must not be null", detail)
            assertEquals(engine, detail.type)
            assertTrue("iconEmoji must not be blank for $engine", detail.iconEmoji.isNotBlank())
            assertTrue("categoryBadge must not be blank for $engine", detail.categoryBadge.isNotBlank())
            assertTrue("highlight must not be blank for $engine", detail.highlight.isNotBlank())
            assertTrue("costDesc must not be blank for $engine", detail.costDesc.isNotBlank())
            assertTrue("suitableScenarios must not be blank for $engine", detail.suitableScenarios.isNotBlank())
            assertTrue("detailText must not be blank for $engine", detail.detailText.isNotBlank())
            assertTrue("brandColorLong must have non-zero alpha", (detail.brandColorLong and 0xFF000000L) != 0L)
        }
    }

    @Test
    fun testCategoriesPartition() {
        val openTrans = TranslationEngineCatalog.getTranslationEnginesByCategory(EngineCategory.OPEN_SOURCE)
        val paidTrans = TranslationEngineCatalog.getTranslationEnginesByCategory(EngineCategory.API_PAID)
        assertEquals(3, openTrans.size)
        assertEquals(4, paidTrans.size)
        assertEquals(7, openTrans.size + paidTrans.size)

        assertTrue(openTrans.contains(TranslationEngineType.MICROSOFT))
        assertTrue(openTrans.contains(TranslationEngineType.GOOGLE_FREE))
        assertTrue(openTrans.contains(TranslationEngineType.NONE))

        assertTrue(paidTrans.contains(TranslationEngineType.DEEPSEEK))
        assertTrue(paidTrans.contains(TranslationEngineType.ZHIPU))
        assertTrue(paidTrans.contains(TranslationEngineType.GEMINI))
        assertTrue(paidTrans.contains(TranslationEngineType.OPENAI_COMPAT))

        val openAsr = TranslationEngineCatalog.getAsrEnginesByCategory(EngineCategory.OPEN_SOURCE)
        val paidAsr = TranslationEngineCatalog.getAsrEnginesByCategory(EngineCategory.API_PAID)
        assertEquals(1, openAsr.size)
        assertEquals(2, paidAsr.size)
        assertTrue(openAsr.contains(AsrEngineType.LOCAL_SENSEVOICE))
        assertTrue(paidAsr.contains(AsrEngineType.ALIYUN_FUN_ASR))
        assertTrue(paidAsr.contains(AsrEngineType.GENERIC_OPENAI))
    }

    @Test
    fun testOpenSourceTranslationPresets() {
        val presets = TranslationEngineCatalog.OPEN_SOURCE_TRANSLATION_PRESETS
        assertEquals(7, presets.size)
        for (p in presets) {
            assertTrue(p.id.isNotBlank())
            assertTrue(p.name.isNotBlank())
            assertTrue(p.iconEmoji.isNotBlank())
            assertTrue(p.highlight.isNotBlank())
            assertTrue(p.detailText.isNotBlank())
        }
    }

    @Test
    fun testOpenAiCompatHasRecommendedModels() {
        val detail = TranslationEngineCatalog.getDetail(TranslationEngineType.OPENAI_COMPAT)
        assertTrue("OpenAI Compat should recommend top models", detail.recommendedModels.isNotEmpty())
        assertTrue("Should include gpt-4o-mini", detail.recommendedModels.any { it.contains("gpt-4o-mini") })
        assertTrue("Should include deepseek-chat", detail.recommendedModels.any { it.contains("deepseek-chat") })
    }
}
