package com.xzm.realtimetranslate.translate

import android.os.SystemClock
import android.util.Log
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
import java.util.concurrent.atomic.AtomicInteger

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

    // 会话内翻译历史（原文→译文），供上下文感知翻译。引擎按会话创建，天然随会话重置。
    private val history = ArrayDeque<Pair<String, String>>()
    private val historyWindow = 4

    // TRANSPROF 诊断用：请求序号，把 req#/done# 两行日志对上。
    private val reqSeq = AtomicInteger(0)

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
        // 上下文：把最近几轮（原文→译文）作为对话历史带入，翻译时参考前文（人称、指代、省略等）。
        for ((src, dst) in history) {
            messages.put(JSONObject().put("role", "user").put("content", src))
            messages.put(JSONObject().put("role", "assistant").put("content", dst))
        }
        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", text),
        )
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("temperature", 0.3)
            // V4 家族思考模式默认开启：模型每翻一句都先在后台"想" 475~2115 字（实测），
            // 想完才吐译文，首字延迟 = 思考时间，可达 17.6s，而译文本身只有 24~54 字。
            // 翻译是典型的低延迟任务，关掉思考首字延迟降到几百毫秒。
            // 注意：思考开启时 temperature 是被忽略的，关掉后它才真正生效。
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("messages", messages)

        val url = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // ---- TRANSPROF 诊断（只读埋点，不影响任何行为）----------------------
        // 每条请求打两行：req# 是发出去时的输入构成，done#/fail# 是耗时拆解。
        // 关键数是 ttf（首字延迟）：LLM 的自回归延迟主要体现在这里和 total。
        val seq = reqSeq.incrementAndGet()
        val t0 = SystemClock.elapsedRealtime()
        var promptChars = 0
        for (i in 0 until messages.length()) {
            promptChars += messages.optJSONObject(i)?.optString("content")?.length ?: 0
        }
        Log.i(
            TAG,
            "TRANSPROF req#$seq model=$model host=${req.url.host} in=${text.length} " +
                "hist=${history.size} promptChars=$promptChars",
        )
        var t1 = 0L
        var ttf = -1L
        var outChars = 0
        var reasonChars = 0
        var end = "none"
        var finishReason = ""
        // -------------------------------------------------------------------

        try {
            val resp = client.newCall(req).execute()
            t1 = SystemClock.elapsedRealtime()
            resp.use {
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IOException("DeepSeek HTTP ${resp.code}: ${err.take(400)}")
                }
                val source = resp.body?.source() ?: throw IOException("DeepSeek 响应为空")
                val builder = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()
                    if (line == null) {
                        end = "EOF_NULL"
                        break
                    }
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        end = "DONE"
                        break
                    }
                    val choice = runCatching {
                        JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                    }.getOrNull()
                    // 只读：网关到底会不会给 finish_reason，用来定位「流迟迟不结束」。
                    val fr = choice?.opt("finish_reason")
                    if (fr is String && fr.isNotEmpty()) finishReason = fr
                    val content = choice?.optJSONObject("delta")?.opt("content")
                    // org.json coerces a JSON null into the literal string "null".
                    // Reasoning-model chunks stream content:null (text lives in
                    // reasoning_content) — skip anything that isn't a real String so
                    // the translation never fills with "null" spam.
                    if (content is String && content.isNotEmpty()) {
                        if (ttf < 0) ttf = SystemClock.elapsedRealtime() - t0
                        builder.append(content)
                        emit(builder.toString())
                    }
                    // 只读：累计 reasoning_content 的体量（它照旧被跳过，只是数一下）。
                    // 这个数一直 > 0 就说明该模型每句都在"思考"，首字延迟 = 思考时间，
                    // 那就是模型选型问题而不是 App 的问题。
                    val reasoning = choice?.optJSONObject("delta")?.opt("reasoning_content")
                    if (reasoning is String) reasonChars += reasoning.length
                }
                if (end == "none") end = "EOF"
                outChars = builder.length
                Log.i(
                    TAG,
                    "TRANSPROF done#$seq code=${resp.code} proto=${resp.protocol} " +
                        "hdr=${t1 - t0} ttf=$ttf total=${SystemClock.elapsedRealtime() - t0} " +
                        "out=$outChars reason=$reasonChars end=$end " +
                        "finish=${finishReason.ifEmpty { "none" }}",
                )
                if (builder.isEmpty()) {
                    throw IOException("DeepSeek 未返回任何内容（请检查模型名与 Key 权限）")
                }
                // 本句翻译完成，写入历史供下一句参考；超窗移除最旧。失败路径不会走到这里。
                history.addLast(text to builder.toString())
                if (history.size > historyWindow) history.removeFirst()
            }
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "TRANSPROF fail#$seq after=${SystemClock.elapsedRealtime() - t0} " +
                    "hdr=${if (t1 > 0) t1 - t0 else -1} ttf=$ttf out=$outChars end=$end " +
                    "err=${t.javaClass.simpleName}: ${t.message}",
            )
            throw t
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val t0 = SystemClock.elapsedRealtime()
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
                    Log.i(
                        TAG,
                        "TRANSPROF testcode model=$model total=${SystemClock.elapsedRealtime() - t0}",
                    )
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

    private companion object {
        private const val TAG = "DeepSeekTranslationEngine"
    }
}
