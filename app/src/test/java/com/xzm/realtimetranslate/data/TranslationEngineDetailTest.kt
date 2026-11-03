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
    fun testOpenAiCompatHasRecommendedModels() {
        val detail = TranslationEngineCatalog.getDetail(TranslationEngineType.OPENAI_COMPAT)
        assertTrue("OpenAI Compat should recommend top open-source models", detail.recommendedModels.isNotEmpty())
        assertTrue("Should include SakuraLLM", detail.recommendedModels.any { it.contains("Sakura") })
        assertTrue("Should include Seed-X", detail.recommendedModels.any { it.contains("Seed-X") })
    }
}
