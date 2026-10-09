package com.xzm.realtimetranslate.util

/**
 * Single choke point for scrubbing secrets out of anything that may be written to disk
 * or handed to the user. Everything [AppLog] persists goes through [sanitize], so a new
 * log call site cannot leak a credential by forgetting to redact.
 *
 * Pure string logic on purpose: no Android APIs, so it runs in plain JVM unit tests.
 */
object LogSanitizer {

    /** Long messages are truncated before the regexes run, to bound the cost per line. */
    const val DEFAULT_MAX_LEN = 4096

    /** Stack traces get more room than ordinary messages, but still a bound. */
    const val STACK_MAX_LEN = 16 * 1024

    private const val TRUNCATED = "…(truncated)"

    // "?key=", "&api_key=", "&token=" … — the value is what must never be exported.
    private val QUERY_SECRET = Regex(
        """(?i)([?&](?:key|api[_-]?key|apikey|token|access[_-]?token|auth|password|secret)=)[^&\s"']+"""
    )

    // "Authorization: Bearer eyJ…"
    private val BEARER = Regex("""(?i)(Bearer\s+)[A-Za-z0-9._\-]+""")

    // Google AI Studio keys are always "AIza" + 35 chars.
    private val GOOGLE_KEY = Regex("""AIza[0-9A-Za-z_\-]{35}""")

    // OpenAI-style keys (also used by most relays/OpenRouter).
    private val OPENAI_KEY = Regex("""sk-[A-Za-z0-9_\-]{16,}""")

    // Hugging Face tokens, used by the model downloader.
    private val HF_TOKEN = Regex("""hf_[A-Za-z0-9]{20,}""")

    // Long opaque blobs: base64 payloads, JWTs and similar.
    private val LONG_BASE64 = Regex("""[A-Za-z0-9+/]{200,}={0,2}""")

    /**
     * Redacts known credential shapes and truncates to [maxLen]. Idempotent: running it
     * over an already-redacted string leaves it alone.
     */
    fun sanitize(raw: String, maxLen: Int = DEFAULT_MAX_LEN): String {
        val text = if (raw.length > maxLen) raw.take(maxLen) + TRUNCATED else raw
        var out = QUERY_SECRET.replace(text, "$1***")
        out = BEARER.replace(out, "$1***")
        out = GOOGLE_KEY.replace(out, "AIza***")
        out = OPENAI_KEY.replace(out, "sk-***")
        out = HF_TOKEN.replace(out, "hf_***")
        out = LONG_BASE64.replace(out, "[base64 omitted]")
        return out
    }

    /**
     * Redacts the credential query parameter of an endpoint URL so it is safe to show
     * in the UI and in logs. Same job as the private helper this replaced in
     * `LiveTranslateClient`, now shared.
     */
    fun redactUrl(url: String): String = sanitize(url)
}
