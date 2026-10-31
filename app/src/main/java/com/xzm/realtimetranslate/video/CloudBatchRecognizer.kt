package com.xzm.realtimetranslate.video

import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.util.WavEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 云端整段识别：把 VAD 切好的每段音频编码成 WAV，POST 到 OpenAI 兼容的
 * `/audio/transcriptions`。Whisper large-v3 这类模型支持 99 种语言，
 * 补上本地 SenseVoice 只有五种语言的短板。
 *
 * 视频是离线文件，没有实时延迟压力，所以这里不需要流式协议。
 */
class CloudBatchRecognizer(
    baseUrl: String,
    apiKey: String,
    model: String,
    /** 源语言代码，如 "zh-Hans" / "en"；"auto" 表示交给模型自己判断。 */
    language: String,
    private val client: OkHttpClient = defaultClient(),
) : BatchSpeechRecognizer {

    private val endpoint = baseUrl.trim().trimEnd('/')
        .removeSuffix("/chat/completions")
        .removeSuffix("/audio/transcriptions") + "/audio/transcriptions"

    private val apiKey = apiKey.trim()
    private val model = model.ifBlank { "whisper-1" }
    private val languageHint = isoLanguageOf(language)

    override suspend fun recognize(samples: FloatArray): String? {
        if (samples.isEmpty()) return null
        return withContext(Dispatchers.IO) { transcribe(samples) }
    }

    private fun transcribe(samples: FloatArray): String? {
        val wav = WavEncoder.encodePcm16leToWav(toPcm16le(samples), VadTimeline.SAMPLE_RATE)

        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "segment.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
        languageHint?.let { form.addFormDataPart("language", it) }

        val request = Request.Builder()
            .url(endpoint)
            .post(form.build())
            .apply { if (apiKey.isNotEmpty()) header("Authorization", "Bearer $apiKey") }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "transcription HTTP ${response.code}: ${body.take(200)}")
                    return null
                }
                JSONObject(body).optString("text", "").trim().takeIf { it.isNotBlank() }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "transcription failed: ${t.message}")
            null
        }
    }

    override fun close() = Unit

    companion object {
        private const val TAG = "CloudBatchRecognizer"

        /** Whisper 认 ISO-639-1，本项目内部是 "zh-Hans" / "pt-BR" 这类带地区的代码。 */
        fun isoLanguageOf(code: String): String? {
            val raw = code.trim()
            if (raw.isEmpty() || raw == "auto") return null
            return raw.substringBefore('-').lowercase()
        }

        fun toPcm16le(samples: FloatArray): ByteArray {
            val out = ByteArrayOutputStream(samples.size * 2)
            for (sample in samples) {
                val clamped = sample.coerceIn(-1f, 1f)
                // 与 AudioPcmExtractor 的 PCM 解释保持一致：满量程 = Short.MAX_VALUE
                val value = (clamped * Short.MAX_VALUE).toInt().toShort()
                out.write(value.toInt() and 0xFF)
                out.write((value.toInt() shr 8) and 0xFF)
            }
            return out.toByteArray()
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
