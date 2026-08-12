package com.xzm.realtimetranslate.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Microsoft free translation via the Edge translate endpoint — no key required.
 *
 * The classic flow (GET edge.microsoft.com/translate/auth for a JWT, then POST to
 * api-edge.cognitive.microsofttranslator.com) was removed upstream in 2026-07,
 * so the token endpoint now returns 404. The successor is the unauthenticated
 * [TRANSLATE_URL] endpoint: POST a bare JSON string array, source language is
 * auto-detected. Plain text only (no HTML), which suits subtitles fine.
 */
class MicrosoftFreeTranslationEngine : TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun translate(text: String, sourceLang: String, targetLang: String): Flow<String> = flow {
        val target = targetLang.ifBlank { "zh-Hans" }
        val url = "$TRANSLATE_URL?to=$target&isEnterpriseClient=false"
        // Bare JSON string array — this endpoint rejects the old [{"Text":...}] shape.
        val body = JSONArray().put(text).toString()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw IOException("微软翻译 HTTP ${resp.code}: ${err.take(300)}")
            }
            val json = JSONArray(resp.body!!.string())
            val translated = json
                .optJSONObject(0)
                ?.optJSONArray("translations")
                ?.optJSONObject(0)
                ?.opt("text")
                ?.takeIf { it is String }
                ?.toString()
                ?: throw IOException("微软翻译响应解析失败")
            emit(translated)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = targetLang.ifBlank { "zh-Hans" }
                val url = "$TRANSLATE_URL?to=$target&isEnterpriseClient=false"
                val body = JSONArray().put("ping").toString()
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_UA)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string().orEmpty()
                        throw IOException("微软翻译 HTTP ${resp.code}: ${err.take(300)}")
                    }
                    val json = JSONArray(resp.body!!.string())
                    val reply = json
                        .optJSONObject(0)
                        ?.optJSONArray("translations")
                        ?.optJSONObject(0)
                        ?.opt("text")
                        ?.takeIf { it is String }
                        ?.toString()
                        .orEmpty()
                        .trim()
                    buildString {
                        append("微软免费翻译连接成功（无需 Key）")
                        if (reply.isNotBlank()) append(" · 测试：$reply")
                    }
                }
            }
        }

    companion object {
        private const val TRANSLATE_URL = "https://edge.microsoft.com/translate/translatetext"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
    }
}
