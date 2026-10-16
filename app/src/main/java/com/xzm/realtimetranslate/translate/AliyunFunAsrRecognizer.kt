package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aliyun DashScope real-time speech recognition (FunASR/Paraformer) via full-duplex WebSocket.
 * Official endpoint: wss://dashscope.aliyuncs.com/api-ws/v1/inference/
 *
 * Provides millisecond typewriter partial transcripts (onPartial) and automatic
 * sentence segmentation (onSegment).
 */
class AliyunFunAsrRecognizer(
    private val apiKey: String,
    private val model: String = "fun-asr-realtime",
    private val language: String? = null,
    private val onSegment: (String) -> Unit,
    private val onPartial: ((String) -> Unit)? = null,
    private val client: OkHttpClient = defaultClient(),
) : SpeechRecognizer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val readyForAudio = AtomicBoolean(false)
    private var currentTaskId = UUID.randomUUID().toString().replace("-", "")

    private val handshakeLock = Any()
    private val handshakeBuffer = java.io.ByteArrayOutputStream()

    private var webSocket: WebSocket? = null

    init {
        start()
    }

    override fun start() {
        if (closed.get()) return
        readyForAudio.set(false)
        currentTaskId = UUID.randomUUID().toString().replace("-", "")

        val request = Request.Builder()
            .url(WS_ENDPOINT)
            .header("Authorization", "bearer ${apiKey.trim()}")
            .header("X-DashScope-DataInspection", "enable")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected, sending run-task (task_id=$currentTaskId)...")
                sendRunTask(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed.get()) {
                    Log.w(TAG, "WebSocket failure: ${t.message}, scheduling reconnect...")
                    scheduleReconnect(1000)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.get()) {
                    Log.i(TAG, "WebSocket closed ($code $reason), scheduling reconnect...")
                    scheduleReconnect(500)
                }
            }
        })
    }

    private fun scheduleReconnect(delayMs: Long = 500) {
        if (closed.get()) return
        readyForAudio.set(false)
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (!closed.get()) {
                Log.i(TAG, "Reconnecting Aliyun FunASR WebSocket...")
                start()
            }
        }
    }

    private fun sendRunTask(ws: WebSocket) {
        val json = JSONObject().apply {
            put("header", JSONObject().apply {
                put("action", "run-task")
                put("task_id", currentTaskId)
                put("streaming", "duplex")
            })
            put("payload", JSONObject().apply {
                put("task_group", "audio")
                put("task", "asr")
                put("function", "recognition")
                put("model", model.ifBlank { "fun-asr-realtime" })
                put("parameters", JSONObject().apply {
                    put("format", "pcm")
                    put("sample_rate", 16000)
                    put("disfluency_removal", true)
                    if (!language.isNullOrBlank() && language != "auto") {
                        put("language_hints", JSONArray().put(language))
                    }
                })
                put("input", JSONObject())
            })
        }
        ws.send(json.toString())
    }

    private fun handleServerMessage(ws: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val header = json.optJSONObject("header") ?: return
            val event = header.optString("event", "")

            when (event) {
                "task-started" -> {
                    readyForAudio.set(true)
                    Log.i(TAG, "Task started, ready to stream audio.")
                    // 握手完成：将暂存的首字缓冲一次性灌入，解决首字吞音
                    synchronized(handshakeLock) {
                        if (handshakeBuffer.size() > 0) {
                            val bytes = handshakeBuffer.toByteArray()
                            handshakeBuffer.reset()
                            ws.send(bytes.toByteString())
                        }
                    }
                }
                "result-generated" -> {
                    val payload = json.optJSONObject("payload") ?: return
                    val output = payload.optJSONObject("output") ?: return
                    val sentence = output.optJSONObject("sentence") ?: return
                    val sentenceText = sentence.optString("text", "").trim()
                    // 阿里云百炼 FunASR 流式协议在定稿时携带 sentence_end: true 或包含 end_time 标定
                    val isEnd = sentence.optBoolean("sentence_end", false)
                        || (sentence.has("end_time") && sentence.opt("end_time") != JSONObject.NULL)
                        || sentence.optBoolean("end", false)
                        || output.optBoolean("end", false)

                    if (sentenceText.isNotBlank()) {
                        if (isEnd) {
                            Log.d(TAG, "FunASR onSegment: $sentenceText")
                            onSegment(sentenceText)
                        } else {
                            onPartial?.invoke(sentenceText)
                        }
                    }
                }
                "task-finished" -> {
                    Log.i(TAG, "Task finished by server, starting new task...")
                    scheduleReconnect(200)
                }
                "task-failed" -> {
                    val errorMsg = header.optString("error_message", "Unknown error")
                    Log.w(TAG, "Task failed: $errorMsg, retrying...")
                    scheduleReconnect(1000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}")
        }
    }

    override fun acceptPcm(chunk: ByteArray) {
        if (closed.get() || chunk.isEmpty()) return
        if (!readyForAudio.get()) {
            synchronized(handshakeLock) {
                if (handshakeBuffer.size() < 48000) {
                    handshakeBuffer.write(chunk)
                }
            }
            return
        }
        webSocket?.send(chunk.toByteString())
    }

    override fun stop() {
        if (closed.get()) return
        readyForAudio.set(false)
        webSocket?.let { ws ->
            val finishJson = JSONObject().apply {
                put("header", JSONObject().apply {
                    put("action", "finish-task")
                    put("task_id", currentTaskId)
                    put("streaming", "duplex")
                })
                put("payload", JSONObject().apply {
                    put("input", JSONObject())
                })
            }
            ws.send(finishJson.toString())
        }
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        readyForAudio.set(false)
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        synchronized(handshakeLock) {
            handshakeBuffer.reset()
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "AliyunFunAsr"
        private const val WS_ENDPOINT = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket keeps open
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
    }
}
