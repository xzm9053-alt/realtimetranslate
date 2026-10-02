package com.xzm.realtimetranslate.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Translation API v2 — the supported channel, needs a GCP API key.
 *
 * POST `https://translation.googleapis.com/language/translate/v2?key=…` with
 * `{"q":["…"],"target":"zh-CN","format":"text"}`; the answer is
 * `data.translations[0].translatedText`.
 *
 * v2 escapes HTML entities **even with `format=text`**, so the result goes through
 * [unescapeHtmlEntities] — without it an apostrophe shows up as `&#39;`.
 *
 * Non-streaming, like [GoogleFreeTranslationEngine].
 */
class GoogleApiTranslationEngine(private val apiKey: String) : TranslationEngine {

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
                "谷歌翻译（API Key）连接成功 · 测试：$reply"
            }
        }

    private fun fetch(text: String, sourceLang: String, targetLang: String): String {
        val body = JSONObject()
            .put("q", JSONArray().put(text))
            .put("target", TranslationLanguageCodes.googleLangCode(targetLang.ifBlank { "zh-Hans" }))
            .put("format", "text")
        // Omitting "source" lets Google auto-detect, which is what "auto" means here.
        if (sourceLang.isNotBlank() && sourceLang != "auto") {
            body.put("source", TranslationLanguageCodes.googleLangCode(sourceLang))
        }
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("translation.googleapis.com")
            .addPathSegments("language/translate/v2")
            .addQueryParameter("key", apiKey)
            .build()
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException(errorMessage(resp.code, resp.body?.string().orEmpty()))
            }
            val json = JSONObject(resp.body?.string().orEmpty())
            val translated = json.optJSONObject("data")
                ?.optJSONArray("translations")
                ?.optJSONObject(0)
                ?.opt("translatedText")
            if (translated !is String || translated.isEmpty()) {
                throw IOException("谷歌翻译（API Key）响应解析失败")
            }
            unescapeHtmlEntities(translated)
        }
    }

    private fun errorMessage(code: Int, body: String): String = when (code) {
        400 -> "谷歌翻译（API Key）请求被拒绝（HTTP 400），请检查源/目标语言代码"
        403 -> "谷歌翻译（API Key）鉴权失败（HTTP 403）：Key 无效，" +
            "或该项目未启用 Cloud Translation API / 未绑定结算账号"
        429 -> "谷歌翻译（API Key）配额已用尽或被限流（HTTP 429），" +
            "请到 GCP 控制台检查配额"
        else -> "谷歌翻译（API Key）HTTP $code: ${body.take(300)}"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
