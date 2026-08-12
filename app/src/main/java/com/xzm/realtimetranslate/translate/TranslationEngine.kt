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

    /** Cheap connectivity check (no translation needed). */
    suspend fun testConnection(targetLang: String): Result<String>
}
