package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.data.TranslationEngineType

/**
 * Whether a model can actually be called right now.
 *
 * The distinction that carries the feature is [RETIRED] versus everything else. A model
 * the provider no longer serves is the user's problem to fix; a bad key, an empty
 * balance or a rate limit are temporary and unrelated, and reporting any of them as a
 * delisting would send the user off to change a setting that was never wrong.
 */
enum class ModelAvailability {
    AVAILABLE,
    RETIRED,
    BAD_KEY,
    NO_BALANCE,
    RATE_LIMITED,
    UNKNOWN,
    UNREACHABLE,
    ;

    companion object {
        /** Ways providers say the *model*, not the request or the key, is the problem. */
        private val MODEL_MISSING_MARKERS = listOf(
            "model_not_found",
            "no longer available",
            "not exist",
            "does not exist",
            "unknown model",
            "invalid model",
            "model not found",
            "不存在的模型",
            "模型不存在",
        )

        fun fromHttp(code: Int, body: String): ModelAvailability = when {
            code in 200..299 -> AVAILABLE
            code == 401 || code == 403 -> BAD_KEY
            code == 402 -> NO_BALANCE
            code == 404 -> RETIRED
            code == 429 -> RATE_LIMITED
            // Some providers answer 400 rather than 404 for an unknown model, so the body
            // has to be read — but only a body that names the model counts. Anything else
            // (a rejected parameter, say) stays UNKNOWN rather than becoming a false
            // delisting.
            code == 400 && blamesTheModel(body) -> RETIRED
            else -> UNKNOWN
        }

        private fun blamesTheModel(body: String): Boolean {
            val lower = body.lowercase()
            return MODEL_MISSING_MARKERS.any { lower.contains(it) }
        }
    }
}

/**
 * Result of asking a provider about one model.
 *
 * @param detail short provider message for the failures; empty when available. Kept in
 *   memory only — it is useful in the moment and not worth persisting.
 */
data class ModelProbe(
    val availability: ModelAvailability,
    val detail: String = "",
)

/**
 * One live check: [probes] answers for the models that were asked about, [discovered] is
 * whatever else the provider volunteered.
 */
data class ModelCheck(
    val probes: Map<String, ModelProbe> = emptyMap(),
    val discovered: List<String> = emptyList(),
)

/**
 * Providers list their whole catalogue — image generators, speech models, embedding
 * endpoints — under the same `/models` call as their chat models. Offering those in a
 * translation app's picker is noise, so they are filtered out by name.
 */
object ModelListFilter {
    private val NON_CHAT_MARKERS = listOf(
        "image", "imagen", "tts", "audio", "live", "embedding", "aqa", "veo",
        "video", "rerank", "moderation", "cogview", "cogvideo", "charglm",
        "vision", "whisper", "speech", "realtime",
    )

    fun isChatModel(id: String): Boolean {
        val lower = id.trim().lowercase()
        return lower.isNotEmpty() && NON_CHAT_MARKERS.none { lower.contains(it) }
    }
}

/**
 * The models offered without asking the network — the picker's offline baseline.
 *
 * These go stale as providers retire and rename models, which is what the live check
 * and the 「自定义…」 row exist to absorb. A stale entry is a nuisance; a *missing*
 * entry is a dead end, which is why the lists carry a couple of neighbouring versions
 * of each family rather than only the current one.
 */
object ModelPresets {

    private val BY_ENGINE: Map<TranslationEngineType, List<String>> = mapOf(
        TranslationEngineType.DEEPSEEK to listOf("deepseek-v4-flash", "deepseek-v4-pro"),
        TranslationEngineType.ZHIPU to listOf("glm-4-flash", "glm-4-air", "glm-5"),
        // 3.x Flash only: gemini-2.5-flash was retired for new API users (HTTP 404),
        // and it is what this app shipped as its default before that.
        TranslationEngineType.GEMINI to listOf("gemini-3.8-flash", "gemini-3.7-flash"),
    )

    /** Empty for engines that have no model to choose (Microsoft, the keyless Google one). */
    fun forEngine(type: TranslationEngineType): List<String> = BY_ENGINE[type].orEmpty()

    /**
     * The picker's list: presets in their curated order, then anything the provider
     * reported that we did not already know about, deduped and name-sorted.
     */
    fun visible(type: TranslationEngineType, discovered: List<String>): List<String> {
        val presets = forEngine(type)
        val extra = discovered
            .filter { it.isNotBlank() && it !in presets && ModelListFilter.isChatModel(it) }
            .distinct()
            .sorted()
        return presets + extra
    }
}
