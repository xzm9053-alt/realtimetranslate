package com.xzm.realtimetranslate.translate

import android.os.SystemClock
import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.util.LogSanitizer
import com.xzm.realtimetranslate.data.ThinkingOffStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Per-request LLM settings. Resolved through a `suspend () -> LlmRequestConfig`
 * provider rather than captured at construction, so editing the model / base URL /
 * thinking switch in Settings takes effect on the *next* translation without
 * restarting the session (both pipelines build the engine once per session).
 */
data class LlmRequestConfig(
    val baseUrl: String,
    val model: String,
    /** True = let the model think (we send no `thinking` field; the provider default applies). */
    val thinking: Boolean,
    /**
     * How this provider spells "don't think". Defaulted to the DeepSeek/Zhipu object so
     * the built-in engines keep the exact body they have always sent; the generic engine
     * overrides it from user settings, where `NONE` means "send nothing at all".
     */
    val thinkingOffStyle: ThinkingOffStyle = ThinkingOffStyle.THINKING_OBJECT,
)

/**
 * Shared implementation for OpenAI-compatible chat-completions backends
 * (DeepSeek, Zhipu GLM, Gemini's compatibility layer). All speak the same protocol:
 * POST `{baseUrl}/chat/completions`, `Authorization: Bearer <key>`, a `data:`-framed
 * SSE stream whose `choices[0].delta.content` carries the running translation.
 *
 * Streaming is done manually over OkHttp so every delta is flushed the moment it
 * arrives. Each emission carries the *cumulative* translation, matching the
 * subtitle UI's rewrite semantics.
 *
 * The one thing that is *not* shared is how a provider spells "don't think" — see
 * [applyThinkingOff].
 */
abstract class OpenAiCompatChatEngine(
    protected val apiKey: String,
    private val config: suspend () -> LlmRequestConfig,
    /** Human-readable name used in user-facing error messages, e.g. "DeepSeek". */
    private val providerName: String,
    private val logTag: String,
) : TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Shares [client]'s connection pool but with tight timeouts: a model probe blocks
     * the settings screen, and one hung model must not hold it for the translation
     * client's full 60-second read timeout. Probes run in parallel, so this is the
     * check's whole wall clock.
     */
    private val probeClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // 会话内翻译历史（原文→译文），供上下文感知翻译。引擎按会话创建，天然随会话重置。
    private val history = ArrayDeque<Pair<String, String>>()
    private val historyWindow = 4

    // TRANSPROF 诊断用：请求序号，把 req#/done# 两行日志对上。
    private val reqSeq = AtomicInteger(0)

    /**
     * Latched once a model rejects the thinking-off parameter with HTTP 400 — Zhipu's
     * glm-5.3 family and Google's Gemini 3 line both always think and refuse to be
     * turned off. Latched so later sentences don't each waste a round trip on a
     * doomed request.
     */
    @Volatile
    private var thinkingDisableRejected = false

    override fun translate(text: String, sourceLang: String, targetLang: String): Flow<String> = flow {
        val cfg = config()
        val target = targetLabel(targetLang.ifBlank { "zh-Hans" })
        val messages = buildMessages(text, target)
        val url = endpoint(cfg, "/chat/completions")

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
            logTag,
            "TRANSPROF req#$seq model=${cfg.model} host=${url.toHttpUrlOrNull()?.host ?: "?"} " +
                "in=${text.length} hist=${history.size} promptChars=$promptChars",
        )
        var t1 = 0L
        var ttf = -1L
        var outChars = 0
        var reasonChars = 0
        var end = "none"
        var finishReason = ""
        // -------------------------------------------------------------------

        try {
            val resp = executeWithTransientRetry(
                maxAttempts = TRANSLATE_RETRY_ATTEMPTS,
                baseDelayMillis = 400,
                maxWaitMillis = 2_000,
            ) { executeWithThinkingFallback(cfg, messages, url) }
            t1 = SystemClock.elapsedRealtime()
            resp.use {
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    // This message is both shown to the user and persisted, so it goes
                    // through the sanitizer and stays short.
                    throw IOException(
                        "$providerName HTTP ${resp.code}: ${LogSanitizer.sanitize(err, 200)}"
                    )
                }
                val source = resp.body?.source() ?: throw IOException("$providerName 响应为空")
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
                    logTag,
                    "TRANSPROF done#$seq code=${resp.code} proto=${resp.protocol} " +
                        "hdr=${t1 - t0} ttf=$ttf total=${SystemClock.elapsedRealtime() - t0} " +
                        "out=$outChars reason=$reasonChars end=$end " +
                        "finish=${finishReason.ifEmpty { "none" }}",
                )
                if (builder.isEmpty()) {
                    throw IOException("$providerName 未返回任何内容（请检查模型名与 Key 权限）")
                }
                // 本句翻译完成，写入历史供下一句参考；超窗移除最旧。失败路径不会走到这里。
                history.addLast(text to builder.toString())
                if (history.size > historyWindow) history.removeFirst()
            }
        } catch (t: Throwable) {
            Log.w(
                logTag,
                "TRANSPROF fail#$seq after=${SystemClock.elapsedRealtime() - t0} " +
                    "hdr=${if (t1 > 0) t1 - t0 else -1} ttf=$ttf out=$outChars end=$end " +
                    "err=${t.javaClass.simpleName}: ${t.message}",
            )
            throw t
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Minimal one-shot probe: it verifies the key, the model name and reachability.
     *
     * Deliberately does **not** send the thinking-off parameter, unlike [translate].
     * A model that rejects it (glm-5.3, Gemini 3) would then fail the connection test
     * even though translation works fine — [executeWithThinkingFallback] retries
     * without the field, and there is no equivalent fallback here.
     */
    override suspend fun testConnection(targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cfg = config()
                val t0 = SystemClock.elapsedRealtime()
                val body = JSONObject()
                    .put("model", cfg.model)
                    .put("stream", false)
                    .put("max_tokens", 8)
                    .put(
                        "messages",
                        JSONArray().put(
                            JSONObject().put("role", "user").put("content", "ping"),
                        ),
                    )
                val url = endpoint(cfg, "/chat/completions")
                val resp = executeWithTransientRetry(
                    maxAttempts = TEST_RETRY_ATTEMPTS,
                    baseDelayMillis = 600,
                    maxWaitMillis = 4_000,
                ) { client.newCall(chatRequest(url, body, client)).execute() }
                resp.use {
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string().orEmpty()
                        // Retries are already spent by the time this is thrown, so say so
                        // — "HTTP 503" on its own reads like the user did something wrong.
                        val hint = if (TransientFailures.isRetryable(resp.code)) {
                            "（服务端繁忙，已重试仍失败；稍后再试，或换个模型）"
                        } else {
                            ""
                        }
                        throw IOException(
                            "HTTP ${resp.code}$hint: ${LogSanitizer.sanitize(err, 200)}"
                        )
                    }
                    val json = JSONObject(resp.body!!.string())
                    val content = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.opt("content")
                    val reply = (content as? String)?.trim().orEmpty()
                    val usage = json.optJSONObject("usage")
                    Log.i(
                        logTag,
                        "TRANSPROF testcode model=${cfg.model} " +
                            "total=${SystemClock.elapsedRealtime() - t0}",
                    )
                    buildString {
                        append("$providerName 连接成功（模型 ${cfg.model}）")
                        if (reply.isNotBlank()) append(" · 回复：${reply.take(60)}")
                        if (usage != null) append(" · 额度：${usage.optString("total_tokens")}t")
                    }
                }
            }
        }

    /**
     * Asks the provider which of [candidates] still work, by calling each one.
     *
     * Listing models cannot answer that question: a provider keeps a retired model in
     * its catalogue while every call to it returns 404 — Google did exactly that with
     * `gemini-2.5-flash`, which is why this checks liveness instead of names.
     */
    override suspend fun checkModels(candidates: List<String>): ModelCheck =
        withContext(Dispatchers.IO) {
            val cfg = config()
            // Best effort, and deliberately unable to affect the verdicts below: Google's
            // compatibility layer has been reported to answer 401 on /models even with a
            // working key.
            val discovered = runCatching { fetchModelIds(cfg) }.getOrDefault(emptyList())
            // Paced: fire the whole list at once and the provider's rate limiter answers
            // for the burst, which would be reported as the models being unavailable —
            // the check lying in exactly the direction it exists to prevent.
            val gate = Semaphore(PROBE_CONCURRENCY)
            val probes = coroutineScope {
                candidates.distinct()
                    .map { model -> async { gate.withPermit { model to probeModel(cfg, model) } } }
                    .awaitAll()
                    .toMap()
            }
            ModelCheck(probes = probes, discovered = discovered)
        }

    /**
     * One minimal request to find out whether [model] is really served.
     *
     * Like [testConnection], this deliberately omits the thinking-off parameter: a model
     * that rejects it would otherwise be reported as unavailable, which is the opposite
     * of the truth.
     */
    private suspend fun probeModel(cfg: LlmRequestConfig, model: String): ModelProbe {
        val body = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("max_tokens", 16)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", "ping")),
            )
        // runCatching, not just an IOException catch: the probes run in parallel inside
        // one coroutineScope, so anything thrown here would cancel the other models'
        // verdicts as well. One odd model must not cost the whole check.
        return runCatching {
            val url = endpoint(cfg, "/chat/completions")
            // Retried for the same reason the check exists: an overloaded server is not a
            // verdict about the model, and UNKNOWN is a poor answer when one more ask
            // would settle it.
            val resp = executeWithTransientRetry(
                maxAttempts = PROBE_RETRY_ATTEMPTS,
                baseDelayMillis = 400,
                maxWaitMillis = 3_000,
            ) { probeClient.newCall(chatRequest(url, body, probeClient)).execute() }
            resp.use {
                val text = resp.body?.string().orEmpty()
                val availability = ModelAvailability.fromHttp(resp.code, text)
                Log.i(logTag, "MODELPROBE model=$model code=${resp.code} -> $availability")
                ModelProbe(
                    availability = availability,
                    detail = if (availability == ModelAvailability.AVAILABLE) "" else text.take(300),
                )
            }
        }.getOrElse { e ->
            Log.w(logTag, "MODELPROBE model=$model 失败：${e.javaClass.simpleName}: ${e.message}")
            ModelProbe(
                availability = if (e is IOException) {
                    ModelAvailability.UNREACHABLE
                } else {
                    ModelAvailability.UNKNOWN
                },
                detail = e.message.orEmpty(),
            )
        }
    }

    /** The provider's own catalogue, OpenAI-shaped (`data[].id`). */
    private fun fetchModelIds(cfg: LlmRequestConfig): List<String> {
        val req = Request.Builder()
            .url(endpoint(cfg, "/models"))
            .bearerAuth()
            .get()
            .build()
        probeClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val data = JSONObject(resp.body!!.string()).optJSONArray("data") ?: return emptyList()
            return (0 until data.length())
                // `as? String` rather than optString: org.json turns a JSON null into the
                // literal "null", which would then be offered as a model name.
                .mapNotNull { data.optJSONObject(it)?.opt("id") as? String }
        }
    }

    /**
     * Sends [send], retrying the failures that deserve it.
     *
     * A provider answering 503 "model overloaded" is asking to be called again — Google
     * says as much in its own Gemini guidance, which prescribes exponential backoff with
     * jitter for 429 and 5xx and says to leave 4xx alone. This app has no queue to absorb
     * the failure: one unrecovered 503 is one sentence of a live conversation lost.
     *
     * If the provider names a cooldown longer than [maxWaitMillis] we stop instead of
     * sleeping. A subtitle that waits half a minute has failed either way, and the user
     * is better served by the real error than by a hang.
     *
     * Only used where nothing has been read off the response yet, so a retry can never
     * duplicate emitted text.
     */
    private suspend fun executeWithTransientRetry(
        maxAttempts: Int,
        baseDelayMillis: Long,
        maxWaitMillis: Long,
        send: suspend () -> Response,
    ): Response {
        var delayMillis = baseDelayMillis
        for (attempt in 1..maxAttempts.coerceAtLeast(1)) {
            val resp = send()
            if (!TransientFailures.isRetryable(resp.code)) return resp
            if (attempt == maxAttempts) return resp
            // peekBody rather than body: if we decide not to retry, the caller still has
            // to be able to read this response.
            val peeked = runCatching { resp.peekBody(RETRY_PEEK_BYTES).string() }.getOrDefault("")
            val asked = TransientFailures.retryDelayMillis(peeked)
            if (asked != null && asked > maxWaitMillis) return resp
            Log.w(logTag, "$providerName HTTP ${resp.code} — retrying in ${delayMillis}ms")
            resp.close()
            // Jitter, so parallel probes do not all come back at the same instant.
            delay(delayMillis + Random.nextLong(delayMillis / 2 + 1))
            delayMillis *= 2
        }
        throw IllegalStateException("unreachable: the final attempt always returns")
    }

    /**
     * Adds the bearer header only when a key is actually stored.
     *
     * The generic slot allows an empty key (Ollama and LM Studio have none), and
     * `Authorization: Bearer ` with an empty credential is a malformed header some
     * servers answer with 401 rather than treating as anonymous.
     */
    private fun Request.Builder.bearerAuth(): Request.Builder = apply {
        if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
    }

    /**
     * The full URL for [path] under the configured base URL.
     *
     * The check matters for the generic engine, whose base URL has no fallback default:
     * left empty, the composed URL is a bare path and OkHttp throws
     * `IllegalArgumentException: Expected URL scheme 'http' or 'https'` — true, but not
     * something a user can act on. Saying which setting is empty is.
     */
    private fun endpoint(cfg: LlmRequestConfig, path: String): String {
        val url = cfg.baseUrl.trim().trimEnd('/') + path
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw IOException(
                "$providerName 服务地址无效（当前为「${cfg.baseUrl.trim()}」）：" +
                    "请在设置里填写以 https:// 开头的 Base URL",
            )
        }
        return url
    }

    private fun chatRequest(url: String, body: JSONObject, client: OkHttpClient): Request =
        Request.Builder()
            .url(url)
            .bearerAuth()
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

    /**
     * Sends the request, retrying once without the thinking-off parameter if the model
     * rejects it. A 400 from a request that carried one means the model forces thinking
     * (Zhipu glm-5.3 answers HTTP 400 / code 1210; Gemini 3 refuses to turn off too);
     * the parameter is then latched off for the lifetime of this engine.
     *
     * Safe to retry: a 400 produces no `data:` frames, so nothing has been emitted.
     */
    private fun executeWithThinkingFallback(
        cfg: LlmRequestConfig,
        messages: JSONArray,
        url: String,
    ): Response {
        while (true) {
            // The style check matters: with NONE the request carries no thinking
            // parameter at all, so a 400 cannot be about one. Without it the latch
            // below would fire and resend a byte-identical request.
            val disableThinking = !cfg.thinking &&
                !thinkingDisableRejected &&
                cfg.thinkingOffStyle != ThinkingOffStyle.NONE
            val body = buildBody(cfg, messages, disableThinking)
            val resp = client.newCall(chatRequest(url, body, client)).execute()
            if (resp.isSuccessful || !disableThinking || resp.code != 400) return resp
            resp.close()
            thinkingDisableRejected = true
            Log.w(
                logTag,
                "$providerName rejected the thinking-off parameter (HTTP 400) — " +
                    "retrying without it; model ${cfg.model} appears to force thinking",
            )
        }
    }

    private fun buildBody(
        cfg: LlmRequestConfig,
        messages: JSONArray,
        disableThinking: Boolean,
    ): JSONObject {
        val body = JSONObject()
            .put("model", cfg.model)
            .put("stream", true)
            .put("temperature", 0.3)
        // 推理模型的思考模式默认开启：模型每翻一句都先在后台"想" 475~2115 字（实测），
        // 想完才吐译文，首字延迟 = 思考时间，可达 17.6s，而译文本身只有 24~54 字。
        // 翻译是典型的低延迟任务，关掉思考首字延迟降到几百毫秒。
        // 注意：思考开启时 temperature 是被忽略的，关掉后它才真正生效。
        if (disableThinking) applyThinkingOff(body, cfg.thinkingOffStyle)
        body.put("messages", messages)
        return body
    }

    /**
     * Writes the provider's "don't think" parameter into [body], spelled as [style] asks.
     *
     * `THINKING_OBJECT` is DeepSeek's and Zhipu's shared `{"thinking":{"type":"disabled"}}`;
     * `REASONING_EFFORT` is the flat form Gemini's compatibility layer expects (it silently
     * drops a `thinking` object, which is what made Gemini think before every sentence);
     * `NONE` sends nothing, which is the only safe default for a backend this app has
     * never seen — callers already skip this entirely for `NONE`.
     */
    protected open fun applyThinkingOff(body: JSONObject, style: ThinkingOffStyle) {
        when (style) {
            ThinkingOffStyle.THINKING_OBJECT ->
                body.put("thinking", JSONObject().put("type", "disabled"))
            ThinkingOffStyle.REASONING_EFFORT -> body.put("reasoning_effort", "none")
            ThinkingOffStyle.NONE -> Unit
        }
    }

    private fun buildMessages(text: String, target: String): JSONArray {
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
        messages.put(JSONObject().put("role", "user").put("content", text))
        return messages
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
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Enough of an error body to find `google.rpc.RetryInfo`. */
        const val RETRY_PEEK_BYTES = 2_048L

        /**
         * One retry for a live subtitle: the second attempt has to land inside the delay
         * the viewer would notice anyway. Trying harder than this costs more than the
         * sentence is worth.
         */
        const val TRANSLATE_RETRY_ATTEMPTS = 2

        /** The user is watching this one, so it can afford a third attempt. */
        const val TEST_RETRY_ATTEMPTS = 3

        const val PROBE_RETRY_ATTEMPTS = 2

        /** Keeps a whole catalogue from arriving at the provider in one burst. */
        const val PROBE_CONCURRENCY = 4
    }
}
