package com.xzm.realtimetranslate.translate

/**
 * The app's BCP-47-ish language codes ("zh-Hans", "pt-BR") translated into whatever
 * a translation provider wants. Unknown codes pass through unchanged, which is the
 * right default: most providers accept a bare language subtag.
 */
object TranslationLanguageCodes {

    /** Google (both the keyless endpoint and Cloud Translation v2) uses legacy codes. */
    fun googleLangCode(code: String): String = when (code) {
        "zh-Hans" -> "zh-CN"
        "zh-Hant" -> "zh-TW"
        // Google has one Portuguese target; pt-BR/pt-PT both collapse onto it.
        "pt-BR", "pt-PT" -> "pt"
        else -> code
    }
}

private val HTML_ENTITY = Regex("&(#\\d+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")

/**
 * Turns Google's HTML-escaped output back into plain text.
 *
 * Cloud Translation v2 escapes HTML entities **even with `format=text`** — an
 * apostrophe comes back as `&#39;`, an ampersand as `&amp;`.
 *
 * This is a single regex pass rather than a chain of `replace()` calls, which makes
 * it immune to the double-decoding trap: with sequential replacements, `&amp;#39;`
 * decodes to `&#39;` on the `&amp;` step and then to `'` on the `&#39;` step,
 * producing a character the source never had. One pass sees each `&…;` exactly once.
 */
fun unescapeHtmlEntities(raw: String): String {
    if ('&' !in raw) return raw
    return HTML_ENTITY.replace(raw) { match ->
        when (val body = match.groupValues[1]) {
            "quot" -> "\""
            "apos" -> "'"
            "lt" -> "<"
            "gt" -> ">"
            "amp" -> "&"
            // Non-breaking space, kept as U+00A0 rather than a plain space.
            "nbsp" -> " "
            else -> codePointOf(body)?.let { String(Character.toChars(it)) } ?: match.value
        }
    }
}

/** `#39` / `#x27` → the code point, or null for names we don't know. */
private fun codePointOf(body: String): Int? {
    val cp = when {
        body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(16)
        body.startsWith("#") -> body.drop(1).toIntOrNull()
        else -> null
    }
    return cp?.takeIf { it in 0..0x10FFFF }
}
