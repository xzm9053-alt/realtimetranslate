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
        assertEquals(4, openTrans.size)
        assertEquals(3, paidTrans.size)
        assertEquals(7, openTrans.size + paidTrans.size)

        assertTrue(openTrans.contains(TranslationEngineType.OPENAI_COMPAT))
        assertTrue(openTrans.contains(TranslationEngineType.MICROSOFT))
        assertTrue(openTrans.contains(TranslationEngineType.GOOGLE_FREE))
        assertTrue(openTrans.contains(TranslationEngineType.NONE))

        assertTrue(paidTrans.contains(TranslationEngineType.DEEPSEEK))
        assertTrue(paidTrans.contains(TranslationEngineType.ZHIPU))
        assertTrue(paidTrans.contains(TranslationEngineType.GEMINI))

        val openAsr = TranslationEngineCatalog.getAsrEnginesByCategory(EngineCategory.OPEN_SOURCE)
        val paidAsr = TranslationEngineCatalog.getAsrEnginesByCategory(EngineCategory.API_PAID)
        assertEquals(2, openAsr.size)
        assertEquals(1, paidAsr.size)
        assertTrue(openAsr.contains(AsrEngineType.LOCAL_SENSEVOICE))
        assertTrue(openAsr.contains(AsrEngineType.GENERIC_OPENAI))
        assertTrue(paidAsr.contains(AsrEngineType.ALIYUN_FUN_ASR))
    }

    @Test
    fun testOpenAiCompatHasRecommendedModels() {
        val detail = TranslationEngineCatalog.getDetail(TranslationEngineType.OPENAI_COMPAT)
        assertTrue("OpenAI Compat should recommend top open-source models", detail.recommendedModels.isNotEmpty())
        assertTrue("Should include SakuraLLM", detail.recommendedModels.any { it.contains("Sakura") })
        assertTrue("Should include Seed-X", detail.recommendedModels.any { it.contains("Seed-X") })
    }
}
