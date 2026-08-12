package com.xzm.realtimetranslate.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the sherpa-onnx ASR models on first launch.
 *
 * - SenseVoice (zh / en / ja / ko / yue) int8 ONNX from Hugging Face.
 *   The mirror base URL is configurable ([UserSettings.modelMirrorUrl]); the
 *   default [DEFAULT_MIRROR] is hf-mirror.com so it works from mainland China
 *   without a VPN. huggingface.co also works if the user sets it as the mirror.
 * - silero_vad.onnx from the k2-fsa GitHub release (small, always reachable).
 *
 * Files land under filesDir/models/ so the APK stays small until models are
 * actually needed. Progress is reported as overall fraction (0..1) plus a
 * human label, or -1f for indeterminate when a server omits Content-Length.
 */
data class DownloadProgress(
    val overall: Float, // 0..1, or -1f = indeterminate
    val label: String,
)

class ModelDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val paths: ModelPaths by lazy {
        val dir = File(context.filesDir, "models")
        ModelPaths(
            senseVoiceModel = File(dir, "sensevoice/model.int8.onnx"),
            tokens = File(dir, "sensevoice/tokens.txt"),
            sileroVad = File(dir, "silero_vad.onnx"),
        )
    }

    // ---- Bundled-model seeding (models shipped inside the APK) ----
    // Assets are copied to filesDir on first use so the downstream loaders keep
    // working on plain file paths. isReady() stays a pure stat() — blocking seed
    // must only ever run on an IO thread (connect() and app startup).
    private val seedLock = Any()
    @Volatile private var seeded = false
    @Volatile private var assetsChecked = false
    @Volatile private var assetsAvailable = false

    /** True once bundled models have been unpacked to filesDir (settings UI). */
    private val _seededFlow = MutableStateFlow(false)
    val seededFlow = _seededFlow.asStateFlow()

    /** Whether this APK ships the ASR models in assets/ (cached after first check). */
    fun assetsBundled(): Boolean {
        if (!assetsChecked) synchronized(seedLock) {
            if (!assetsChecked) {
                assetsAvailable = BUNDLED_ASSETS.all { rel ->
                    runCatching { context.assets.open(rel).use { } }.isSuccess
                }
                assetsChecked = true
            }
        }
        return assetsAvailable
    }

    /**
     * Blocking: unpack the bundled models into filesDir (idempotent, thread-safe).
     * Must be called on an IO thread. Returns true if the models are usable.
     * Falls back to false when the APK has no bundled models (debug builds) so
     * the caller can fall back to the download path.
     */
    fun seedFromAssetsIfNeeded(): Boolean {
        if (seeded && paths.isReady()) return true
        synchronized(seedLock) {
            if (paths.isReady()) {
                seeded = true
                _seededFlow.value = true
                return true
            }
            if (!assetsBundled()) return false // debug build / old APK → download path
            return try {
                copyAsset("models/sensevoice/model.int8.onnx", paths.senseVoiceModel, SENSE_MODEL_BYTES)
                copyAsset("models/sensevoice/tokens.txt", paths.tokens, SENSE_TOKENS_BYTES)
                copyAsset("models/silero_vad.onnx", paths.sileroVad, VAD_BYTES)
                seeded = true
                _seededFlow.value = true
                true
            } catch (t: Throwable) {
                Log.e(TAG, "seedFromAssets failed", t)
                false
            }
        }
    }

    /** Copies one bundled asset to [dest]; a temp ".seedpart" file is renamed over it. */
    private fun copyAsset(assetPath: String, dest: File, expectedBytes: Long) {
        if (dest.isFile && dest.length() == expectedBytes) return // idempotent / auto-repair
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".seedpart")
        tmp.delete() // clear a leftover from a previous interrupted run
        context.assets.open(assetPath).use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                if (total != expectedBytes) {
                    throw IOException("内置模型 $assetPath 尺寸不符: $total != $expectedBytes")
                }
            }
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        Log.i(TAG, "Seeded $assetPath -> ${dest.absolutePath}")
    }

    fun isReady(): Boolean = paths.isReady()

    /**
     * @param mirrorBaseUrl  e.g. https://hf-mirror.com (default) or https://huggingface.co
     * @param huggingfaceToken optional read token (helps when HF rejects anonymous
     *                         downloads, e.g. some ISPs / VPN exits).
     */
    suspend fun downloadAll(
        mirrorBaseUrl: String,
        huggingfaceToken: String? = null,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = mirrorBaseUrl.trim().trimEnd('/').ifBlank { DEFAULT_MIRROR }
            val token = huggingfaceToken?.trim()?.takeIf { it.isNotEmpty() }

            paths.senseVoiceModel.parentFile?.mkdirs()
            paths.sileroVad.parentFile?.mkdirs()

            // Sequence order = overall progress weighting by expected bytes.
            val files = listOf(
                FileSpec(
                    url = "$base/$SENSE_REPO/resolve/main/model.int8.onnx",
                    dest = paths.senseVoiceModel,
                    label = "SenseVoice 语音模型",
                    expectedBytes = SENSE_MODEL_BYTES,
                ),
                FileSpec(
                    url = "$base/$SENSE_REPO/resolve/main/tokens.txt",
                    dest = paths.tokens,
                    label = "词汇表 tokens",
                    expectedBytes = SENSE_TOKENS_BYTES,
                ),
                FileSpec(
                    url = "$VAD_URL",
                    dest = paths.sileroVad,
                    label = "静音检测 silero-vad",
                    expectedBytes = VAD_BYTES,
                ),
            )
            val totalExpected = files.sumOf { it.expectedBytes }
            var done = 0L

            files.forEach { spec ->
                if (isUsable(spec.dest)) {
                    done += spec.expectedBytes
                    report(done, totalExpected, onProgress, "${spec.label} 已存在")
                    return@forEach
                }
                var fileDone = 0L
                val fileTotal = downloadFile(spec, token) { n ->
                    fileDone += n
                    report(done + fileDone, totalExpected, onProgress, spec.label)
                }
                done += fileTotal
            }
            onProgress(DownloadProgress(1f, "✅ 模型下载完成"))
        }
    }

    private fun report(
        done: Long,
        total: Long,
        onProgress: (DownloadProgress) -> Unit,
        label: String,
    ) {
        if (total <= 0) return
        onProgress(DownloadProgress((done.toFloat() / total).coerceIn(0f, 1f), label))
    }

    private fun isUsable(f: File): Boolean = f.isFile && f.length() > 0

    /** Streams [spec] to disk (via a .part temp file). Returns bytes written. */
    private fun downloadFile(
        spec: FileSpec,
        authToken: String?,
        onChunk: (Long) -> Unit,
    ): Long {
        val dest = spec.dest
        val tmp = File(dest.parentFile, dest.name + ".part")
        val reqBuilder = Request.Builder().url(spec.url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) LiveTranslate/0.1")
        if (authToken != null) {
            reqBuilder.header("Authorization", "Bearer $authToken")
        }
        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("下载 ${spec.label} 失败 HTTP ${resp.code}（可检查镜像地址/网络/加速器）")
            }
            val body = resp.body ?: throw IOException("下载 ${spec.label} 响应为空")
            val streamedTotal = body.contentLength()
            if (streamedTotal > 0 && dest.isFile && dest.length() == streamedTotal) {
                return streamedTotal // already fully downloaded (rare race)
            }
            dest.parentFile?.mkdirs()
            var total = 0L
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        onChunk(n.toLong())
                    }
                }
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            Log.i(TAG, "Downloaded ${spec.label} -> ${dest.absolutePath}")
            return total
        }
    }

    companion object {
        private const val TAG = "ModelDownloader"
        const val DEFAULT_MIRROR = "https://hf-mirror.com"

        // Asset paths shipped inside the release APK (mirror of [paths]).
        private val BUNDLED_ASSETS = listOf(
            "models/sensevoice/model.int8.onnx",
            "models/sensevoice/tokens.txt",
            "models/silero_vad.onnx",
        )
        // NOTE: the repo name has NO "int8" segment — that variant 404s.
        private const val SENSE_REPO =
            "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"
        private const val VAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

        // Expected sizes (bytes) — weight overall progress AND are the seed
        // idempotency check (must match the actual file bytes exactly).
        const val SENSE_MODEL_BYTES = 239_233_841L
        const val SENSE_TOKENS_BYTES = 315_894L
        const val VAD_BYTES = 643_854L // actual size of the asr-models release asset
    }

    private data class FileSpec(
        val url: String,
        val dest: File,
        val label: String,
        val expectedBytes: Long,
    )
}

/** Absolute paths of every model file the ASR pipeline needs. */
data class ModelPaths(
    val senseVoiceModel: File,
    val tokens: File,
    val sileroVad: File,
) {
    fun isReady(): Boolean =
        senseVoiceModel.isFile && senseVoiceModel.length() > 0 &&
            tokens.isFile && tokens.length() > 0 &&
            sileroVad.isFile && sileroVad.length() > 0

    /** Total size on disk once downloaded (MB). */
    val totalMb: Int
        get() = (listOf(senseVoiceModel, tokens, sileroVad).sumOf { it.length() } / (1024 * 1024)).toInt()
}
