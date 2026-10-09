package com.xzm.realtimetranslate.util

import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Flattens the settings that shape a translation request into plain text for the
 * diagnostic report.
 *
 * The rule here is absolute: **no credential ever appears**. API keys live in
 * [com.xzm.realtimetranslate.data.ApiKeyStore], which this file does not even import, and
 * the one secret that *does* live in [UserSettings] — the Hugging Face token — is reported
 * only as set/unset. Base URLs go through [LogSanitizer] because a user can point the
 * generic engine at a relay URL that carries a key in the query string.
 *
 * Most "translation produces nothing" reports come down to the engine/model/language
 * combination below, which is why the report leads with it.
 */
object SettingsSummary {

    private val CHECKED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    fun build(settings: UserSettings): String = buildString {
        val engine = settings.translationEngine
        append("engine: ${engine.name} (${if (engine.isLlm) "llm" else "api"})\n")
        append("model: ${modelOf(settings)}\n")
        append("baseUrl: ${LogSanitizer.sanitize(baseUrlOf(settings), 300)}\n")
        if (engine == TranslationEngineType.OPENAI_COMPAT) {
            append("thinkingOffStyle: ${settings.genericThinkingOffStyle.name}\n")
        }
        append("aiDeepThinking: ${settings.aiDeepThinking}\n")
        append("languages: source=${settings.sourceLanguageCode} target=${settings.targetLanguageCode}\n")
        append("audioSource: ${settings.audioSourceMode.name}\n")
        append(
            "vad: minSilence=${sec(settings.vadMinSilenceSec)} maxSpeech=${sec(settings.vadMaxSpeechSec)}\n"
        )
        append("ocrScript: ${settings.ocrScript.name} outline=${settings.ocrRegionOutlineEnabled}\n")
        append("history: ${settings.historyMode.name} limit=${settings.historyLimit}\n")
        append(
            "subtitle: font=${sec(settings.fontSizeSp)}sp alpha=${sec(settings.backgroundAlpha)} " +
                "mode=${settings.displayMode.name} " +
                "box=${settings.overlayWidthDp}x${settings.overlayHeightDp}dp " +
                "at=(${settings.overlayX},${settings.overlayY})\n"
        )
        append("liveModel: ${settings.modelId} (${settings.endpoint})\n")
        append("modelMirror: ${LogSanitizer.sanitize(settings.modelMirrorUrl, 200)}\n")
        append("huggingfaceToken: ${if (settings.huggingfaceToken.isBlank()) "未设置" else "已设置"}\n")
        append("modelsDiscovered: ${settings.discoveredModels.size} ${availabilityCounts(settings)}\n")
        append("modelsCheckedAt: ${checkedAt(settings.modelsCheckedAt)}\n")
    }

    /** The model id the active engine will actually send. */
    fun modelOf(settings: UserSettings): String = when (settings.translationEngine) {
        TranslationEngineType.DEEPSEEK -> settings.deepseekModel
        TranslationEngineType.ZHIPU -> settings.zhipuModel
        TranslationEngineType.GEMINI -> settings.geminiModel
        TranslationEngineType.OPENAI_COMPAT -> settings.genericModel.ifBlank { "(未设置)" }
        else -> "(不适用)"
    }

    /** The endpoint the active engine will actually call. */
    fun baseUrlOf(settings: UserSettings): String = when (settings.translationEngine) {
        TranslationEngineType.DEEPSEEK -> settings.deepseekBaseUrl
        TranslationEngineType.ZHIPU -> settings.zhipuBaseUrl
        TranslationEngineType.GEMINI -> settings.geminiBaseUrl
        TranslationEngineType.OPENAI_COMPAT -> settings.genericBaseUrl.ifBlank { "(未设置)" }
        else -> "(内置)"
    }

    /** Dead and rate-limited models are the usual reason a picker looks empty. */
    private fun availabilityCounts(settings: UserSettings): String {
        if (settings.modelAvailability.isEmpty()) return "(no availability cache)"
        val counts = settings.modelAvailability.values.groupingBy { it }.eachCount()
        return counts.entries.joinToString(prefix = "(", postfix = ")") { "${it.key}=${it.value}" }
    }

    private fun checkedAt(millis: Long): String {
        if (millis <= 0L) return "never"
        return CHECKED_AT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }

    /** Fixed two decimals, `Locale.US`, so the report reads the same on every device. */
    private fun sec(value: Float): String = String.format(Locale.US, "%.2f", value)
}
