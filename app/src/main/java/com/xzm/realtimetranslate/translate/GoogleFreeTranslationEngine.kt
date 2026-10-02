package com.xzm.realtimetranslate.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Translate's keyless web endpoint — no API key, no GCP project.
 *
 * This is the endpoint the browser widget talks to. It is undocumented, so it can
 * change or rate-limit without notice — users who get 403/429 are pointed at the
 * other engines instead.
 *
 * Non-streaming: the endpoint answers with the whole translation in one response,
 * which the subtitle UI handles through its "whole sentence" branch (same as
 * Microsoft's engine).
 */
class GoogleFreeTranslationEngine : TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun translate(text: String, sourceLang: String, targetLang: String): Flow<String> = flow {
        emit(fetch(text, sourceLang, targetLang))
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val reply = fetch("hello", "auto", targetLang)
                "谷歌翻译（免费）连接成功（无需 Key） · 测试：$reply"
            }
        }

    private fun fetch(text: String, sourceLang: String, targetLang: String): String {
        // Built through HttpUrl.Builder, never string concatenation: the query carries
        // "&", "#" and CJK text, all of which would corrupt a hand-rolled URL.
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("translate.googleapis.com")
            .addPathSegments("translate_a/single")
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", TranslationLanguageCodes.googleLangCode(sourceLang.ifBlank { "auto" }))
            .addQueryParameter("tl", TranslationLanguageCodes.googleLangCode(targetLang.ifBlank { "zh-Hans" }))
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException(errorMessage(resp.code, resp.body?.string().orEmpty()))
            }
            val json = JSONArray(resp.body?.string().orEmpty())
            // Shape: [[["译文","原文",null,null,10],["…","…",…]],null,"en",…]
            // The outer array is split into sentence segments; each segment's [0] is
            // translated text. Transliteration segments carry JSON null at [0].
            val segments = json.optJSONArray(0)
                ?: throw IOException("谷歌翻译（免费）响应解析失败")
            val builder = StringBuilder()
            for (i in 0 until segments.length()) {
                val segment = segments.optJSONArray(i) ?: continue
                // opt(0) + `is String` rather than optString(0): org.json turns a JSON
                // null into the literal string "null".
                val piece = segment.opt(0)
                if (piece is String) builder.append(piece)
            }
            if (builder.isEmpty()) throw IOException("谷歌翻译（免费）响应解析失败")
            // Deliberately NOT HTML-unescaped: this endpoint returns plain text, so
            // decoding it would corrupt source text that legitimately contains
            // "&amp;" or "&#39;" — turning those into "&" and "'" the user never typed.
            builder.toString()
        }
    }

    private fun errorMessage(code: Int, body: String): String = when (code) {
        429 -> "谷歌翻译（免费）已被限流（HTTP 429），请稍后再试，" +
            "或改用「微软翻译」/「Gemini」"
        403 -> "谷歌翻译（免费）端点拒绝访问（HTTP 403），该免费端点可能已变更，" +
            "建议改用「微软翻译」或配置一个 API Key 引擎（Gemini / 智谱 / DeepSeek）"
        else -> "谷歌翻译（免费）HTTP $code: ${body.take(300)}"
    }

    private companion object {
        // The endpoint expects a browser-shaped UA; a bare OkHttp one gets rejected.
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
    }
}
