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
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek (OpenAI-compatible) chat completions with SSE streaming.
 *
 * Streaming is done manually over OkHttp so we can flush every delta the moment
 * it arrives (SSE lines). Each `data:` frame's `choices[0].delta.content` is
 * appended to the running translation, which is then emitted — the subtitle UI
 * treats each emission as the current full translation.
 */
class DeepSeekTranslationEngine(
    private val apiKey: String,
    private val baseUrl: String = "https://api.deepseek.com",
    private val model: String = "deepseek-v4-flash",
) : TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun translate(text: String, sourceLang: String, targetLang: String): Flow<String> = flow {
        val target = targetLabel(targetLang.ifBlank { "zh-Hans" })
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "You are a professional translation engine. Translate the user's text into " +
                            "$target. Output ONLY the translated text. No explanations, no notes, " +
                            "no quotation marks, no code fences.",
                    ),
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", text),
            )
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("temperature", 0.3)
            .put("messages", messages)

        val url = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(req).execute()
        resp.use {
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw IOException("DeepSeek HTTP ${resp.code}: ${err.take(400)}")
            }
            val source = resp.body?.source() ?: throw IOException("DeepSeek 响应为空")
            val builder = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val content = runCatching {
                    JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.opt("content")
                }.getOrNull()
                // org.json coerces a JSON null into the literal string "null".
                // Reasoning-model chunks stream content:null (text lives in
                // reasoning_content) — skip anything that isn't a real String so
                // the translation never fills with "null" spam.
                if (content is String && content.isNotEmpty()) {
                    builder.append(content)
                    emit(builder.toString())
                }
            }
            if (builder.isEmpty()) {
                throw IOException("DeepSeek 未返回任何内容（请检查模型名与 Key 权限）")
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("model", model)
                    .put("stream", false)
                    .put("max_tokens", 8)
                    .put(
                        "messages",
                        JSONArray().put(
                            JSONObject().put("role", "user").put("content", "ping"),
                        ),
                    )
                val url = baseUrl.trim().trimEnd('/') + "/chat/completions"
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string().orEmpty()
                        throw IOException("HTTP ${resp.code}: ${err.take(300)}")
                    }
                    val json = JSONObject(resp.body!!.string())
                    val content = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.opt("content")
                    val reply = (content as? String)?.trim().orEmpty()
                    val usage = json.optJSONObject("usage")
                    buildString {
                        append("DeepSeek 连接成功（模型 $model）")
                        if (reply.isNotBlank()) append(" · 回复：${reply.take(60)}")
                        if (usage != null) append(" · 额度：${usage.optString("total_tokens")}t")
                    }
                }
            }
        }

    private fun targetLabel(code: String): String = when (code) {
        "zh-Hans" -> "Simplified Chinese (简体中文)"
        "zh-Hant" -> "Traditional Chinese (繁體中文)"
        "en" -> "English"
        "ja" -> "Japanese (日本語)"
        "ko" -> "Korean (한국어)"
        "es" -> "Spanish"
        "fr" -> "French"
        "de" -> "German"
        "ru" -> "Russian"
        else -> code
    }
}
