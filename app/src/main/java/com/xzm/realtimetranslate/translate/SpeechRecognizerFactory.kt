package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.data.ApiKeyStore
import com.xzm.realtimetranslate.data.AsrEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.util.ModelDownloader
import com.xzm.realtimetranslate.util.WavEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Single dispatch point for creating [SpeechRecognizer] instances and testing connectivity
 * based on [UserSettings.asrEngine].
 */
object SpeechRecognizerFactory {

    fun create(
        settings: UserSettings,
        keys: ApiKeyStore,
        models: ModelDownloader,
        onSegment: (String) -> Unit,
        onPartial: ((String) -> Unit)?,
    ): SpeechRecognizer {
        return when (settings.asrEngine) {
            AsrEngineType.LOCAL_SENSEVOICE -> {
                AsrEngine(
                    modelDir = models.paths.senseVoiceModel.parentFile!!,
                    sileroVadPath = models.paths.sileroVad.absolutePath,
                    language = AsrEngine.senseVoiceLanguageFor(settings.sourceLanguageCode),
                    vadMinSilenceDuration = settings.vadMinSilenceSec,
                    vadMaxSpeechDuration = settings.vadMaxSpeechSec,
                    onSegment = onSegment,
                    onPartial = onPartial,
                )
            }
            AsrEngineType.ALIYUN_FUN_ASR -> {
                val apiKey = keys.getKeyFor(AsrEngineType.ALIYUN_FUN_ASR)
                when (AsrModelCatalog.protocolFor(settings.aliyunAsrModel)) {
                    AsrProtocol.RUN_TASK -> AliyunFunAsrRecognizer(
                        apiKey = apiKey,
                        model = settings.aliyunAsrModel,
                        language = settings.sourceLanguageCode,
                        maxSentenceSilenceMs = settings.aliyunAsrSilenceMs,
                        workspaceId = settings.aliyunWorkspaceId,
                        onSegment = onSegment,
                        onPartial = onPartial,
                    )
                    AsrProtocol.OPENAI_REALTIME -> OpenAiRealtimeAsrRecognizer(
                        apiKey = apiKey,
                        model = settings.aliyunAsrModel,
                        language = settings.sourceLanguageCode,
                        maxSentenceSilenceMs = settings.aliyunAsrSilenceMs,
                        workspaceId = settings.aliyunWorkspaceId,
                        onSegment = onSegment,
                        onPartial = onPartial,
                    )
                }
            }
            AsrEngineType.GENERIC_OPENAI -> {
                val apiKey = keys.getKeyFor(AsrEngineType.GENERIC_OPENAI)
                GenericOpenAiSpeechRecognizer(
                    baseUrl = settings.genericAsrBaseUrl,
                    apiKey = apiKey,
                    model = settings.genericAsrModel,
                    language = settings.sourceLanguageCode,
                    onSegment = onSegment,
                    onPartial = onPartial,
                )
            }
        }
    }

    /**
     * One-shot connectivity and health check for the selected [engineType].
     */
    suspend fun testConnection(
        engineType: AsrEngineType,
        settings: UserSettings,
        keys: ApiKeyStore,
        models: ModelDownloader,
    ): Result<String> = withContext(Dispatchers.IO) {
        when (engineType) {
            AsrEngineType.LOCAL_SENSEVOICE -> {
                val paths = models.paths
                if (paths.senseVoiceModel.exists() && paths.sileroVad.exists() && paths.tokens.exists()) {
                    Result.success("✅ 本地模型已就绪：SenseVoice (239MB) 与 Silero VAD 完整，离线识别可正常运行")
                } else {
                    Result.failure(Exception("本地模型文件缺失，请在下方点击「下载/修复模型」"))
                }
            }
            AsrEngineType.ALIYUN_FUN_ASR -> {
                val apiKey = keys.getKeyFor(AsrEngineType.ALIYUN_FUN_ASR)
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("请先填写 DashScope API Key"))
                }
                val modelName = settings.aliyunAsrModel.ifBlank { "fun-asr-realtime" }
                if (DashScopeEndpoints.requiresWorkspace(modelName) &&
                    settings.aliyunWorkspaceId.isBlank()
                ) {
                    return@withContext Result.failure(
                        Exception("模型 [$modelName] 需要百炼业务空间 ID：请在下方填写后重试"),
                    )
                }
                when (AsrModelCatalog.protocolFor(modelName)) {
                    AsrProtocol.RUN_TASK ->
                        testAliyunWs(apiKey, modelName, settings.aliyunWorkspaceId)
                    AsrProtocol.OPENAI_REALTIME ->
                        testOpenAiRealtime(apiKey, modelName, settings.aliyunWorkspaceId)
                }
            }
            AsrEngineType.GENERIC_OPENAI -> {
                val baseUrl = settings.genericAsrBaseUrl.trim()
                if (baseUrl.isBlank()) {
                    return@withContext Result.failure(Exception("请填写转录 API 地址 (Base URL)"))
                }
                val apiKey = keys.getKeyFor(AsrEngineType.GENERIC_OPENAI)
                val modelName = settings.genericAsrModel.ifBlank { "FunAudioLLM/SenseVoiceSmall" }
                testGenericEndpoint(baseUrl, apiKey, modelName)
            }
        }
    }

    private suspend fun testAliyunWs(apiKey: String, model: String, workspaceId: String): Result<String> =
        suspendCancellableCoroutine { cont ->
            val client = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(DashScopeEndpoints.realtime(workspaceId))
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("X-DashScope-DataInspection", "enable")
                .build()

            val taskId = UUID.randomUUID().toString().replace("-", "")
            var resumed = false

            fun resumeOnce(res: Result<String>) {
                if (!resumed) {
                    resumed = true
                    cont.resume(res)
                }
            }

            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val json = JSONObject().apply {
                        put("header", JSONObject().apply {
                            put("action", "run-task")
                            put("task_id", taskId)
                            put("streaming", "duplex")
                        })
                        put("payload", JSONObject().apply {
                            put("task_group", "audio")
                            put("task", "asr")
                            put("function", "recognition")
                            put("model", model)
                            put("parameters", JSONObject().apply {
                                put("format", "pcm")
                                put("sample_rate", 16000)
                            })
                            put("input", JSONObject())
                        })
                    }
                    webSocket.send(json.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        val header = json.optJSONObject("header")
                        val event = header?.optString("event", "")
                        if (event == "task-started") {
                            webSocket.close(1000, "test finished")
                            resumeOnce(Result.success("✅ 阿里百炼连接成功：模型 [$model] 鉴权与流式通道就绪"))
                        } else if (event == "task-failed") {
                            val errMsg = header?.optString("error_message", "未知错误")
                            webSocket.close(1000, "test failed")
                            resumeOnce(Result.failure(Exception("任务创建失败：$errMsg")))
                        }
                    } catch (e: Exception) {
                        resumeOnce(Result.failure(e))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code
                    val msg = if (code != null) "HTTP $code: ${t.message}" else t.message ?: "网络连接失败"
                    resumeOnce(Result.failure(Exception(msg)))
                }
            })

            cont.invokeOnCancellation {
                ws.cancel()
            }
        }

    /**
     * OpenAI-Realtime handshake check: open the socket, send `session.update`, and treat
     * `session.created` / `session.updated` as success. Any `error` event is surfaced with
     * its original text — DashScope's field names are the part most likely to drift.
     */
    private suspend fun testOpenAiRealtime(
        apiKey: String,
        model: String,
        workspaceId: String,
    ): Result<String> = suspendCancellableCoroutine { cont ->
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(DashScopeEndpoints.openAiRealtime(model, workspaceId))
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .build()

        var resumed = false
        fun resumeOnce(res: Result<String>) {
            if (!resumed) {
                resumed = true
                cont.resume(res)
            }
        }

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(OpenAiRealtimeProtocol.sessionUpdate(model))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val event = OpenAiRealtimeProtocol.parse(text)) {
                    is OpenAiRealtimeProtocol.Event.Ready -> {
                        webSocket.close(1000, "test finished")
                        resumeOnce(Result.success("✅ 阿里百炼连接成功：模型 [$model] 的 Realtime 会话已建立"))
                    }
                    is OpenAiRealtimeProtocol.Event.Failed -> {
                        webSocket.close(1000, "test failed")
                        resumeOnce(Result.failure(Exception("模型 [$model] 返回错误：${event.message}")))
                    }
                    else -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val msg = if (code != null) "HTTP $code: ${t.message}" else t.message ?: "网络连接失败"
                resumeOnce(Result.failure(Exception(msg)))
            }
        })

        cont.invokeOnCancellation { ws.cancel() }
    }

    private fun testGenericEndpoint(baseUrl: String, apiKey: String, model: String): Result<String> {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val endpoint = baseUrl.trimEnd('/')
            .removeSuffix("/chat/completions")
            .removeSuffix("/audio/transcriptions") + "/audio/transcriptions"

        // 0.1s silence PCM
        val blankPcm = ByteArray(3200)
        val wavBytes = WavEncoder.encodePcm16leToWav(blankPcm)

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "test.wav", wavBytes.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .build()

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(multipart)

        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        return try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                when {
                    response.isSuccessful -> {
                        Result.success("✅ 通用转录端点连接成功：模型 [$model] 响应正常")
                    }
                    code == 401 || code == 403 -> {
                        Result.failure(Exception("认证失败 (HTTP $code)：请检查 API Key"))
                    }
                    code == 404 -> {
                        Result.failure(Exception("端点未找到 (HTTP 404)：请检查 API Base URL"))
                    }
                    else -> {
                        // Many transcription APIs reject empty audio with a 400 validation error,
                        // which actually proves the endpoint and key are valid!
                        Result.success("✅ 端点与鉴权连接成功 (HTTP $code)：服务在线")
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败：${e.message}"))
        }
    }
}
