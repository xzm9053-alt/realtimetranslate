package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `presets exist exactly for the engines that ship with them`() {
        // hasPresetModels, not isLlm: the generic engine is an LLM with nothing to ship,
        // because its models belong to a provider this app has never heard of — its
        // picker is filled from GET /models instead. Every other engine's flag must
        // still agree with its list, or the picker would show an empty dropdown.
        TranslationEngineType.entries.forEach { type ->
            val presets = ModelPresets.forEngine(type)
            if (type.hasPresetModels) {
                assertTrue("$type claims presets but ships none", presets.isNotEmpty())
            } else {
                assertTrue("$type ships no presets but lists some", presets.isEmpty())
            }
            // A non-LLM engine has no model to choose at all.
            if (!type.isLlm) {
                assertTrue("$type is not an LLM but lists presets", presets.isEmpty())
            }
        }

        assertTrue(ModelPresets.forEngine(TranslationEngineType.OPENAI_COMPAT).isEmpty())
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

    // ---- Which failures deserve another attempt. ----
    // The asymmetry is the point: retrying a client error wastes a round trip on a
    // request that cannot succeed, while not retrying an overloaded server throws away
    // a sentence that would have gone through on the next try.

    @Test
    fun `overload and rate limits are retryable`() {
        listOf(408, 429, 500, 502, 503, 504, 599).forEach {
            assertTrue("HTTP $it should be retryable", TransientFailures.isRetryable(it))
        }
    }

    @Test
    fun `client errors are not retryable`() {
        listOf(200, 400, 401, 402, 403, 404, 422).forEach {
            assertFalse("HTTP $it should not be retryable", TransientFailures.isRetryable(it))
        }
    }

    // ---- Reading the cooldown a provider asks for. ----

    @Test
    fun `google retry info duration is read out of the error body`() {
        // Gemini's compat layer sends no retry-after header; the wait is in the body.
        val body = """
            {"error":{"code":503,"message":"The model is overloaded.","status":"UNAVAILABLE",
            "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"1.5s"}]}}
        """.trimIndent()
        assertEquals(1500L, TransientFailures.retryDelayMillis(body))
        assertEquals(39_000L, TransientFailures.retryDelayMillis("""{"retryDelay":"39s"}"""))
    }

    @Test
    fun `an absent or unreadable cooldown reads as no hint`() {
        assertNull(TransientFailures.retryDelayMillis("""{"error":{"code":503}}"""))
        assertNull(TransientFailures.retryDelayMillis(""))
        // Bare seconds with no unit, a unit with no number, and a zero wait are all
        // "no usable hint" rather than a guess.
        assertNull(TransientFailures.retryDelayMillis("""{"retryDelay":"1.5"}"""))
        assertNull(TransientFailures.retryDelayMillis("""{"retryDelay":"s"}"""))
        assertNull(TransientFailures.retryDelayMillis("""{"retryDelay":"0s"}"""))
    }
}
