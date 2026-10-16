package com.xzm.realtimetranslate.live

import android.os.SystemClock
import com.xzm.realtimetranslate.util.AppLog
import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.AsrEngineType
import com.xzm.realtimetranslate.translate.AsrEngine
import com.xzm.realtimetranslate.translate.SpeechRecognizer
import com.xzm.realtimetranslate.translate.SpeechRecognizerFactory
import com.xzm.realtimetranslate.translate.TranslationEngine
import com.xzm.realtimetranslate.translate.TranslationEngineFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drop-in replacement for [LiveTranslateClient] with the exact same public
 * contract (state flow, event flow, nested types), but it runs the whole
 * pipeline on-device / via HTTP instead of the Gemini WebSocket:
 *
 *   sendPcm16le → AsrEngine (VAD + SenseVoice) → sentence text
 *       → TranslationEngine (DeepSeek SSE / Microsoft free) → Chinese fragments
 *       → events: InputTranscript / OutputTranscript / Error / SetupComplete
 *
 * 打开「边说边出」后，同一条管线在句子定稿之前还会先报草稿：
 *   AsrEngine 的 onPartial（[StablePrefix] 稳下来的半句）→ InputPartial
 *       → 限流后的草稿翻译 → OutputPartial（整句定稿时作废）
 *
 * [connect] loads the local ASR model first (must already be downloaded via
 * Settings), then emits SetupComplete + Ready so [connect] callers behave the
 * same as with Gemini.
 */
class RealtimeTranslationClient(private val app: LiveTranslateApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translateMutex = Mutex()

    // Silero VAD 偶发把同一段语音重复识别（含微差变体）→ 记录最近处理过的原文段，
    // 完全相等、或在 8 秒内去标点小写化后相等者视为重复，直接跳过。
    private val recentInputs = ArrayDeque<Pair<String, Long>>() // (text, timestamp)
    private val dedupTimeWindowMs = 8_000L
    private val dedupWindowSize = 6

    /** TRANSPROF 诊断用：语音段序号，对照屏幕取词的 ocrloop 日志。 */
    private val voiceSeq = AtomicInteger(0)

    /** 本会话的语言配置；草稿翻译的协程要用，所以留一份。 */
    private var activeConfig: SessionConfig? = null

    // ---- 「边说边出」的译文草稿 --------------------------------------------------
    // 原文草稿是纯本地产物，几乎不要钱；译文草稿每一条都是一次真实的翻译请求，
    // 所以要限流：只有草稿"明显变长"且距上次提交够久，才值得再发一次。
    private data class DraftRequest(val text: String, val epoch: Int)

    /** 只有最新一条算数：新的草稿来了，正在翻的那条直接被 collectLatest 取消。 */
    private val draftRequest = MutableStateFlow(DraftRequest("", 0))

    /** 每定稿一句 +1：带着旧值的草稿译文一律作废，绝不许盖到新句那行上。 */
    private val draftEpoch = AtomicInteger(0)

    /**
     * 串行化"当前这行译文归谁"。草稿译文的 emit 来自翻译协程，[LiveEvent.OutputReset]
     * 来自另一条协程，两者之间没有这把锁的话，上一句的草稿可能抢在 OutputReset 之后
     * 上屏，把新句那行盖成半句旧话。锁里只做 tryEmit，不做任何等待。
     */
    private val outputLineLock = Any()

    private var draftJob: Job? = null

    /**
     * 是否有正式翻译正在跑。草稿在正式翻译期间一律不提交，因为那时这一行已经归正式
     * 翻译了——上一句的译文还在流式往外吐，这时把下一句的草稿接在同一行后面，屏幕上会
     * 变成"上一句译文＋下一句草稿"两句叠在一起。副作用正好也是好事：那段时间用户本来就
     * 有字幕在看，少发几条草稿请求。
     */
    @Volatile
    private var finalInFlight = false

    // 只在 ASR 的识别线程上读写（onPartial / onSegment 都在那儿），不需要同步。
    private var lastDraftText = ""
    private var lastDraftAtMs = 0L

    @Volatile
    private var draftTranslationPreview = false
    private var settingsJob: Job? = null
    private var idleFlushJob: Job? = null
    private var lastPartialText = ""

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(
        replay = 0,
        // 「边说边出」把事件频率抬高了一档（每句多出若干草稿事件），缓冲区跟着放宽，
        // 免得 DROP_OLDEST 在拥堵时把正式的 InputTranscript/OutputTranscript 挤掉。
        extraBufferCapacity = 256,
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
        data class InputTranscript(val text: String, val languageCode: String? = null, val epoch: Long = 0L) : LiveEvent()
        data class OutputTranscript(val text: String, val languageCode: String? = null, val epoch: Long = 0L) : LiveEvent()
        data class AudioChunk(val pcm: ByteArray, val mimeType: String?) : LiveEvent() {
            override fun equals(other: Any?): Boolean = this === other
            override fun hashCode(): Int = pcm.contentHashCode()
        }
        data class Error(val message: String) : LiveEvent()
        data object SetupComplete : LiveEvent()
        data class Debug(val message: String) : LiveEvent()
        /** Marks the start of a new translated sentence: the subtitle UI clears its current output line. */
        data class OutputReset(val epoch: Long = 0L) : LiveEvent()

        /**
         * 草稿原文：这一句还没说完，但已经稳下来的那截（见 [StablePrefix]）。
         * 会被同一句的 [InputTranscript] 取代，所以显示方要把草稿单独放在一个可丢弃的
         * 缓冲里，**不能**直接追加进累积原文。
         */
        data class InputPartial(val text: String, val epoch: Long = 0L) : LiveEvent()

        /**
         * 草稿译文：拿 [InputPartial] 那截半句先翻出来的结果，专为「仅译文」显示模式而存在
         * ——那个模式下不先翻草稿，屏幕上就什么都不会动。
         * [OutputReset] / [OutputTranscript] 一到即作废。
         */
        data class OutputPartial(val text: String, val epoch: Long = 0L) : LiveEvent()
    }

    private val intentionalClose = AtomicBoolean(false)
    private val setupComplete = AtomicBoolean(false)
    private var speechRecognizer: SpeechRecognizer? = null
    private var translationEngine: TranslationEngine? = null
    private var connectJob: Job? = null

    fun connect(config: SessionConfig) {
        closeInternal(intentional = true, notify = false)
        intentionalClose.set(false)
        setupComplete.set(false)
        recentInputs.clear() // 新会话：去重窗口重新开始
        _connectionState.value = ConnectionState.Connecting
        emitDebug("初始化语音识别…")
        activeConfig = config

        connectJob = scope.launch {
            try {
                val settings = app.settingsRepository.settings.first()

                val engineType = settings.translationEngine
                if (!app.apiKeyStore.hasKeyFor(engineType)) {
                    fail(app.getString(R.string.msg_need_api_key, app.getString(engineType.keyLabelRes!!)))
                    return@launch
                }

                val asrType = settings.asrEngine
                if (!app.apiKeyStore.hasKeyFor(asrType)) {
                    fail("请先在设置中填写语音识别所需的 API Key")
                    return@launch
                }

                val models = app.modelManager
                if (asrType == AsrEngineType.LOCAL_SENSEVOICE) {
                    // Blocking fallback on the IO thread: unpack bundled models if the
                    // app-startup seed hasn't finished yet (idempotent, fast when done).
                    val seeded = models.seedFromAssetsIfNeeded()
                    if (!seeded || !models.isReady()) {
                        fail("语音识别模型未就绪：内置解压失败或存储空间不足，可到 设置 → 语音识别模型 重新下载修复")
                        return@launch
                    }
                    emitDebug("加载本地 SenseVoice 模型…")
                } else {
                    emitDebug("连接云端语音识别（${asrType.name}）…")
                }

                draftTranslationPreview = settings.draftTranslationPreview
                settingsJob?.cancel()
                settingsJob = scope.launch {
                    app.settingsRepository.settings.collectLatest { s ->
                        draftTranslationPreview = s.draftTranslationPreview
                    }
                }

                val partials = settings.partialTranscripts
                speechRecognizer = SpeechRecognizerFactory.create(
                    settings = settings,
                    keys = app.apiKeyStore,
                    models = models,
                    onSegment = { text -> onSegment(text, config) },
                    onPartial = if (partials) {
                        { text -> onPartial(text, config) }
                    } else {
                        null
                    },
                )
                if (partials) ensureDraftWorker()

                // liveSettings: 引擎每次请求重读设置，模型名 / baseUrl / 思考开关
                // 改动下一句即生效，不必重启会话。
                translationEngine = TranslationEngineFactory.create(
                    settings = settings,
                    keys = app.apiKeyStore,
                    liveSettings = { app.settingsRepository.settings.first() },
                )

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
        speechRecognizer?.acceptPcm(chunk)
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
            val engineType = settings.translationEngine
            if (!app.apiKeyStore.hasKeyFor(engineType)) {
                val label = app.getString(engineType.keyLabelRes!!)
                Result.failure(Exception(app.getString(R.string.msg_need_api_key, label)))
            } else {
                // 分派统一走 Factory，这里不再重复 when(engineType)。
                TranslationEngineFactory.testConnection(settings, app.apiKeyStore, target)
            }
        } ?: Result.failure(Exception("测试超时（${timeoutMs}ms）"))

    private fun onSegment(text: String, config: SessionConfig) {
        if (intentionalClose.get()) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        idleFlushJob?.cancel()
        idleFlushJob = null
        lastPartialText = ""
        // 一句说完了 = 一句新的要开始了：草稿的限流计数跟着翻页，否则上一句攒下的
        // "已经翻到哪儿了"会白白压住下一句的第一条草稿。
        lastDraftText = ""
        lastDraftAtMs = 0L
        // VAD 重复识别防护：与最近若干段完全相等、或在 8 秒内去标点小写化后相等
        // （VAD 同段重复回调的微差变体）→ 直接跳过，不发原文、不翻译、不上屏。
        synchronized(recentInputs) {
            if (isDuplicate(trimmed)) return
            recentInputs.addLast(trimmed to System.currentTimeMillis())
            if (recentInputs.size > dedupWindowSize) recentInputs.removeFirst()
        }
        scope.launch {
            if (intentionalClose.get()) return@launch
            val engine = translationEngine
            if (engine == null) return@launch
            // TRANSPROF 诊断（只读埋点）：语音侧对照组。lockWait 是排队等上一句
            // 翻译结束的时间，translate 是同引擎的一次完整往返，用来和屏幕侧对比。
            val seq = voiceSeq.incrementAndGet()
            val tStart = SystemClock.elapsedRealtime()

            // 原文字幕立即上屏，不被上一句的网络翻译阻塞（滚动字幕缓冲天然容错乱序）。
            _events.emit(LiveEvent.InputTranscript(trimmed, languageCode = null))

            // 翻译结果仍串行，保证输出顺序不交错。
            // 这一行归正式翻译了——从这一刻算起，包含排队等锁的那段时间（上一句的译文
            // 还在往这一行上吐字），草稿一律不上屏。
            finalInFlight = true
            translateMutex.withLock {
                if (intentionalClose.get()) return@withLock
                val lockWait = SystemClock.elapsedRealtime() - tStart
                // 新句开始：先清空字幕当前输出行，避免本句流式增量被逐段追加成上一句的重复堆叠。
                // draftEpoch++ 必须和 OutputReset 在同一把锁里（理由见 outputLineLock 注释），
                // 这样「草稿上屏」和「这一行归正式翻译了」之间没有插队的缝。
                val epoch = synchronized(outputLineLock) {
                    val ep = draftEpoch.incrementAndGet().toLong()
                    _events.tryEmit(LiveEvent.OutputReset(ep))
                    ep
                }
                val settings = app.settingsRepository.settings.first()
                val target = config.targetLanguageCode.ifBlank { "zh-Hans" }
                val tTranslate = SystemClock.elapsedRealtime()
                var outChars = 0
                var outText = ""
                try {
                    engine.translate(
                        text = trimmed,
                        sourceLang = settings.sourceLanguageCode,
                        targetLang = target,
                    ).collect { fragment ->
                        if (intentionalClose.get()) return@collect
                        outChars = fragment.length
                        outText = fragment
                        _events.emit(LiveEvent.OutputTranscript(fragment, languageCode = null, epoch = epoch))
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "translate failed", t)
                    _events.emit(LiveEvent.Error("翻译失败：${t.message}"))
                }
                // The pair that answers "the translation is wrong" — the recognized
                // sentence next to what came back. Opt-in only, and it goes to the
                // separate content log, never to app.log.
                if (AppLog.contentEnabled && outText.isNotBlank()) {
                    AppLog.content(TAG, "voice ${trimmed.take(500)} → ${outText.take(500)}")
                }
                Log.i(
                    TAG,
                    "TRANSPROF voice seg#$seq chars=${trimmed.length} lockWait=$lockWait " +
                        "translate=${SystemClock.elapsedRealtime() - tTranslate} out=$outChars",
                )
            }
            finalInFlight = false
        }
    }

    /** True if [candidate] is a VAD repeat of a recently processed segment. */
    private fun isDuplicate(candidate: String): Boolean {
        val now = System.currentTimeMillis()
        val cand = dedupNormalize(candidate)
        for ((prevText, ts) in recentInputs) {
            if (prevText == candidate) return true
            if (now - ts < dedupTimeWindowMs && cand.isNotEmpty() && cand == dedupNormalize(prevText)) return true
        }
        return false
    }

    /** Lowercased alphanumerics only — 忽略空白/标点差异，抓 VAD 同段重复的微差变体。 */
    private fun dedupNormalize(s: String): String = s.filter { it.isLetterOrDigit() }.lowercase()

    /**
     * ASR 报来「这句还没说完，但已经稳下来的那截」。
     *
     * 原文草稿立即上屏（纯本地产物，不要钱）；译文草稿要限流，因为它每一条都是一次真实
     * 的翻译请求——句中每 300ms 就翻一次，会把每句话的请求数翻好几倍。所以只有草稿比
     * 上次送去翻译时明显变长、且距上次提交够久，才值得再发一次。
     *
     * 在 ASR 的识别线程上被调用，和 [onSegment] 串行，所以下面的计数字段不用加锁。
     */
    private fun onPartial(text: String, config: SessionConfig) {
        if (intentionalClose.get()) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // tryEmit（而不是 emitEvent）：草稿必须**同步**进入事件流，才能保证它一定排在
        // 本句定稿的 InputTranscript 之前——那个是在协程里发的，一异步就有插队的可能。
        // 原文草稿纯本地处理（打字机展开），零延迟、零 Token 开销。
        _events.tryEmit(LiveEvent.InputPartial(trimmed))

        lastPartialText = trimmed

        // 静音/停顿自动结句计时器：
        // 若说话人在句尾停顿超过 1.5 秒且 ASR 未发出 onSegment（常见于云端流式 ASR 的静音停流），
        // 自动强制把已识别的文本作为整句送翻，彻底防止界面卡住或翻译悬停！
        idleFlushJob?.cancel()
        idleFlushJob = scope.launch {
            delay(IDLE_FLUSH_TIMEOUT_MS)
            val toFlush = lastPartialText.trim()
            if (toFlush.isNotEmpty() && !intentionalClose.get()) {
                Log.i(TAG, "ASR 静音停顿超时(${IDLE_FLUSH_TIMEOUT_MS}ms)，触发强制结句送翻: ${toFlush.take(50)}")
                lastPartialText = ""
                onSegment(toFlush, config)
            }
        }

        // 译文草稿预览门禁：默认关闭以彻底消灭全屏跳动与前文篡改，仅在用户显式开启时才做低频防抖预览
        if (!draftTranslationPreview) return

        if (trimmed.length - lastDraftText.length < DRAFT_MIN_GROWTH_CHARS) return
        // 正式翻译还在跑：这一行归它（理由见 finalInFlight）。
        if (finalInFlight) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDraftAtMs < DRAFT_MIN_INTERVAL_MS) return
        lastDraftText = trimmed
        lastDraftAtMs = now
        draftRequest.value = DraftRequest(trimmed, draftEpoch.get())
    }

    /**
     * 草稿翻译协程：同时只跑一条，新草稿来了就取消旧的（collectLatest）。
     * 只有一句新话开始时才会重新起协程——上一句的草稿请求已被 [draftEpoch] 判死。
     */
    private fun ensureDraftWorker() {
        if (draftJob?.isActive == true) return
        draftJob = scope.launch {
            draftRequest.collectLatest { req ->
                if (req.text.isBlank()) return@collectLatest
                if (req.epoch != draftEpoch.get()) return@collectLatest
                val engine = translationEngine ?: return@collectLatest
                val config = activeConfig ?: return@collectLatest
                val settings = app.settingsRepository.settings.first()
                val target = config.targetLanguageCode.ifBlank { "zh-Hans" }
                try {
                    engine.translateDraft(
                        text = req.text,
                        sourceLang = settings.sourceLanguageCode,
                        targetLang = target,
                    ).collect { fragment ->
                        if (fragment.isNotBlank()) emitDraftOutput(req.epoch, fragment)
                    }
                } catch (c: CancellationException) {
                    throw c // collectLatest 取消上一条草稿是正常流程，不能当成失败吞掉
                } catch (t: Throwable) {
                    // 草稿失败就当没发生：它本来就是锦上添花，报出来只会变成噪声。
                    // 真有问题的话，紧随其后的正式翻译会给出真正的错误。
                    Log.w(TAG, "draft translate failed: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
    }

    private fun emitDraftOutput(epoch: Int, fragment: String) {
        synchronized(outputLineLock) {
            // 整句已经定稿：这一行现在归正式翻译，草稿再新也不许上屏。
            if (epoch != draftEpoch.get()) return
            _events.tryEmit(LiveEvent.OutputPartial(fragment, epoch = epoch.toLong()))
        }
    }

    private fun closeInternal(intentional: Boolean, notify: Boolean) {
        intentionalClose.set(intentional)
        setupComplete.set(false)
        connectJob?.cancel()
        connectJob = null
        idleFlushJob?.cancel()
        idleFlushJob = null
        lastPartialText = ""
        settingsJob?.cancel()
        settingsJob = null
        speechRecognizer?.close()
        speechRecognizer = null
        translationEngine = null
        activeConfig = null
        // 让上一会话遗留的草稿请求全部作废（worker 是长期协程，跨会话复用）。
        synchronized(outputLineLock) {
            draftEpoch.incrementAndGet()
            draftRequest.value = DraftRequest("", draftEpoch.get())
        }
        lastDraftText = ""
        lastDraftAtMs = 0L
        finalInFlight = false
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

        /**
         * 两条译文草稿之间的最小间隔。
         */
        private const val DRAFT_MIN_INTERVAL_MS = 1_800L

        /** 草稿至少比上次送去翻译时长这么多字，才值得再发一次请求。 */
        private const val DRAFT_MIN_GROWTH_CHARS = 8

        /** ASR 停顿超过该时长未定稿，触发强制结句送翻（800ms 贴合口语自然停顿，极速响应）。 */
        private const val IDLE_FLUSH_TIMEOUT_MS = 800L
    }
}
