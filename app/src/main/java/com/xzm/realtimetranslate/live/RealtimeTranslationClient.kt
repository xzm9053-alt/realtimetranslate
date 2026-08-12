package com.xzm.realtimetranslate.live

import android.util.Log
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.translate.AsrEngine
import com.xzm.realtimetranslate.translate.DeepSeekTranslationEngine
import com.xzm.realtimetranslate.translate.MicrosoftFreeTranslationEngine
import com.xzm.realtimetranslate.translate.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drop-in replacement for [LiveTranslateClient] with the exact same public
 * contract (state flow, event flow, nested types), but it runs the whole
 * pipeline on-device / via HTTP instead of the Gemini WebSocket:
 *
 *   sendPcm16le → AsrEngine (VAD + SenseVoice) → sentence text
 *       → TranslationEngine (DeepSeek SSE / Microsoft free) → Chinese fragments
 *       → events: InputTranscript / OutputTranscript / Error / SetupComplete
 *
 * [connect] loads the local ASR model first (must already be downloaded via
 * Settings), then emits SetupComplete + Ready so [connect] callers behave the
 * same as with Gemini.
 */
class RealtimeTranslationClient(private val app: LiveTranslateApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translateMutex = Mutex()

    // Silero VAD 偶发把同一段语音重复识别 → 记录最近处理过的原文段，重复文本直接跳过。
    private val recentInputs = ArrayDeque<String>()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()

    data class SessionConfig(
        val endpoint: String,
        val apiKey: String,
        val modelId: String,
        val targetLanguageCode: String,
        val echoTargetLanguage: Boolean = true,
    )

    sealed class ConnectionState {
        data object Idle : ConnectionState()
        data object Connecting : ConnectionState()
        data object Ready : ConnectionState()
        data class Failed(val message: String) : ConnectionState()
        data object Closed : ConnectionState()
    }

    sealed class LiveEvent {
        data class InputTranscript(val text: String, val languageCode: String? = null) : LiveEvent()
        data class OutputTranscript(val text: String, val languageCode: String? = null) : LiveEvent()
        data class AudioChunk(val pcm: ByteArray, val mimeType: String?) : LiveEvent() {
            override fun equals(other: Any?): Boolean = this === other
            override fun hashCode(): Int = pcm.contentHashCode()
        }
        data class Error(val message: String) : LiveEvent()
        data object SetupComplete : LiveEvent()
        data class Debug(val message: String) : LiveEvent()
    }

    private val intentionalClose = AtomicBoolean(false)
    private val setupComplete = AtomicBoolean(false)
    private var asrEngine: AsrEngine? = null
    private var translationEngine: TranslationEngine? = null
    private var connectJob: Job? = null

    fun connect(config: SessionConfig) {
        closeInternal(intentional = true, notify = false)
        intentionalClose.set(false)
        setupComplete.set(false)
        recentInputs.clear() // 新会话：去重窗口重新开始
        _connectionState.value = ConnectionState.Connecting
        emitDebug("初始化本地语音识别…")

        connectJob = scope.launch {
            try {
                val settings = app.settingsRepository.settings.first()

                if (settings.translationEngine == TranslationEngineType.DEEPSEEK &&
                    !app.apiKeyStore.hasDeepSeekKey()
                ) {
                    fail("请先在设置中填写 DeepSeek API Key")
                    return@launch
                }

                val models = app.modelManager
                // Blocking fallback on the IO thread: unpack bundled models if the
                // app-startup seed hasn't finished yet (idempotent, fast when done).
                val seeded = models.seedFromAssetsIfNeeded()
                if (!seeded || !models.isReady()) {
                    fail("语音识别模型未就绪：内置解压失败或存储空间不足，可到 设置 → 语音识别模型 重新下载修复")
                    return@launch
                }

                emitDebug("加载 SenseVoice 模型…")
                val engine = AsrEngine(
                    modelDir = models.paths.senseVoiceModel.parentFile!!,
                    sileroVadPath = models.paths.sileroVad.absolutePath,
                    onSegment = { text -> onSegment(text, config) },
                )
                asrEngine = engine

                translationEngine = when (settings.translationEngine) {
                    TranslationEngineType.DEEPSEEK -> DeepSeekTranslationEngine(
                        apiKey = app.apiKeyStore.getDeepSeekKey(),
                        baseUrl = settings.deepseekBaseUrl.ifBlank { "https://api.deepseek.com" },
                        model = settings.deepseekModel.ifBlank { "deepseek-v4-flash" },
                    )
                    TranslationEngineType.MICROSOFT -> MicrosoftFreeTranslationEngine()
                }

                setupComplete.set(true)
                _connectionState.value = ConnectionState.Ready
                emitEvent(LiveEvent.SetupComplete)
                emitDebug("语音识别就绪，等待声音…")
            } catch (t: Throwable) {
                Log.e(TAG, "connect failed", t)
                fail("初始化失败：${t.message}")
            }
        }
    }

    fun sendPcm16le(chunk: ByteArray, sampleRate: Int = 16_000) {
        if (sampleRate != 16_000) return // pipeline is fixed to 16 kHz
        asrEngine?.acceptPcm(chunk)
    }

    fun close() {
        closeInternal(intentional = true, notify = true)
    }

    fun destroy() {
        close()
        scope.cancel()
    }

    /**
     * Settings "连接测试". Tests the *selected* engine (not the WebSocket), and
     * also verifies the local ASR model state.
     */
    suspend fun testConnection(config: SessionConfig, timeoutMs: Long = 25_000): Result<String> =
        withTimeoutOrNull(timeoutMs) {
            val settings = app.settingsRepository.settings.first()
            val target = config.targetLanguageCode.ifBlank { "zh-Hans" }
            when (settings.translationEngine) {
                TranslationEngineType.DEEPSEEK -> {
                    val key = app.apiKeyStore.getDeepSeekKey()
                    if (key.isBlank()) {
                        Result.failure(Exception("DeepSeek API Key 为空"))
                    } else {
                        DeepSeekTranslationEngine(
                            apiKey = key,
                            baseUrl = settings.deepseekBaseUrl.ifBlank { "https://api.deepseek.com" },
                            model = settings.deepseekModel.ifBlank { "deepseek-v4-flash" },
                        ).testConnection(target)
                    }
                }
                TranslationEngineType.MICROSOFT -> {
                    MicrosoftFreeTranslationEngine().testConnection(target)
                }
            }
        } ?: Result.failure(Exception("测试超时（${timeoutMs}ms）"))

    private fun onSegment(text: String, config: SessionConfig) {
        if (intentionalClose.get()) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // VAD 重复识别防护：最近 3 段内出现相同文本 → 直接跳过（不发原文、不翻译、不上屏）。
        // 微软整句返回时重复句会堆进滚动字幕，这里在源头掐掉。
        synchronized(recentInputs) {
            if (recentInputs.contains(trimmed)) return
            recentInputs.addLast(trimmed)
            if (recentInputs.size > 3) recentInputs.removeFirst()
        }
        scope.launch {
            if (intentionalClose.get()) return@launch
            val engine = translationEngine
            if (engine == null) return@launch

            // 原文字幕立即上屏，不被上一句的网络翻译阻塞（滚动字幕缓冲天然容错乱序）。
            _events.emit(LiveEvent.InputTranscript(trimmed, languageCode = null))

            // 翻译结果仍串行，保证输出顺序不交错。
            translateMutex.withLock {
                if (intentionalClose.get()) return@withLock
                val settings = app.settingsRepository.settings.first()
                val target = config.targetLanguageCode.ifBlank { "zh-Hans" }
                try {
                    engine.translate(
                        text = trimmed,
                        sourceLang = settings.sourceLanguageCode,
                        targetLang = target,
                    ).collect { fragment ->
                        if (intentionalClose.get()) return@collect
                        _events.emit(LiveEvent.OutputTranscript(fragment, languageCode = null))
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "translate failed", t)
                    _events.emit(LiveEvent.Error("翻译失败：${t.message}"))
                }
            }
        }
    }

    private fun closeInternal(intentional: Boolean, notify: Boolean) {
        intentionalClose.set(intentional)
        setupComplete.set(false)
        connectJob?.cancel()
        connectJob = null
        asrEngine?.close()
        asrEngine = null
        translationEngine = null
        if (notify && _connectionState.value !is ConnectionState.Failed) {
            _connectionState.value = ConnectionState.Idle
        }
    }

    private fun fail(message: String) {
        _connectionState.value = ConnectionState.Failed(message)
        scope.launch { _events.emit(LiveEvent.Error(message)) }
    }

    private fun emitDebug(message: String) {
        scope.launch { _events.emit(LiveEvent.Debug(message)) }
    }

    private fun emitEvent(event: LiveEvent) {
        if (!_events.tryEmit(event)) {
            scope.launch { _events.emit(event) }
        }
    }

    companion object {
        private const val TAG = "RealtimeTranslationClient"
    }
}
