package com.xzm.realtimetranslate.data

import androidx.annotation.StringRes
import com.xzm.realtimetranslate.R

/**
 * A provider that translates for free, described completely enough to set up in one tap.
 *
 * This is deliberately *not* [com.xzm.realtimetranslate.translate.ModelPresets]: that one
 * lists the models a given engine may pick from, and only for engines with
 * `hasPresetModels = true`. This one answers a different question — "which endpoint and
 * which model does a user with no API knowledge need to type in?" — and it exists because
 * every free AI translation tier already runs on an engine this app ships. No new engine,
 * no protocol change: the missing piece was only ever the *instructions*.
 */
data class FreeProvider(
    @StringRes val nameRes: Int,
    /** One line about the free tier, shown under the name. */
    @StringRes val quotaRes: Int,
    /** Which shipped engine this provider runs on. */
    val engine: TranslationEngineType,
    /**
     * What to write into the engine's BaseUrl field. Blank means "keep the engine's own
     * default": a preset must not hard-code a copy of a default the engine already
     * carries, or the two drift apart the day one of them changes.
     */
    val baseUrl: String,
    /** Model id to select. Blank means "leave the model field alone". */
    val model: String,
    /** Short site name for the sign-up page, used as the guide's button label. */
    @StringRes val consoleNameRes: Int,
    /** Where the user registers and creates a key. Opened via ACTION_VIEW. */
    val consoleUrl: String,
)

/**
 * Free AI translation tiers that run on engines this app already has.
 *
 * Two checked facts decide the order, and both come from provider documentation rather
 * than recall:
 *
 *  - Zhipu's `glm-4-flash` is free with no token cap (concurrency-limited only), so it
 *    goes first: a Zhipu key is the whole setup.
 *  - SiliconFlow's Hunyuan-MT-7B is translation-*specialised* and expects an
 *    "output only the translation" instruction — which is exactly the system prompt
 *    `OpenAiCompatChatEngine` already sends, so it needs no prompt work either.
 *
 * Providers whose free tier could not be verified against official docs are not listed
 * here (the project's rule for presets). Naming an endpoint that turns out to be wrong
 * costs the user a confusing 404, so unverified ones stay out until checked.
 */
object FreeTranslationProviders {

    /** The permanently free Zhipu model — also what the Zhipu engine defaults to. */
    private const val ZHIPU_FREE_MODEL = "glm-4-flash"

    private const val SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"
    private const val SILICONFLOW_CONSOLE = "https://cloud.siliconflow.cn/account/ak"

    val all: List<FreeProvider> = listOf(
        FreeProvider(
            nameRes = R.string.free_provider_zhipu,
            quotaRes = R.string.free_provider_zhipu_quota,
            engine = TranslationEngineType.ZHIPU,
            baseUrl = "",
            model = ZHIPU_FREE_MODEL,
            consoleNameRes = R.string.free_console_zhipu,
            consoleUrl = "https://open.bigmodel.cn/",
        ),
        FreeProvider(
            nameRes = R.string.free_provider_hunyuan,
            quotaRes = R.string.free_provider_hunyuan_quota,
            engine = TranslationEngineType.OPENAI_COMPAT,
            baseUrl = SILICONFLOW_BASE_URL,
            model = "tencent/Hunyuan-MT-7B",
            consoleNameRes = R.string.free_console_siliconflow,
            consoleUrl = SILICONFLOW_CONSOLE,
        ),
        FreeProvider(
            nameRes = R.string.free_provider_qwen,
            quotaRes = R.string.free_provider_qwen_quota,
            engine = TranslationEngineType.OPENAI_COMPAT,
            baseUrl = SILICONFLOW_BASE_URL,
            model = "Qwen/Qwen3-8B",
            consoleNameRes = R.string.free_console_siliconflow,
            consoleUrl = SILICONFLOW_CONSOLE,
        ),
    )

    /**
     * One entry per distinct sign-up page, in preset order — two SiliconFlow models share
     * a console, and the guide should not show the same button twice.
     */
    val consolePages: List<FreeProvider> = all.distinctBy { it.consoleUrl }
}

/**
 * Whether [settings] currently describe this provider — i.e. tapping the row would be a
 * no-op. Used to put the ✓ on the provider that is actually live.
 */
fun FreeProvider.isActiveIn(settings: UserSettings): Boolean = when (engine) {
    TranslationEngineType.ZHIPU ->
        settings.translationEngine == TranslationEngineType.ZHIPU &&
            settings.zhipuModel.ifBlank { UserSettings.Defaults.ZHIPU_MODEL }.trim() == model

    TranslationEngineType.OPENAI_COMPAT ->
        settings.translationEngine == TranslationEngineType.OPENAI_COMPAT &&
            settings.genericModel.trim() == model &&
            normalizeBaseUrl(settings.genericBaseUrl) == normalizeBaseUrl(baseUrl)

    else -> false
}

/**
 * Both sides of the comparison in [isActiveIn] go through the same normalisation
 * [com.xzm.realtimetranslate.translate.TranslationEngineFactory] applies before it uses
 * the URL, so a user who pasted the full `/chat/completions` endpoint still reads as
 * "already configured" instead of seeing a ✓ disappear for a cosmetic difference.
 */
private fun normalizeBaseUrl(url: String): String =
    url.trim().trimEnd('/').removeSuffix("/chat/completions").trimEnd('/')
