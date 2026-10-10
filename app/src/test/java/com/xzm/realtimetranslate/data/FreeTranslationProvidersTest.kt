package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The presets are hand-written data pointing at third-party services, so the value of
 * these tests is catching the ways that data goes wrong silently: a typo'd endpoint, a
 * copy-paste duplicate, or a preset that quietly contradicts the engine it runs on.
 */
class FreeTranslationProvidersTest {

    private val presets = FreeTranslationProviders.all

    @Test
    fun `every preset is describable in the UI and points at a real page`() {
        assertTrue("the card would render empty", presets.isNotEmpty())
        presets.forEach { p ->
            assertNotEquals("$p has no name string", 0, p.nameRes)
            assertNotEquals("$p has no quota string", 0, p.quotaRes)
            assertNotEquals("$p has no console label", 0, p.consoleNameRes)
            assertTrue("${p.consoleUrl} must be https", p.consoleUrl.startsWith("https://"))
            assertTrue("$p has no model to select", p.model.isNotBlank())
        }
    }

    @Test
    fun `generic-engine presets carry a complete endpoint, since that engine has no default`() {
        // TranslationEngineFactory.genericConfig deliberately has no `ifBlank { default }`:
        // a blank URL there is a hard error rather than a quiet fallback. So for this
        // engine the preset must supply the address itself — that is the entire point.
        val generic = presets.filter { it.engine == TranslationEngineType.OPENAI_COMPAT }
        assertTrue("no preset for the generic engine", generic.isNotEmpty())

        generic.forEach { p ->
            assertTrue("${p.nameRes} would configure a blank endpoint", p.baseUrl.isNotBlank())
            assertTrue("${p.baseUrl} must be https", p.baseUrl.startsWith("https://"))
            assertFalse("${p.baseUrl} has a trailing slash", p.baseUrl.endsWith("/"))
            // Pasting the full endpoint out of a provider's docs is the common mistake; a
            // preset has no excuse for encoding it, since the base class appends the path.
            assertFalse(
                "${p.baseUrl} already includes /chat/completions",
                p.baseUrl.contains("/chat/completions"),
            )
        }
    }

    @Test
    fun `the zhipu preset leaves the endpoint alone and agrees with the engine default`() {
        // A blank endpoint means "use the engine's own default". Copying the default into
        // the preset instead would let the two drift apart the day one of them changes.
        val zhipu = presets.filter { it.engine == TranslationEngineType.ZHIPU }
        assertTrue("no preset for zhipu", zhipu.isNotEmpty())

        zhipu.forEach { p ->
            assertEquals("${p.nameRes} would pin an endpoint the engine already has", "", p.baseUrl)
            // Tapping the row writes this model into the Zhipu model field. If it stopped
            // matching the built-in default, the "free and already selected" promise in
            // the card's copy would be false.
            assertEquals(UserSettings.Defaults.ZHIPU_MODEL, p.model)
        }
    }

    @Test
    fun `no two presets describe the same engine, endpoint and model`() {
        // A duplicate is invisible in the UI — two rows that configure the same thing —
        // and would mean one of them was meant to point somewhere else.
        val keys = presets.map { Triple(it.engine, normalize(it.baseUrl), it.model) }
        assertEquals("duplicate preset: $keys", keys.size, keys.distinct().size)
    }

    @Test
    fun `the sign-up guide shows one button per page`() {
        val pages = FreeTranslationProviders.consolePages
        assertTrue("the guide would have no links", pages.isNotEmpty())
        assertEquals(pages.size, pages.map { it.consoleUrl }.distinct().size)
        assertTrue(pages.all { it in presets })
    }

    @Test
    fun `isActiveIn marks exactly the provider that is configured`() {
        // Defaults: DeepSeek, nothing free selected.
        presets.forEach { assertFalse("$it", it.isActiveIn(UserSettings())) }

        val zhipu = UserSettings(translationEngine = TranslationEngineType.ZHIPU)
        presets.forEach { p ->
            assertEquals(
                "under Zhipu only the Zhipu preset is live",
                p.engine == TranslationEngineType.ZHIPU,
                p.isActiveIn(zhipu),
            )
        }

        val siliconFlow = UserSettings(
            translationEngine = TranslationEngineType.OPENAI_COMPAT,
            genericBaseUrl = "https://api.siliconflow.cn/v1",
            genericModel = "Qwen/Qwen3-8B",
        )
        val active = presets.filter { it.isActiveIn(siliconFlow) }
        assertEquals(1, active.size)
        assertEquals("Qwen/Qwen3-8B", active.single().model)
    }

    @Test
    fun `isActiveIn forgives how the user spelled the endpoint`() {
        // The factory trims and strips these before use, so a preset whose URL was pasted
        // in full is genuinely the same configuration — showing no tick would just look
        // like the tap failed.
        val qwen = presets.single { it.model == "Qwen/Qwen3-8B" }
        listOf(
            "https://api.siliconflow.cn/v1",
            " https://api.siliconflow.cn/v1 ",
            "https://api.siliconflow.cn/v1/",
            "https://api.siliconflow.cn/v1/chat/completions",
        ).forEach { written ->
            assertTrue(
                "a preset configured as '$written' should still read as active",
                qwen.isActiveIn(
                    UserSettings(
                        translationEngine = TranslationEngineType.OPENAI_COMPAT,
                        genericBaseUrl = written,
                        genericModel = "Qwen/Qwen3-8B",
                    ),
                ),
            )
        }
    }

    private fun normalize(url: String): String =
        url.trim().trimEnd('/').removeSuffix("/chat/completions").trimEnd('/')
}
