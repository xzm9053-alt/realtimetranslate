package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model picker's judgement calls: what an HTTP status means for a model, which
 * provider-listed models are worth showing, and the preset lists themselves.
 *
 * All of this is pure logic on purpose — it is the part that decides whether the user
 * is told "your model was retired" or "your key is wrong", and that call has to be
 * right without a network.
 */
class ModelCatalogTest {

    // ---- Status code → availability. This is the whole feature's judgement. ----

    @Test
    fun `a 2xx means the model is usable`() {
        assertEquals(ModelAvailability.AVAILABLE, ModelAvailability.fromHttp(200, ""))
    }

    @Test
    fun `a 404 means the model is gone`() {
        // Verbatim from the error that started this: Google retires a model for new
        // users and answers 404 while still listing it in /models.
        val body = """{"error":{"code":404,"message":"This model models/gemini-2.5-flash """ +
            """is no longer available to new users."}}"""
        assertEquals(ModelAvailability.RETIRED, ModelAvailability.fromHttp(404, body))
    }

    @Test
    fun `a rate limit is never reported as a delisting`() {
        // Quota exhaustion happens to everyone and must not tell the user their model
        // was retired — they would go change a setting that was never the problem.
        assertEquals(ModelAvailability.RATE_LIMITED, ModelAvailability.fromHttp(429, "quota exceeded"))
    }

    @Test
    fun `credential and billing failures are told apart from delisting`() {
        assertEquals(ModelAvailability.BAD_KEY, ModelAvailability.fromHttp(401, "invalid api key"))
        assertEquals(ModelAvailability.BAD_KEY, ModelAvailability.fromHttp(403, "permission denied"))
        assertEquals(ModelAvailability.NO_BALANCE, ModelAvailability.fromHttp(402, "insufficient balance"))
    }

    @Test
    fun `a 400 only counts as delisting when the body blames the model`() {
        assertEquals(
            ModelAvailability.RETIRED,
            ModelAvailability.fromHttp(400, """{"error":{"code":"1210","message":"模型不存在"}}"""),
        )
        assertEquals(
            ModelAvailability.RETIRED,
            ModelAvailability.fromHttp(400, """{"error":{"code":"model_not_found"}}"""),
        )
        // An unrelated 400 (a bad parameter) must not read as "this model is gone".
        assertEquals(
            ModelAvailability.UNKNOWN,
            ModelAvailability.fromHttp(400, """{"error":{"message":"max_tokens too large"}}"""),
        )
    }

    @Test
    fun `a server error is unknown rather than delisted`() {
        assertEquals(ModelAvailability.UNKNOWN, ModelAvailability.fromHttp(500, "internal error"))
        assertEquals(ModelAvailability.UNKNOWN, ModelAvailability.fromHttp(503, "unavailable"))
    }

    // ---- Which provider-listed models belong in a translation app's picker. ----

    @Test
    fun `non-chat models are filtered out of a discovered list`() {
        assertTrue(ModelListFilter.isChatModel("gemini-3.8-flash"))
        assertTrue(ModelListFilter.isChatModel("glm-4-flash"))
        assertTrue(ModelListFilter.isChatModel("deepseek-v4-pro"))

        assertFalse(ModelListFilter.isChatModel("gemini-3.8-live"))
        assertFalse(ModelListFilter.isChatModel("gemini-3-pro-image"))
        assertFalse(ModelListFilter.isChatModel("text-embedding-3-large"))
        assertFalse(ModelListFilter.isChatModel("cogview-4"))
        assertFalse(ModelListFilter.isChatModel("cogvideox-2"))
        assertFalse(ModelListFilter.isChatModel(""))
    }

    // ---- The preset lists. ----

    @Test
    fun `every LLM engine offers presets and no keyless engine does`() {
        TranslationEngineType.entries.forEach { type ->
            if (type.isLlm) {
                assertTrue("$type is an LLM but has no presets", ModelPresets.forEngine(type).isNotEmpty())
            } else {
                assertTrue("$type has no model to choose but lists presets", ModelPresets.forEngine(type).isEmpty())
            }
        }
    }

    @Test
    fun `each engine's shipped default is one of its own presets`() {
        // Guarantees the factory default is always reachable from the picker: a default
        // that exists only in code and nowhere in the UI is a dead end for the user.
        assertTrue(
            ModelPresets.forEngine(TranslationEngineType.DEEPSEEK)
                .contains(UserSettings.Defaults.DEEPSEEK_MODEL),
        )
        assertTrue(
            ModelPresets.forEngine(TranslationEngineType.ZHIPU)
                .contains(UserSettings.Defaults.ZHIPU_MODEL),
        )
        assertTrue(
            ModelPresets.forEngine(TranslationEngineType.GEMINI)
                .contains(UserSettings.Defaults.GEMINI_MODEL),
        )
    }

    @Test
    fun `the retired gemini model is gone from the presets`() {
        // Shipped as the default until Google retired it for new API users.
        assertFalse(
            ModelPresets.forEngine(TranslationEngineType.GEMINI).contains("gemini-2.5-flash"),
        )
    }

    // ---- Merging what we ship with what the provider reports. ----

    @Test
    fun `presets come first and discovered models are appended without duplicates`() {
        val merged = ModelPresets.visible(
            TranslationEngineType.DEEPSEEK,
            listOf("deepseek-v4-pro", "deepseek-v5-flash", "cogview-4", "deepseek-v4-flash", ""),
        )
        assertEquals(
            listOf("deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v5-flash"),
            merged,
        )
    }

    @Test
    fun `a failed discovery leaves the presets untouched`() {
        assertEquals(
            ModelPresets.forEngine(TranslationEngineType.GEMINI),
            ModelPresets.visible(TranslationEngineType.GEMINI, emptyList()),
        )
    }
}
