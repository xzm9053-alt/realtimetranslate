package com.xzm.realtimetranslate.translate

import kotlinx.coroutines.flow.Flow

/**
 * Turns one recognized sentence into [targetLang] text.
 *
 * Implementations may stream: every emission carries the *cumulative* translation
 * so far (like a rewrite), so the subtitle service can simply append/replace.
 * A non-streaming engine emits the full text exactly once.
 */
interface TranslationEngine {

    /** Translates a single sentence; never throws — errors surface as a failed Flow. */
    fun translate(text: String, sourceLang: String, targetLang: String): Flow<String>

    /**
     * Same translation, but for a sentence that is **not finished yet** — the draft
     * shown while the speaker is still talking.
     *
     * Two things separate it from [translate], and both exist because a draft is
     * expected to be thrown away:
     *  - it must not enter the engine's conversational context (a half sentence, kept
     *    as context, would poison the next real sentence);
     *  - whatever it returns is discarded the moment the finished sentence arrives.
     *
     * Engines that keep no context at all — the pure translation APIs — are exactly
     * [translate], which is why this defaults to it instead of being abstract.
     */
    fun translateDraft(text: String, sourceLang: String, targetLang: String): Flow<String> =
        translate(text, sourceLang, targetLang)

    /** Cheap connectivity check (no translation needed). */
    suspend fun testConnection(targetLang: String): Result<String>

    /**
     * Asks the provider which of [candidates] it can actually serve right now, and
     * (best effort) what else it offers. The settings screen uses this to mark retired
     * models before the user picks one.
     *
     * Engines with no model to choose return an empty [ModelCheck] — the default.
     */
    suspend fun checkModels(candidates: List<String>): ModelCheck = ModelCheck()
}
