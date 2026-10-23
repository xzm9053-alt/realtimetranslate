package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Qwen3-ASR realtime recognition over DashScope's OpenAI-Realtime compatible
 * WebSocket. Audio is 16 kHz mono PCM16, base64-encoded per append event.
 *
 * @param maxSentenceSilenceMs server-VAD silence that ends a sentence.
 */
class OpenAiRealtimeAsrRecognizer(
    private val apiKey: String,
    private val model: String = OpenAiRealtimeProtocol.DEFAULT_MODEL,
    private val language: String? = null,
    private val maxSentenceSilenceMs: Int = 400,
    private val workspaceId: String = "",
    private val onSegment: (String) -> Unit,
    private val onPartial: ((String) -> Unit)? = null,
    private val client: OkHttpClient = defaultClient(),
) : SpeechRecognizer {

    private val closed = AtomicBoolean(false)
    private val readyForAudio = AtomicBoolean(false)
    private val reconnectAttempts = java.util.concurrent.atomic.AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Audio that arrives before the session is configured is kept, not dropped. */
    private val handshakeLock = Any()
    private val handshakeBuffer = ByteArrayOutputStream()

    private var webSocket: WebSocket? = null

    init {
        connect()
    }

    override fun start() {
        if (closed.get()) return
        connect()
    }

    private fun connect() {
        if (closed.get()) return
        readyForAudio.set(false)

        val request = Request.Builder()
            .url(DashScopeEndpoints.openAiRealtime(model, workspaceId))
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "OpenAI-Realtime socket connected, sending session.update ($model)")
                webSocket.send(
                    OpenAiRealtimeProtocol.sessionUpdate(
                        model = model,
                        language = language,
                        silenceDurationMs = maxSentenceSilenceMs,
                    ),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (closed.get()) return
                val code = response?.code
                Log.w(TAG, "socket failure (${code ?: "no code"}): ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (closed.get()) return
                Log.i(TAG, "socket closed ($code $reason)")
                scheduleReconnect()
            }
        })
    }

    /** One retry is enough to ride out a transient drop without masking a bad config. */
    private fun scheduleReconnect() {
        if (closed.get() || reconnectAttempts.get() >= MAX_RECONNECTS) return
        reconnectAttempts.incrementAndGet()
        readyForAudio.set(false)
        scope.launch {
            delay(800)
            if (!closed.get()) {
                Log.i(TAG, "reconnecting OpenAI-Realtime socket")
                connect()
            }
        }
    }

    private fun handleServerMessage(ws: WebSocket, text: String) {
        when (val event = OpenAiRealtimeProtocol.parse(text)) {
            is OpenAiRealtimeProtocol.Event.Ready -> {
                readyForAudio.set(true)
                reconnectAttempts.set(0)
                Log.i(TAG, "session ready, streaming buffered audio")
                synchronized(handshakeLock) {
                    if (handshakeBuffer.size() > 0) {
                        val bytes = handshakeBuffer.toByteArray()
                        handshakeBuffer.reset()
                        ws.send(OpenAiRealtimeProtocol.audioAppend(encode(bytes)))
                    }
                }
            }

            is OpenAiRealtimeProtocol.Event.Segment -> {
                Log.d(TAG, "onSegment: ${event.text}")
                onSegment(event.text)
            }

            is OpenAiRealtimeProtocol.Event.Partial -> onPartial?.invoke(event.text)

            is OpenAiRealtimeProtocol.Event.Failed -> {
                Log.w(TAG, "server error: ${event.message}")
                Log.w(TAG, "raw: ${text.take(500)}")
            }

            OpenAiRealtimeProtocol.Event.Ignored -> Unit
        }
    }

    override fun acceptPcm(chunk: ByteArray) {
        if (closed.get() || chunk.isEmpty()) return
        if (!readyForAudio.get()) {
            synchronized(handshakeLock) {
                if (handshakeBuffer.size() < MAX_BUFFERED_BYTES) handshakeBuffer.write(chunk)
            }
            return
        }
        webSocket?.send(OpenAiRealtimeProtocol.audioAppend(encode(chunk)))
    }

    override fun stop() {
        if (closed.get()) return
        readyForAudio.set(false)
        // server_vad already commits on silence; an explicit commit flushes the tail.
        webSocket?.send(OpenAiRealtimeProtocol.audioCommit())
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        readyForAudio.set(false)
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        synchronized(handshakeLock) { handshakeBuffer.reset() }
        scope.cancel()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    companion object {
        private const val TAG = "OpenAiRealtime"

        /** ~1.5 s of 16 kHz PCM16, matching the run-task recognizer's handshake buffer. */
        private const val MAX_BUFFERED_BYTES = 48_000

        private const val MAX_RECONNECTS = 1

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
