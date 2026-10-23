package com.xzm.realtimetranslate.translate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The "recognize only, don't translate" engine.
 *
 * It echoes the recognized sentence as the translation, so every pipeline (audio
 * subtitles, screen OCR, video) keeps working while no network request is ever made:
 * no API key, no cost, no latency. `isLlm` is false, so the draft-preview switch and
 * the thinking toggle stay off for it.
 *
 * Emitting the source verbatim also means `SrtFormatter`'s `translation.ifBlank { source }`
 * is not what makes the video export work — the cue genuinely carries the text.
 */
object PassthroughTranslationEngine : TranslationEngine {

    override fun translate(text: String, sourceLang: String, targetLang: String): Flow<String> =
        flowOf(text)

    override suspend fun testConnection(targetLang: String): Result<String> =
        Result.success("仅识别模式：不联网，无需 API Key")
}
